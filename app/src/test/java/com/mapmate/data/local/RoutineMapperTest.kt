package com.mapmate.data.local

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class RoutineMapperTest {
    @Test
    fun toEntity_flattensRoutineForRoomStorage() {
        val routine = Routine(
            name = "학교 가는 길",
            destination = Destination(
                name = "숭실대학교",
                address = "서울특별시 동작구 상도로 369",
                latitude = 37.4963,
                longitude = 126.9574,
            ),
            targetArrivalTime = LocalTime.of(9, 0),
            repeatDays = setOf(RepeatDay.WEDNESDAY, RepeatDay.MONDAY),
            transportMode = TransportMode.TRANSIT,
            personalBufferMinutes = 6,
            safetyMarginMinutes = 5,
        )

        val entity = routine.toEntity(createdAtEpochMillis = 1234L)

        assertEquals(0L, entity.id)
        assertEquals("학교 가는 길", entity.name)
        assertEquals("숭실대학교", entity.destinationName)
        assertEquals("서울특별시 동작구 상도로 369", entity.destinationAddress)
        assertEquals(37.4963, entity.destinationLatitude ?: 0.0, 0.0001)
        assertEquals(126.9574, entity.destinationLongitude ?: 0.0, 0.0001)
        assertEquals("09:00", entity.targetArrivalTime)
        assertEquals("MONDAY,WEDNESDAY", entity.repeatDays)
        assertEquals("TRANSIT", entity.transportMode)
        assertEquals(6, entity.personalBufferMinutes)
        assertEquals(5, entity.safetyMarginMinutes)
        assertEquals(1234L, entity.createdAtEpochMillis)
    }

    @Test
    fun toDomain_restoresRoutineFromRoomEntity() {
        val entity = RoutineEntity(
            id = 7L,
            name = "퇴근",
            destinationName = "강남역",
            destinationAddress = "서울특별시 강남구 강남대로 지하396",
            destinationLatitude = 37.4979,
            destinationLongitude = 127.0276,
            targetArrivalTime = "18:30",
            repeatDays = "MONDAY,TUESDAY,FRIDAY",
            transportMode = "CAR",
            personalBufferMinutes = 10,
            safetyMarginMinutes = 15,
            createdAtEpochMillis = 5678L,
        )

        val routine = entity.toDomain()

        assertEquals(7L, routine.id)
        assertEquals("퇴근", routine.name)
        assertEquals("강남역", routine.destination.name)
        assertEquals("서울특별시 강남구 강남대로 지하396", routine.destination.address)
        assertEquals(37.4979, routine.destination.latitude ?: 0.0, 0.0001)
        assertEquals(127.0276, routine.destination.longitude ?: 0.0, 0.0001)
        assertEquals(LocalTime.of(18, 30), routine.targetArrivalTime)
        assertEquals(
            setOf(RepeatDay.MONDAY, RepeatDay.TUESDAY, RepeatDay.FRIDAY),
            routine.repeatDays,
        )
        assertEquals(TransportMode.CAR, routine.transportMode)
        assertEquals(10, routine.personalBufferMinutes)
        assertEquals(15, routine.safetyMarginMinutes)
    }
}
