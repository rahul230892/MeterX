package com.meterx.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM payment_methods ORDER BY name COLLATE NOCASE")
    fun observePaymentMethods(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods ORDER BY name COLLATE NOCASE")
    suspend fun getPaymentMethodsSnapshot(): List<PaymentMethodEntity>

    @Insert
    suspend fun insertMeter(meter: MeterEntity): Long

    @Insert
    suspend fun insertMeters(meters: List<MeterEntity>): List<Long>

    @Update
    suspend fun updateMeter(meter: MeterEntity)

    @Delete
    suspend fun deleteMeter(meter: MeterEntity)

    @Insert
    suspend fun insertReading(reading: ReadingEntity): Long

    @Insert
    suspend fun insertReadings(readings: List<ReadingEntity>)

    @Update
    suspend fun updateReading(reading: ReadingEntity)

    @Delete
    suspend fun deleteReading(reading: ReadingEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPaymentMethod(method: PaymentMethodEntity): Long

    @Insert
    suspend fun insertPaymentMethods(methods: List<PaymentMethodEntity>): List<Long>

    @Delete
    suspend fun deletePaymentMethod(method: PaymentMethodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentRecord(payment: PaymentRecordEntity): Long

    @Insert
    suspend fun insertPaymentRecords(payments: List<PaymentRecordEntity>)

    @Query("SELECT * FROM payment_records WHERE reading_id = :readingId LIMIT 1")
    suspend fun getPaymentForReading(readingId: Long): PaymentRecordEntity?

    @Query("DELETE FROM payment_records WHERE reading_id = :readingId")
    suspend fun deletePaymentForReading(readingId: Long)

    @Query("DELETE FROM payment_records")
    suspend fun deleteAllPaymentRecords()

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAllPaymentMethods()

    @Query("DELETE FROM readings")
    suspend fun deleteAllReadings()

    @Query("DELETE FROM meters")
    suspend fun deleteAllMeters()
}
