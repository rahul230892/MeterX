package com.meterx.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class MeterRepository(
    private val database: MeterDatabase,
    private val backup: LocalBackup,
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
            if (meter.type == MeterType.ELECTRICITY && meter.cycleBaseline == null) {
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
                meter.cycleBaseline == reading.value
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

    private suspend fun syncBackup() {
        backup.write(dao.getMetersSnapshot())
    }
}
