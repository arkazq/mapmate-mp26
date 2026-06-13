package com.mapmate.presentation.history

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordsStatsTest {
    @Test
    fun from_returnsEmptyStatsWhenNoRecordsExist() {
        val stats = RecordsStats.from(emptyList())

        assertEquals(0, stats.totalRecords)
        assertEquals(0, stats.averageArrivalDeltaMinutes)
        assertEquals(0, stats.onTimeRatePercent)
        assertEquals(0, stats.lateRecords)
        assertEquals(0, stats.recentAverageDeltaMinutes)
        assertNull(stats.mostUsedTransportMode)
    }

    @Test
    fun from_summarizesArrivalAccuracyAndTransportMode() {
        val records = listOf(
            record(delta = -4, mode = TransportMode.TRANSIT, arrivedAt = 6000L),
            record(delta = 0, mode = TransportMode.TRANSIT, arrivedAt = 5000L),
            record(delta = 3, mode = TransportMode.WALK, arrivedAt = 4000L),
            record(delta = 7, mode = TransportMode.TRANSIT, arrivedAt = 3000L),
            record(delta = -1, mode = TransportMode.CAR, arrivedAt = 2000L),
            record(delta = 8, mode = TransportMode.WALK, arrivedAt = 1000L),
        )

        val stats = RecordsStats.from(records)

        assertEquals(6, stats.totalRecords)
        assertEquals(2, stats.averageArrivalDeltaMinutes)
        assertEquals(50, stats.onTimeRatePercent)
        assertEquals(3, stats.lateRecords)
        assertEquals(TransportMode.TRANSIT, stats.mostUsedTransportMode)
        assertEquals(1, stats.recentAverageDeltaMinutes)
    }

    private fun record(
        delta: Int,
        mode: TransportMode,
        arrivedAt: Long,
    ): CommuteRecord {
        return CommuteRecord(
            routineId = 1L,
            routineName = "등교",
            originName = "집",
            destinationName = "학교",
            transportMode = mode,
            targetArrivalTime = LocalTime.of(9, 0),
            recommendedDepartureTime = LocalTime.of(8, 20),
            routeDurationMinutes = 30,
            routeSummary = "테스트 경로",
            startedAtEpochMillis = arrivedAt - 1_800_000L,
            arrivedAtEpochMillis = arrivedAt,
            arrivalDeltaMinutes = delta,
        )
    }
}
