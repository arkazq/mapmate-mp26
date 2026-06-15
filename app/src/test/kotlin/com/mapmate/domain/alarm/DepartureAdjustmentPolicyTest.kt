package com.mapmate.domain.alarm

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureAdjustmentPolicyTest {
    private val zoneId = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 6, 15, 7, 0, 0, 0, zoneId).toEpochMillis()
    private val policy = DepartureAdjustmentPolicy(
        nowProvider = { now },
        zoneId = zoneId,
    )

    @Test
    fun adjust_returnsProposedWhenNoPreviousScheduleExists() {
        val proposed = scheduleAt(8, 0)

        val result = policy.adjust(
            previousSchedule = null,
            proposedSchedule = proposed,
        )

        assertEquals(proposed, result)
    }

    @Test
    fun adjust_reschedulesEarlierWhenNewDepartureIsAtLeastThreeMinutesEarlier() {
        val previous = scheduleAt(8, 0)
        val proposed = scheduleAt(7, 56)

        val result = policy.adjust(
            previousSchedule = previous,
            proposedSchedule = proposed,
        )

        assertEquals(proposed, result)
    }

    @Test
    fun adjust_keepsPreviousWhenChangeIsSmall() {
        val previous = scheduleAt(8, 0)
        val proposed = scheduleAt(8, 2)

        val result = policy.adjust(
            previousSchedule = previous,
            proposedSchedule = proposed,
        )

        assertEquals(previous, result)
    }

    @Test
    fun adjust_capsLaterDepartureShift() {
        val previous = scheduleAt(8, 0)
        val proposed = scheduleAt(8, 30)

        val result = policy.adjust(
            previousSchedule = previous,
            proposedSchedule = proposed,
        )

        assertEquals(LocalTime.of(8, 10), result.recommendedDepartureTime)
        assertEquals(scheduleAt(8, 10).triggerAtEpochMillis, result.triggerAtEpochMillis)
    }

    @Test
    fun adjust_notifiesNowWhenNewDepartureIsWithinFiveMinutes() {
        val previous = scheduleAt(8, 0)
        val proposed = scheduleAt(7, 4)

        val result = policy.adjust(
            previousSchedule = previous,
            proposedSchedule = proposed,
        )

        assertEquals(LocalTime.of(7, 0), result.recommendedDepartureTime)
        assertEquals(now, result.triggerAtEpochMillis)
    }

    @Test
    fun adjust_ignoresPreviousScheduleWhenPreviousDepartureAlreadyPassed() {
        val previous = scheduleAt(6, 50)
        val proposed = scheduleAt(8, 0)

        val result = policy.adjust(
            previousSchedule = previous,
            proposedSchedule = proposed,
        )

        assertEquals(proposed, result)
    }

    @Test
    fun adjust_ignoresPreviousScheduleWhenTargetArrivalEventChanged() {
        val previous = scheduleAt(
            hour = 8,
            minute = 0,
            targetArrivalAtEpochMillis = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zoneId).toEpochMillis(),
        )
        val proposed = scheduleAt(
            hour = 8,
            minute = 30,
            targetArrivalAtEpochMillis = ZonedDateTime.of(2026, 6, 16, 9, 0, 0, 0, zoneId).toEpochMillis(),
        )

        val result = policy.adjust(
            previousSchedule = previous,
            proposedSchedule = proposed,
        )

        assertEquals(proposed, result)
    }

    private fun scheduleAt(
        hour: Int,
        minute: Int,
        routineId: Long = 7L,
        targetArrivalAtEpochMillis: Long? = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zoneId).toEpochMillis(),
    ): DepartureAlarmSchedule {
        val triggerAt = ZonedDateTime.of(2026, 6, 15, hour, minute, 0, 0, zoneId)
        return DepartureAlarmSchedule(
            routineId = routineId,
            routineName = "Routine",
            destinationName = "Destination",
            targetArrivalTime = LocalTime.of(9, 0),
            recommendedDepartureTime = LocalTime.of(hour, minute),
            routeDurationMinutes = 40,
            triggerAtEpochMillis = triggerAt.toEpochMillis(),
            targetArrivalAtEpochMillis = targetArrivalAtEpochMillis,
        )
    }

    private fun ZonedDateTime.toEpochMillis(): Long {
        return toInstant().toEpochMilli()
    }
}
