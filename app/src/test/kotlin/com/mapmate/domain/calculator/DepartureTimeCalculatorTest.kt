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
}
