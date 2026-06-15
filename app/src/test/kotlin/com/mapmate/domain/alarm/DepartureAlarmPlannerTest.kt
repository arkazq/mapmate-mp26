package com.mapmate.domain.alarm

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DepartureAlarmPlannerTest {
    private val zoneId = ZoneId.of("Asia/Seoul")
    private val planner = DepartureAlarmPlanner(zoneId = zoneId)

    @Test
    fun nextAlarmForRoutine_returnsTodayWhenDepartureTimeIsStillFuture() {
        val routine = sampleRoutine(repeatDays = setOf(RepeatDay.MONDAY))
        val now = ZonedDateTime.of(2026, 6, 8, 7, 0, 0, 0, zoneId)

        val result = planner.nextAlarmForRoutine(
            routine = routine,
            routeDurationMinutes = 42,
            now = now,
        )

        assertNotNull(result)
        assertEquals(LocalTime.of(8, 7), result!!.recommendedDepartureTime)
        assertEquals(
            ZonedDateTime.of(2026, 6, 8, 8, 7, 0, 0, zoneId).toEpochMillis(),
            result.triggerAtEpochMillis,
        )
    }

    @Test
    fun nextAlarmForRoutine_returnsNowWhenTodayDepartureTimePassedButArrivalIsAhead() {
        val routine = sampleRoutine(repeatDays = setOf(RepeatDay.MONDAY))
        val now = ZonedDateTime.of(2026, 6, 8, 8, 8, 0, 0, zoneId)

        val result = planner.nextAlarmForRoutine(
            routine = routine,
            routeDurationMinutes = 42,
            now = now,
        )

        assertNotNull(result)
        assertEquals(LocalTime.of(8, 8), result!!.recommendedDepartureTime)
        assertEquals(
            now.toEpochMillis(),
            result.triggerAtEpochMillis,
        )
    }

    @Test
    fun nextAlarmForRoutine_skipsFiredArrivalEventWhenReschedulingAfterNotification() {
        val routine = sampleRoutine(repeatDays = setOf(RepeatDay.MONDAY))
        val now = ZonedDateTime.of(2026, 6, 8, 8, 8, 0, 0, zoneId)
        val firedArrivalAt = ZonedDateTime.of(2026, 6, 8, 9, 0, 0, 0, zoneId).toEpochMillis()

        val result = planner.nextAlarmForRoutine(
            routine = routine,
            routeDurationMinutes = 42,
            now = now,
            excludedArrivalAtEpochMillis = firedArrivalAt,
        )

        assertNotNull(result)
        assertEquals(LocalTime.of(8, 7), result!!.recommendedDepartureTime)
        assertEquals(
            ZonedDateTime.of(2026, 6, 15, 8, 7, 0, 0, zoneId).toEpochMillis(),
            result.triggerAtEpochMillis,
        )
    }

    @Test
    fun nextAlarmForRoutine_returnsNextWeekWhenTodayArrivalTimeAlreadyPassed() {
        val routine = sampleRoutine(repeatDays = setOf(RepeatDay.MONDAY))
        val now = ZonedDateTime.of(2026, 6, 8, 9, 1, 0, 0, zoneId)

        val result = planner.nextAlarmForRoutine(
            routine = routine,
            routeDurationMinutes = 42,
            now = now,
        )

        assertNotNull(result)
        assertEquals(LocalTime.of(8, 7), result!!.recommendedDepartureTime)
        assertEquals(
            ZonedDateTime.of(2026, 6, 15, 8, 7, 0, 0, zoneId).toEpochMillis(),
            result.triggerAtEpochMillis,
        )
    }

    @Test
    fun nextAlarm_returnsNearestRoutineAcrossRoutines() {
        val mondayRoutine = sampleRoutine(
            id = 1,
            name = "월요일 등교",
            repeatDays = setOf(RepeatDay.MONDAY),
        )
        val wednesdayRoutine = sampleRoutine(
            id = 2,
            name = "수요일 등교",
            repeatDays = setOf(RepeatDay.WEDNESDAY),
        )
        val now = ZonedDateTime.of(2026, 6, 9, 9, 0, 0, 0, zoneId)

        val result = planner.nextAlarm(
            routineRouteDurations = listOf(
                mondayRoutine to 42,
                wednesdayRoutine to 42,
            ),
            now = now,
        )

        assertNotNull(result)
        assertEquals(2L, result!!.routineId)
        assertEquals(
            ZonedDateTime.of(2026, 6, 10, 8, 7, 0, 0, zoneId).toEpochMillis(),
            result.triggerAtEpochMillis,
        )
    }

    @Test
    fun nextAlarmForRoutine_handlesDepartureOnPreviousDateForOvernightArrival() {
        val routine = sampleRoutine(
            targetArrivalTime = LocalTime.of(0, 30),
            repeatDays = setOf(RepeatDay.MONDAY),
            personalBufferMinutes = 10,
            safetyMarginMinutes = 5,
        )
        val now = ZonedDateTime.of(2026, 6, 7, 22, 0, 0, 0, zoneId)

        val result = planner.nextAlarmForRoutine(
            routine = routine,
            routeDurationMinutes = 45,
            now = now,
        )

        assertNotNull(result)
        assertEquals(LocalTime.of(23, 30), result!!.recommendedDepartureTime)
        assertEquals(
            ZonedDateTime.of(2026, 6, 7, 23, 30, 0, 0, zoneId).toEpochMillis(),
            result.triggerAtEpochMillis,
        )
    }

    private fun sampleRoutine(
        id: Long = 1,
        name: String = "등교",
        targetArrivalTime: LocalTime = LocalTime.of(9, 0),
        repeatDays: Set<RepeatDay> = setOf(RepeatDay.MONDAY),
        personalBufferMinutes: Int = 6,
        safetyMarginMinutes: Int = 5,
    ): Routine {
        return Routine(
            id = id,
            name = name,
            origin = Destination(
                name = "집",
                address = "집 주소",
                latitude = 37.0,
                longitude = 127.0,
            ),
            destination = Destination(
                name = "학교",
                address = "학교 주소",
                latitude = 37.5,
                longitude = 127.5,
            ),
            targetArrivalTime = targetArrivalTime,
            repeatDays = repeatDays,
            transportMode = TransportMode.TRANSIT,
            personalBufferMinutes = personalBufferMinutes,
            safetyMarginMinutes = safetyMarginMinutes,
        )
    }

    private fun ZonedDateTime.toEpochMillis(): Long {
        return Instant.from(this).toEpochMilli()
    }
}
