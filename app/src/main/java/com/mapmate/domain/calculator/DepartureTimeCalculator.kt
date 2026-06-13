package com.mapmate.domain.calculator

import java.time.LocalTime
import java.time.temporal.ChronoUnit

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

    fun calculateWithNowClamp(
        targetArrivalTime: LocalTime,
        routeDurationMinutes: Int,
        personalBufferMinutes: Int,
        safetyMarginMinutes: Int,
        now: LocalTime = LocalTime.now(),
    ): DepartureTimeRecommendation {
        val calculatedDepartureTime = calculate(
            targetArrivalTime = targetArrivalTime,
            routeDurationMinutes = routeDurationMinutes,
            personalBufferMinutes = personalBufferMinutes,
            safetyMarginMinutes = safetyMarginMinutes,
        )
        val isImmediateDepartureRecommended =
            calculatedDepartureTime.isBefore(now) && !targetArrivalTime.isBefore(now)

        return DepartureTimeRecommendation(
            calculatedDepartureTime = calculatedDepartureTime,
            recommendedDepartureTime = if (isImmediateDepartureRecommended) {
                now.truncatedTo(ChronoUnit.MINUTES)
            } else {
                calculatedDepartureTime
            },
            isImmediateDepartureRecommended = isImmediateDepartureRecommended,
        )
    }
}

data class DepartureTimeRecommendation(
    val calculatedDepartureTime: LocalTime,
    val recommendedDepartureTime: LocalTime,
    val isImmediateDepartureRecommended: Boolean,
)
