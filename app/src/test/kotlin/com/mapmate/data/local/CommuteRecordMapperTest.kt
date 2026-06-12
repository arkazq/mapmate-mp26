package com.mapmate.data.local

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CommuteRecordMapperTest {
    @Test
    fun toEntity_flattensRecordForRoomStorage() {
        val record = CommuteRecord(
            id = null,
            routineId = 3L,
            routineName = "학교 가는 길",
            originName = "집",
            destinationName = "숭실대학교",
            transportMode = TransportMode.TRANSIT,
            targetArrivalTime = LocalTime.of(9, 0),
            recommendedDepartureTime = LocalTime.of(8, 7),
            routeDurationMinutes = 42,
            routeSummary = "대중교통 기준 42분 예상",
            startedAtEpochMillis = 1000L,
            arrivedAtEpochMillis = 2000L,
            arrivalDeltaMinutes = 2,
        )

        val entity = record.toEntity()

        assertEquals(0L, entity.id)
        assertEquals(3L, entity.routineId)
        assertEquals("학교 가는 길", entity.routineName)
        assertEquals("집", entity.originName)
        assertEquals("숭실대학교", entity.destinationName)
        assertEquals("TRANSIT", entity.transportMode)
        assertEquals("09:00", entity.targetArrivalTime)
        assertEquals("08:07", entity.recommendedDepartureTime)
        assertEquals(42, entity.routeDurationMinutes)
        assertEquals("대중교통 기준 42분 예상", entity.routeSummary)
        assertEquals(1000L, entity.startedAtEpochMillis)
        assertEquals(2000L, entity.arrivedAtEpochMillis)
        assertEquals(2, entity.arrivalDeltaMinutes)
    }

    @Test
    fun toDomain_restoresRecordFromRoomEntity() {
        val entity = CommuteRecordEntity(
            id = 8L,
            routineId = null,
            routineName = "퇴근",
            originName = "회사",
            destinationName = "집",
            transportMode = "CAR",
            targetArrivalTime = "18:30",
            recommendedDepartureTime = "17:52",
            routeDurationMinutes = 25,
            routeSummary = "자동차 기준 25분 예상",
            startedAtEpochMillis = 3000L,
            arrivedAtEpochMillis = 4000L,
            arrivalDeltaMinutes = -3,
        )

        val record = entity.toDomain()

        assertEquals(8L, record.id)
        assertEquals(null, record.routineId)
        assertEquals("퇴근", record.routineName)
        assertEquals("회사", record.originName)
        assertEquals("집", record.destinationName)
        assertEquals(TransportMode.CAR, record.transportMode)
        assertEquals(LocalTime.of(18, 30), record.targetArrivalTime)
        assertEquals(LocalTime.of(17, 52), record.recommendedDepartureTime)
        assertEquals(25, record.routeDurationMinutes)
        assertEquals("자동차 기준 25분 예상", record.routeSummary)
        assertEquals(3000L, record.startedAtEpochMillis)
        assertEquals(4000L, record.arrivedAtEpochMillis)
        assertEquals(-3, record.arrivalDeltaMinutes)
    }
}
