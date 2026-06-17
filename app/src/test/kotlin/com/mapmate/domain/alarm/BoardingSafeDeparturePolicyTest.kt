package com.mapmate.domain.alarm

import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteBoardingStatus
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardingSafeDeparturePolicyTest {
    @Test
    fun applyBoardingSafeDeparture_pullsScheduleEarlierWhenSafeDepartureIsEarlier() {
        val schedule = schedule(triggerAtEpochMillis = minutes(21))
        val result = schedule.applyBoardingSafeDeparture(
            boardingAdvice = boardingAdvice(safeDepartureEpochMillis = minutes(6)),
            nowEpochMillis = 0L,
            zoneId = zoneId,
        )

        assertEquals(minutes(6), result.triggerAtEpochMillis)
        assertEquals(LocalTime.of(9, 6), result.recommendedDepartureTime)
    }

    @Test
    fun applyBoardingSafeDeparture_clampsToNowWhenSafeDepartureAlreadyPassed() {
        val schedule = schedule(triggerAtEpochMillis = minutes(21))
        val result = schedule.applyBoardingSafeDeparture(
            boardingAdvice = boardingAdvice(safeDepartureEpochMillis = minutes(6)),
            nowEpochMillis = minutes(8),
            zoneId = zoneId,
        )

        assertEquals(minutes(8), result.triggerAtEpochMillis)
        assertEquals(LocalTime.of(9, 8), result.recommendedDepartureTime)
    }

    @Test
    fun applyBoardingSafeDeparture_keepsScheduleWhenSafeDepartureIsNotEarlier() {
        val schedule = schedule(triggerAtEpochMillis = minutes(21))
        val result = schedule.applyBoardingSafeDeparture(
            boardingAdvice = boardingAdvice(safeDepartureEpochMillis = minutes(25)),
            nowEpochMillis = 0L,
            zoneId = zoneId,
        )

        assertEquals(schedule, result)
    }

    private fun schedule(triggerAtEpochMillis: Long): DepartureAlarmSchedule {
        return DepartureAlarmSchedule(
            routineId = 1L,
            routineName = "routine",
            destinationName = "destination",
            targetArrivalTime = LocalTime.of(10, 0),
            recommendedDepartureTime = LocalTime.of(9, 21),
            routeDurationMinutes = 30,
            triggerAtEpochMillis = triggerAtEpochMillis,
        )
    }

    private fun boardingAdvice(safeDepartureEpochMillis: Long): RouteBoardingAdvice {
        return RouteBoardingAdvice(
            selectedCandidateIndex = 1,
            candidateCount = 1,
            routeName = "753",
            stationName = "start",
            accessMinutes = 4,
            realtimeWaitMinutes = 13,
            slackMinutes = -12,
            status = RouteBoardingStatus.MISS_RISK,
            estimatedTotalMinutes = 26,
            safeDepartureEpochMillis = safeDepartureEpochMillis,
            earlyDepartureRequiredMinutes = 15,
        )
    }

    private fun minutes(value: Int): Long = BASE_EPOCH_MILLIS + value * 60_000L

    private companion object {
        val zoneId: ZoneId = ZoneId.of("Asia/Seoul")
        val BASE_EPOCH_MILLIS: Long = java.time.ZonedDateTime
            .of(2026, 6, 16, 9, 0, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
