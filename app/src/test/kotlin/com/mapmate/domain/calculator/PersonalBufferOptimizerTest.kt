package com.mapmate.domain.calculator

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalBufferOptimizerTest {
    private val optimizer = PersonalBufferOptimizer()

    @Test
    fun optimize_usesRecentAverageWithSmoothing() {
        val result = optimizer.optimize(
            currentPersonalBufferMinutes = 6,
            recentArrivalDeltaMinutes = listOf(4, 5, 3),
        )

        assertEquals(7, result)
    }

    @Test
    fun optimize_limitsEachAdjustmentStep() {
        val lateResult = optimizer.optimize(
            currentPersonalBufferMinutes = 6,
            recentArrivalDeltaMinutes = listOf(30, 30, 30),
        )
        val earlyResult = optimizer.optimize(
            currentPersonalBufferMinutes = 6,
            recentArrivalDeltaMinutes = listOf(-30, -30, -30),
        )

        assertEquals(11, lateResult)
        assertEquals(1, earlyResult)
    }

    @Test
    fun optimize_usesOnlyMostRecentThreeRecords() {
        val result = optimizer.optimize(
            currentPersonalBufferMinutes = 6,
            recentArrivalDeltaMinutes = listOf(9, 9, 9, -30, -30),
        )

        assertEquals(9, result)
    }

    @Test
    fun optimize_keepsValueWithinValidRange() {
        assertEquals(
            60,
            optimizer.optimize(
                currentPersonalBufferMinutes = 58,
                recentArrivalDeltaMinutes = listOf(30, 30, 30),
            ),
        )
        assertEquals(
            0,
            optimizer.optimize(
                currentPersonalBufferMinutes = 2,
                recentArrivalDeltaMinutes = listOf(-30, -30, -30),
            ),
        )
    }

    @Test
    fun optimize_keepsCurrentValueWhenNoRecordsExist() {
        assertEquals(
            6,
            optimizer.optimize(
                currentPersonalBufferMinutes = 6,
                recentArrivalDeltaMinutes = emptyList(),
            ),
        )
    }
}
