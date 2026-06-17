package com.mapmate.domain.alarm

import com.mapmate.domain.model.RouteBoardingAdvice
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

fun DepartureAlarmSchedule.applyBoardingSafeDeparture(
    boardingAdvice: RouteBoardingAdvice?,
    nowEpochMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): DepartureAlarmSchedule {
    val safeDepartureEpochMillis = boardingAdvice
        ?.safeDepartureEpochMillis
        ?.takeIf { it < triggerAtEpochMillis }
        ?: return this
    val adjustedTriggerAtEpochMillis = maxOf(safeDepartureEpochMillis, nowEpochMillis)
    val adjustedDepartureTime = Instant.ofEpochMilli(adjustedTriggerAtEpochMillis)
        .atZone(zoneId)
        .toLocalTime()
        .truncatedTo(ChronoUnit.MINUTES)

    return copy(
        recommendedDepartureTime = adjustedDepartureTime,
        triggerAtEpochMillis = adjustedTriggerAtEpochMillis,
    )
}
