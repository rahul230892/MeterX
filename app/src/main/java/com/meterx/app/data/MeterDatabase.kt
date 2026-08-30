package com.meterx.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

class MeterTypeConverter {
    @TypeConverter
    fun fromMeterType(value: MeterType): String = value.name

    @TypeConverter
    fun toMeterType(value: String): MeterType = MeterType.valueOf(value)
}

class CustomFieldConverters {
    @TypeConverter
    fun fromCustomFieldDefinitions(value: List<CustomFieldDefinition>): String {
        val array = JSONArray()
        value.forEach { field ->
            array.put(
                JSONObject()
                    .put("id", field.id)
                    .put("name", field.name),
            )
        }
        return array.toString()
    }

    @TypeConverter
    fun toCustomFieldDefinitions(value: String): List<CustomFieldDefinition> = runCatching {
        val array = JSONArray(value)
        buildList {
            repeat(array.length()) { index ->
                val field = array.getJSONObject(index)
                add(
                    CustomFieldDefinition(
                        id = field.getString("id"),
                        name = field.getString("name"),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())

    @TypeConverter
    fun fromCustomFieldValues(value: Map<String, String>): String {
        val json = JSONObject()
        value.toSortedMap().forEach { (fieldId, fieldValue) ->
            json.put(fieldId, fieldValue)
        }
        return json.toString()
    }

    @TypeConverter
    fun toCustomFieldValues(value: String): Map<String, String> = runCatching {
        val json = JSONObject(value)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val fieldId = keys.next()
                put(fieldId, json.getString(fieldId))
            }
        }
    }.getOrDefault(emptyMap())
}

@Database(
    entities = [
        MeterEntity::class,
        ReadingEntity::class,
        PaymentMethodEntity::class,
        PaymentRecordEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(MeterTypeConverter::class, CustomFieldConverters::class)
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
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_methods (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_payment_methods_name
                    ON payment_methods(name)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS payment_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        meter_id INTEGER NOT NULL,
                        reading_id INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        payment_date INTEGER NOT NULL,
                        method_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY(meter_id) REFERENCES meters(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(reading_id) REFERENCES readings(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_payment_records_meter_id ON payment_records(meter_id)",
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_payment_records_reading_id
                    ON payment_records(reading_id)
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE meters ADD COLUMN custom_fields_json TEXT NOT NULL DEFAULT '[]'",
                )
                db.execSQL(
                    "ALTER TABLE readings ADD COLUMN custom_values_json TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }
    }
}
