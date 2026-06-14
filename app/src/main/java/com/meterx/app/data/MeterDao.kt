package com.meterx.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeterDao {
    @Transaction
    @Query("SELECT * FROM meters ORDER BY created_at DESC")
    fun observeMeters(): Flow<List<MeterWithReadings>>

    @Transaction
    @Query("SELECT * FROM meters ORDER BY created_at DESC")
    suspend fun getMetersSnapshot(): List<MeterWithReadings>

    @Insert
    suspend fun insertMeter(meter: MeterEntity): Long

    @Update
    suspend fun updateMeter(meter: MeterEntity)

    @Delete
    suspend fun deleteMeter(meter: MeterEntity)

    @Insert
    suspend fun insertReading(reading: ReadingEntity): Long

    @Update
    suspend fun updateReading(reading: ReadingEntity)

    @Delete
    suspend fun deleteReading(reading: ReadingEntity)
}
