package com.meterx.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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

    private fun electricityMeter(
        baseline: Double,
        freeUnits: Double,
        latest: Double,
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
        readings = listOf(
            ReadingEntity(
                id = 1,
                meterId = 1,
                value = latest,
                readingDate = 1,
            ),
        ),
    )
}
