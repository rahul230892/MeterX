package com.meterx.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class MeterType {
    ELECTRICITY,
    WATER,
    GAS,
}

@Entity(tableName = "meters")
data class MeterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val type: MeterType,
    @ColumnInfo(name = "meter_number") val meterNumber: String,
    @ColumnInfo(name = "consumer_number") val consumerNumber: String?,
    @ColumnInfo(name = "free_units") val freeUnits: Double?,
    @ColumnInfo(name = "cycle_baseline") val cycleBaseline: Double?,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(
            entity = MeterEntity::class,
            parentColumns = ["id"],
            childColumns = ["meter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("meter_id")],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "meter_id") val meterId: Long,
    val value: Double,
    @ColumnInfo(name = "reading_date") val readingDate: Long,
    @ColumnInfo(name = "is_billed") val isBilled: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

data class MeterWithReadings(
    @Embedded val meter: MeterEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "meter_id",
    )
    val readings: List<ReadingEntity>,
) {
    val sortedReadings: List<ReadingEntity>
        get() = readings.sortedWith(
            compareByDescending<ReadingEntity> { it.readingDate }
                .thenByDescending { it.createdAt },
        )

    val latestReading: ReadingEntity?
        get() = sortedReadings.firstOrNull()
}

data class UsageStatus(
    val used: Double,
    val allowance: Double,
    val fraction: Float,
    val level: UsageLevel,
)

enum class UsageLevel {
    NORMAL,
    NEAR_LIMIT,
    OVER_LIMIT,
}

fun MeterWithReadings.usageStatus(): UsageStatus? {
    if (meter.type != MeterType.ELECTRICITY) return null
    val allowance = meter.freeUnits ?: return null
    val latest = latestReading?.value ?: return null
    val baseline = meter.cycleBaseline ?: latest
    val used = (latest - baseline).coerceAtLeast(0.0)
    val fraction = if (allowance > 0) (used / allowance).toFloat() else 1f
    val level = when {
        fraction >= 1f -> UsageLevel.OVER_LIMIT
        fraction >= 0.8f -> UsageLevel.NEAR_LIMIT
        else -> UsageLevel.NORMAL
    }
    return UsageStatus(used, allowance, fraction, level)
}
