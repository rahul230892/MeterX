package com.meterx.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class UsageStatusTest {
    @Test
    fun electricityUsageIsMeasuredFromCycleBaseline() {
        val item = electricityMeter(baseline = 100.0, freeUnits = 200.0, latest = 250.0)

        val status = item.usageStatus()!!

        assertEquals(150.0, status.used, 0.0)
        assertEquals(UsageLevel.NORMAL, status.level)
    }

    @Test
    fun usageTurnsRedAtFreeUnitLimit() {
        val item = electricityMeter(baseline = 100.0, freeUnits = 200.0, latest = 300.0)

        assertEquals(UsageLevel.OVER_LIMIT, item.usageStatus()!!.level)
    }

    @Test
    fun nonElectricMetersDoNotHaveFreeUnitStatus() {
        val item = MeterWithReadings(
            meter = MeterEntity(
                id = 1,
                nickname = "Water",
                type = MeterType.WATER,
                meterNumber = "W1",
                consumerNumber = null,
                freeUnits = null,
                cycleBaseline = null,
            ),
            readings = emptyList(),
        )

        assertNull(item.usageStatus())
    }

    @Test
    fun dailyConsumptionUsesPreviousChronologicalReading() {
        val may1 = LocalDate.of(2026, 5, 1).toEpochDay()
        val may7 = LocalDate.of(2026, 5, 7).toEpochDay()
        val may19 = LocalDate.of(2026, 5, 19).toEpochDay()
        val item = electricityMeter(
            baseline = 100.0,
            freeUnits = 200.0,
            readings = listOf(
                reading(id = 1, value = 100.0, date = may1),
                reading(id = 2, value = 130.0, date = may7),
                reading(id = 3, value = 190.0, date = may19),
            ),
        )

        val may7Average = item.dailyConsumptionFor(item.readings[1])!!
        val may19Average = item.dailyConsumptionFor(item.readings[2])!!

        assertEquals(30.0, may7Average.unitsUsed, 0.0)
        assertEquals(6, may7Average.elapsedDays)
        assertEquals(5.0, may7Average.averagePerDay, 0.0)
        assertEquals(5.0, may19Average.averagePerDay, 0.0)
    }

    @Test
    fun firstOrDecreasingReadingHasNoDailyAverage() {
        val item = electricityMeter(
            baseline = 100.0,
            freeUnits = 200.0,
            readings = listOf(
                reading(id = 1, value = 100.0, date = 1),
                reading(id = 2, value = 90.0, date = 2),
            ),
        )

        assertNull(item.dailyConsumptionFor(item.readings[0]))
        assertNull(item.dailyConsumptionFor(item.readings[1]))
    }

    private fun electricityMeter(
        baseline: Double,
        freeUnits: Double,
        latest: Double,
    ) = electricityMeter(
        baseline = baseline,
        freeUnits = freeUnits,
        readings = listOf(reading(id = 1, value = latest, date = 1)),
    )

    private fun electricityMeter(
        baseline: Double,
        freeUnits: Double,
        readings: List<ReadingEntity>,
    ) = MeterWithReadings(
        meter = MeterEntity(
            id = 1,
            nickname = "Home",
            type = MeterType.ELECTRICITY,
            meterNumber = "E1",
            consumerNumber = null,
            freeUnits = freeUnits,
            cycleBaseline = baseline,
        ),
        readings = readings,
    )

    private fun reading(id: Long, value: Double, date: Long) = ReadingEntity(
        id = id,
        meterId = 1,
        value = value,
        readingDate = date,
    )
}
