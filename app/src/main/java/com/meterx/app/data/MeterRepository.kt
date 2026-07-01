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

    suspend fun addReading(
        meter: MeterEntity,
        value: Double,
        date: Long,
        isBilled: Boolean,
    ) {
        database.withTransaction {
            dao.insertReading(
                ReadingEntity(
                    meterId = meter.id,
                    value = value,
                    readingDate = date,
                    isBilled = isBilled,
                ),
            )
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
    ) {
        database.withTransaction {
            dao.updateReading(
                reading.copy(
                    value = value,
                    readingDate = date,
                    isBilled = isBilled,
                ),
            )
            if (
                meter.type == MeterType.ELECTRICITY &&
                (isBilled || meter.cycleBaseline == reading.value)
            ) {
                dao.updateMeter(meter.copy(cycleBaseline = value))
            }
        }
        syncBackup()
    }

    suspend fun resetFreeUnits(meter: MeterEntity, billedReading: ReadingEntity) {
        require(meter.type == MeterType.ELECTRICITY)
        require(billedReading.isBilled)
        dao.updateMeter(meter.copy(cycleBaseline = billedReading.value))
        syncBackup()
    }

    suspend fun exportData(uri: Uri) {
        backup.export(uri, dao.getMetersSnapshot())
    }

    suspend fun previewImport(uri: Uri): ImportPreview = backup.previewImport(uri)

    suspend fun importData(preview: ImportPreview) {
        val imported = backup.readImport(preview.uri)
        database.withTransaction {
            dao.deleteAllReadings()
            dao.deleteAllMeters()
            dao.insertMeters(imported.meters)
            dao.insertReadings(imported.readings)
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
        if (session.token != null) replaceFromCloud()
    }

    suspend fun syncNow() {
        uploadToCloud()
    }

    suspend fun logout() {
        session.clear()
        database.withTransaction {
            dao.deleteAllReadings()
            dao.deleteAllMeters()
        }
        backup.write(emptyList())
    }

    private suspend fun syncBackup() {
        backup.write(dao.getMetersSnapshot())
        if (session.token != null) uploadToCloud()
    }

    private suspend fun uploadToCloud() {
        val token = session.token ?: return
        api.uploadSnapshot(token, dao.getMetersSnapshot())
    }

    private suspend fun replaceFromCloud() {
        val token = session.token ?: return
        val snapshot = api.downloadSnapshot(token)
        database.withTransaction {
            dao.deleteAllReadings()
            dao.deleteAllMeters()
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
            dao.insertReadings(
                snapshot.readings.mapNotNull { reading ->
                    localIdsByClientId[reading.meterClientId]?.let { localMeterId ->
                        ReadingEntity(
                            meterId = localMeterId,
                            value = reading.value,
                            readingDate = reading.readingDate,
                            isBilled = reading.isBilled,
                            createdAt = reading.createdAt,
                        )
                    }
                },
            )
        }
        backup.write(dao.getMetersSnapshot())
    }
}
