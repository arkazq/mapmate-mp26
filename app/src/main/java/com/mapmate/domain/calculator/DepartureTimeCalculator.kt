package com.mapmate.domain.calculator

import java.time.LocalTime

class DepartureTimeCalculator {
    fun calculate(
        targetArrivalTime: LocalTime,
        routeDurationMinutes: Int,
        personalBufferMinutes: Int,
        safetyMarginMinutes: Int,
    ): LocalTime {
        return targetArrivalTime
            .minusMinutes(routeDurationMinutes.toLong())
            .minusMinutes(personalBufferMinutes.toLong())
            .minusMinutes(safetyMarginMinutes.toLong())
    }
}
