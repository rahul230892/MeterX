package com.meterx.app.data

import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class MeterRepository(
    private val database: MeterDatabase,
    private val backup: LocalBackup,
    private val api: MeterApi,
    private val session: AuthSession,
) {
    private val dao = database.meterDao()

    val meters: Flow<List<MeterWithReadings>> = dao.observeMeters()
    val paymentMethods: Flow<List<PaymentMethodEntity>> = dao.observePaymentMethods()

    suspend fun addMeter(
        nickname: String,
        type: MeterType,
        meterNumber: String,
        consumerNumber: String?,
        freeUnits: Double?,
    ) {
        dao.insertMeter(
            MeterEntity(
                nickname = nickname.trim(),
                type = type,
                meterNumber = meterNumber.trim(),
                consumerNumber = consumerNumber?.trim()?.takeIf(String::isNotEmpty),
                freeUnits = freeUnits.takeIf { type == MeterType.ELECTRICITY },
                cycleBaseline = null,
            ),
        )
        syncBackup()
    }

    suspend fun deleteMeter(meter: MeterEntity) {
        dao.deleteMeter(meter)
        syncBackup()
    }

    suspend fun updateMeter(
        meter: MeterEntity,
        nickname: String,
        meterNumber: String,
        consumerNumber: String?,
        freeUnits: Double?,
    ) {
        dao.updateMeter(
            meter.copy(
                nickname = nickname.trim(),
                meterNumber = meterNumber.trim(),
                consumerNumber = consumerNumber?.trim()?.takeIf(String::isNotEmpty),
                freeUnits = freeUnits.takeIf { meter.type == MeterType.ELECTRICITY },
            ),
        )
        syncBackup()
    }

    suspend fun addReading(
        meter: MeterEntity,
        value: Double,
        date: Long,
        isBilled: Boolean,
        payment: PaymentInput?,
    ) {
        database.withTransaction {
            val readingId = dao.insertReading(
                ReadingEntity(
                    meterId = meter.id,
                    value = value,
                    readingDate = date,
                    isBilled = isBilled,
                ),
            )
            if (isBilled && payment != null) {
                dao.insertPaymentRecord(payment.toRecord(meter.id, readingId))
            }
            if (
                meter.type == MeterType.ELECTRICITY &&
                (meter.cycleBaseline == null || isBilled)
            ) {
                dao.updateMeter(meter.copy(cycleBaseline = value))
            }
        }
        syncBackup()
    }

    suspend fun deleteReading(reading: ReadingEntity) {
        dao.deleteReading(reading)
        syncBackup()
    }

    suspend fun updateReading(
        meter: MeterEntity,
        reading: ReadingEntity,
        value: Double,
        date: Long,
        isBilled: Boolean,
        payment: PaymentInput?,
    ) {
        database.withTransaction {
            dao.updateReading(
                reading.copy(
                    value = value,
                    readingDate = date,
                    isBilled = isBilled,
                ),
            )
            if (isBilled && payment != null) {
                val existingPayment = dao.getPaymentForReading(reading.id)
                dao.insertPaymentRecord(
                    payment.toRecord(
                        meterId = meter.id,
                        readingId = reading.id,
                        existing = existingPayment,
                    ),
                )
            } else {
                dao.deletePaymentForReading(reading.id)
            }
            if (
                meter.type == MeterType.ELECTRICITY &&
                (isBilled || meter.cycleBaseline == reading.value)
            ) {
                dao.updateMeter(meter.copy(cycleBaseline = value))
            }
        }
        syncBackup()
    }

    suspend fun addPaymentMethod(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Payment option name is required." }
        dao.insertPaymentMethod(PaymentMethodEntity(name = trimmed))
        syncBackup()
    }

    suspend fun deletePaymentMethod(method: PaymentMethodEntity) {
        dao.deletePaymentMethod(method)
        syncBackup()
    }

    suspend fun resetFreeUnits(meter: MeterEntity, billedReading: ReadingEntity) {
        require(meter.type == MeterType.ELECTRICITY)
        require(billedReading.isBilled)
        dao.updateMeter(meter.copy(cycleBaseline = billedReading.value))
        syncBackup()
    }

    suspend fun exportData(uri: Uri) {
        backup.export(uri, dao.getMetersSnapshot(), dao.getPaymentMethodsSnapshot())
    }

    suspend fun previewImport(uri: Uri): ImportPreview = backup.previewImport(uri)

    suspend fun importData(preview: ImportPreview) {
        val imported = backup.readImport(preview.uri)
        database.withTransaction {
            dao.deleteAllPaymentRecords()
            dao.deleteAllPaymentMethods()
            dao.deleteAllReadings()
            dao.deleteAllMeters()
            dao.insertMeters(imported.meters)
            dao.insertReadings(imported.readings)
            dao.insertPaymentMethods(imported.paymentMethods)
            dao.insertPaymentRecords(imported.payments)
        }
        syncBackup()
    }

    suspend fun register(username: String, password: String): AuthUser {
        val result = api.register(username, password)
        session.save(result.token, result.user)
        uploadToCloud()
        return result.user
    }

    suspend fun login(username: String, password: String): AuthUser {
        val result = api.login(username, password)
        session.save(result.token, result.user)
        replaceFromCloud()
        return result.user
    }

    suspend fun restoreSession() {
        val token = session.token ?: return
        try {
            val refreshed = api.refresh(token)
            session.save(refreshed.token, refreshed.user)
            replaceFromCloud()
        } catch (error: AuthExpiredException) {
            session.clear()
            throw error
        }
    }

    suspend fun syncNow() {
        uploadToCloud()
    }

    suspend fun checkForAppUpdate(currentVersionCode: Int): AppUpdateInfo? {
        val update = api.latestAppVersion()
        return update.takeIf {
            it.latestVersionCode > currentVersionCode ||
                it.minSupportedVersionCode > currentVersionCode
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String): AuthUser {
        val token = session.token ?: throw AuthExpiredException("Session expired.")
        try {
            val result = api.changePassword(token, currentPassword, newPassword)
            session.save(result.token, result.user)
            return result.user
        } catch (error: AuthExpiredException) {
            session.clear()
            throw error
        }
    }

    suspend fun logout() {
        session.clear()
        database.withTransaction {
            dao.deleteAllPaymentRecords()
            dao.deleteAllPaymentMethods()
            dao.deleteAllReadings()
            dao.deleteAllMeters()
        }
        backup.write(emptyList(), emptyList())
    }

    private suspend fun syncBackup() {
        backup.write(dao.getMetersSnapshot(), dao.getPaymentMethodsSnapshot())
        if (session.token != null) uploadToCloud()
    }

    private suspend fun uploadToCloud() {
        val token = session.token ?: return
        try {
            api.uploadSnapshot(
                token = token,
                snapshot = dao.getMetersSnapshot(),
                paymentMethods = dao.getPaymentMethodsSnapshot(),
            )
        } catch (error: AuthExpiredException) {
            session.clear()
            throw error
        }
    }

    private suspend fun replaceFromCloud() {
        val token = session.token ?: return
        val snapshot = try {
            api.downloadSnapshot(token)
        } catch (error: AuthExpiredException) {
            session.clear()
            throw error
        }
        database.withTransaction {
            dao.deleteAllPaymentRecords()
            dao.deleteAllPaymentMethods()
            dao.deleteAllReadings()
            dao.deleteAllMeters()
            dao.insertPaymentMethods(
                snapshot.paymentMethods.map { method ->
                    PaymentMethodEntity(
                        name = method.name,
                        createdAt = method.createdAt,
                    )
                },
            )
            val localIdsByClientId = snapshot.meters.map { meter ->
                val localId = dao.insertMeter(
                    MeterEntity(
                        nickname = meter.nickname,
                        type = meter.type,
                        meterNumber = meter.meterNumber,
                        consumerNumber = meter.consumerNumber,
                        freeUnits = meter.freeUnits,
                        cycleBaseline = meter.cycleBaseline,
                        createdAt = meter.createdAt,
                    ),
                )
                meter.clientId to localId
            }.toMap()
            val localReadingIdsByClientId = snapshot.readings.mapNotNull { reading ->
                val localMeterId = localIdsByClientId[reading.meterClientId] ?: return@mapNotNull null
                val localReadingId = dao.insertReading(
                    ReadingEntity(
                        meterId = localMeterId,
                        value = reading.value,
                        readingDate = reading.readingDate,
                        isBilled = reading.isBilled,
                        createdAt = reading.createdAt,
                    ),
                )
                reading.clientId to localReadingId
            }.toMap()
            dao.insertPaymentRecords(
                snapshot.payments.mapNotNull { payment ->
                    val localMeterId = localIdsByClientId[payment.meterClientId]
                        ?: return@mapNotNull null
                    val localReadingId = localReadingIdsByClientId[payment.readingClientId]
                        ?: return@mapNotNull null
                    PaymentRecordEntity(
                        meterId = localMeterId,
                        readingId = localReadingId,
                        amount = payment.amount,
                        paymentDate = payment.paymentDate,
                        methodName = payment.methodName,
                        createdAt = payment.createdAt,
                    )
                },
            )
        }
        backup.write(dao.getMetersSnapshot(), dao.getPaymentMethodsSnapshot())
    }

    private fun PaymentInput.toRecord(
        meterId: Long,
        readingId: Long,
        existing: PaymentRecordEntity? = null,
    ): PaymentRecordEntity =
        PaymentRecordEntity(
            id = existing?.id ?: 0,
            meterId = meterId,
            readingId = readingId,
            amount = amount,
            paymentDate = paymentDate,
            methodName = methodName.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
}
