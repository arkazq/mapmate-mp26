package com.mapmate.domain.calculator

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureTimeCalculatorTest {
    @Test
    fun calculate_returnsRecommendedDepartureTime() {
        val calculator = DepartureTimeCalculator()

        val result = calculator.calculate(
            targetArrivalTime = LocalTime.of(9, 0),
            routeDurationMinutes = 42,
            personalBufferMinutes = 6,
            safetyMarginMinutes = 5,
        )

        assertEquals(LocalTime.of(8, 7), result)
    }

    @Test
    fun calculateWithNowClamp_returnsNowWhenDepartureTimeAlreadyPassedAndArrivalIsAhead() {
        val calculator = DepartureTimeCalculator()

        val result = calculator.calculateWithNowClamp(
            targetArrivalTime = LocalTime.of(9, 0),
            routeDurationMinutes = 42,
            personalBufferMinutes = 6,
            safetyMarginMinutes = 5,
            now = LocalTime.of(8, 8, 30),
        )

        assertEquals(LocalTime.of(8, 7), result.calculatedDepartureTime)
        assertEquals(LocalTime.of(8, 8), result.recommendedDepartureTime)
        assertEquals(true, result.isImmediateDepartureRecommended)
    }

    @Test
    fun calculateWithNowClamp_keepsCalculatedTimeWhenArrivalAlreadyPassed() {
        val calculator = DepartureTimeCalculator()

        val result = calculator.calculateWithNowClamp(
            targetArrivalTime = LocalTime.of(9, 0),
            routeDurationMinutes = 42,
            personalBufferMinutes = 6,
            safetyMarginMinutes = 5,
            now = LocalTime.of(9, 1),
        )

        assertEquals(LocalTime.of(8, 7), result.calculatedDepartureTime)
        assertEquals(LocalTime.of(8, 7), result.recommendedDepartureTime)
        assertEquals(false, result.isImmediateDepartureRecommended)
    }
}
