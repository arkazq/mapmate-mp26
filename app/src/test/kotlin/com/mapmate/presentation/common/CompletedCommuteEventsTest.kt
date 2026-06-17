package com.mapmate.presentation.common

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletedCommuteEventsTest {
    private val zoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun completedArrivalEventToExclude_returnsTodayTargetArrivalWhenRecordCompletedBeforeTarget() {
        val now = ZonedDateTime.of(2026, 6, 17, 9, 40, 0, 0, zoneId)
        val record = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(10, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 17, 9, 35, 0, 0, zoneId),
        )

        val result = listOf(record).completedArrivalEventToExclude(
            routine = sampleRoutine(id = 1L, targetArrivalTime = LocalTime.of(10, 30)),
            now = now,
        )

        assertEquals(
            ZonedDateTime.of(2026, 6, 17, 10, 30, 0, 0, zoneId).toEpochMillis(),
            result,
        )
    }

    @Test
    fun completedArrivalEventToExclude_returnsNullWhenTargetArrivalAlreadyPassed() {
        val now = ZonedDateTime.of(2026, 6, 17, 10, 31, 0, 0, zoneId)
        val record = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(10, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 17, 9, 35, 0, 0, zoneId),
        )

        val result = listOf(record).completedArrivalEventToExclude(
            routine = sampleRoutine(id = 1L, targetArrivalTime = LocalTime.of(10, 30)),
            now = now,
        )

        assertNull(result)
    }

    @Test
    fun completedArrivalEventToExclude_ignoresOtherRoutineRecords() {
        val now = ZonedDateTime.of(2026, 6, 17, 9, 40, 0, 0, zoneId)
        val record = sampleRecord(
            routineId = 2L,
            targetArrivalTime = LocalTime.of(10, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 17, 9, 35, 0, 0, zoneId),
        )

        val result = listOf(record).completedArrivalEventToExclude(
            routine = sampleRoutine(id = 1L, targetArrivalTime = LocalTime.of(10, 30)),
            now = now,
        )

        assertNull(result)
    }

    @Test
    fun completedArrivalEventToExclude_handlesOvernightTargetWhenRecordCompletedBeforeMidnight() {
        val now = ZonedDateTime.of(2026, 6, 7, 23, 40, 0, 0, zoneId)
        val record = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(0, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 7, 23, 35, 0, 0, zoneId),
        )

        val result = listOf(record).completedArrivalEventToExclude(
            routine = sampleRoutine(
                id = 1L,
                targetArrivalTime = LocalTime.of(0, 30),
            ),
            now = now,
        )

        assertEquals(
            ZonedDateTime.of(2026, 6, 8, 0, 30, 0, 0, zoneId).toEpochMillis(),
            result,
        )
    }

    @Test
    fun completedArrivalEventToExclude_prefersStoredTargetArrivalEpoch() {
        val targetArrivalAt = ZonedDateTime.of(2026, 6, 8, 0, 30, 0, 0, zoneId)
        val now = ZonedDateTime.of(2026, 6, 7, 23, 40, 0, 0, zoneId)
        val record = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(0, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 8, 1, 50, 0, 0, zoneId),
            targetArrivalAt = targetArrivalAt,
        )

        val result = listOf(record).completedArrivalEventToExclude(
            routine = sampleRoutine(
                id = 1L,
                targetArrivalTime = LocalTime.of(0, 30),
            ),
            now = now,
        )

        assertEquals(targetArrivalAt.toEpochMillis(), result)
    }

    @Test
    fun completedArrivalEventToExclude_handlesBeforeMidnightTargetCompletedAfterMidnight() {
        val now = ZonedDateTime.of(2026, 6, 8, 0, 25, 0, 0, zoneId)
        val record = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(23, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 8, 0, 20, 0, 0, zoneId),
            targetArrivalAt = ZonedDateTime.of(2026, 6, 7, 23, 30, 0, 0, zoneId),
        )

        val result = listOf(record).completedArrivalEventToExclude(
            routine = sampleRoutine(
                id = 1L,
                targetArrivalTime = LocalTime.of(23, 30),
            ),
            now = now,
        )

        assertNull(result)
    }

    @Test
    fun hasCompletedCommuteToday_returnsTrueOnlyForTodayRecords() {
        val now = ZonedDateTime.of(2026, 6, 17, 23, 30, 0, 0, zoneId)
        val todayRecord = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(10, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 17, 9, 35, 0, 0, zoneId),
        )
        val yesterdayRecord = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(10, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 16, 9, 35, 0, 0, zoneId),
        )

        assertEquals(true, listOf(todayRecord).hasCompletedCommuteToday(now))
        assertEquals(false, listOf(yesterdayRecord).hasCompletedCommuteToday(now))
    }

    @Test
    fun hasCompletedCommuteToday_usesTargetArrivalDateForOvernightRecords() {
        val now = ZonedDateTime.of(2026, 6, 8, 0, 10, 0, 0, zoneId)
        val record = sampleRecord(
            routineId = 1L,
            targetArrivalTime = LocalTime.of(0, 30),
            arrivedAt = ZonedDateTime.of(2026, 6, 7, 23, 35, 0, 0, zoneId),
        )

        assertEquals(true, listOf(record).hasCompletedCommuteToday(now))
    }

    private fun sampleRoutine(
        id: Long,
        targetArrivalTime: LocalTime,
    ): Routine {
        return Routine(
            id = id,
            name = "등교",
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
            repeatDays = setOf(RepeatDay.WEDNESDAY),
            transportMode = TransportMode.TRANSIT,
            personalBufferMinutes = 3,
            safetyMarginMinutes = 5,
        )
    }

    private fun sampleRecord(
        routineId: Long,
        targetArrivalTime: LocalTime,
        arrivedAt: ZonedDateTime,
        targetArrivalAt: ZonedDateTime? = null,
    ): CommuteRecord {
        return CommuteRecord(
            id = 10L,
            routineId = routineId,
            routineName = "등교",
            originName = "집",
            destinationName = "학교",
            transportMode = TransportMode.TRANSIT,
            targetArrivalTime = targetArrivalTime,
            targetArrivalAtEpochMillis = targetArrivalAt?.toEpochMillis(),
            recommendedDepartureTime = LocalTime.of(9, 56),
            routeDurationMinutes = 26,
            routeSummary = "테스트 경로",
            startedAtEpochMillis = arrivedAt.minusMinutes(20).toEpochMillis(),
            arrivedAtEpochMillis = arrivedAt.toEpochMillis(),
            arrivalDeltaMinutes = -55,
        )
    }

    private fun ZonedDateTime.toEpochMillis(): Long {
        return Instant.from(this).toEpochMilli()
    }
}
