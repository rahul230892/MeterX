package com.meterx.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class MeterTypeConverter {
    @TypeConverter
    fun fromMeterType(value: MeterType): String = value.name

    @TypeConverter
    fun toMeterType(value: String): MeterType = MeterType.valueOf(value)
}

@Database(
    entities = [MeterEntity::class, ReadingEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(MeterTypeConverter::class)
abstract class MeterDatabase : RoomDatabase() {
    abstract fun meterDao(): MeterDao

    companion object {
        @Volatile
        private var instance: MeterDatabase? = null

        fun getInstance(context: Context): MeterDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeterDatabase::class.java,
                    "meterx.db",
                ).build().also { instance = it }
            }
    }
}
