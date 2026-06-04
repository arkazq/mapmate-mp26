package com.mapmate.data.local;

import static org.junit.Assert.assertEquals;

import com.mapmate.domain.model.Destination;
import com.mapmate.domain.model.RepeatDay;
import com.mapmate.domain.model.Routine;
import com.mapmate.domain.model.TransportMode;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.Test;

public class RoutineMapperTest {
    @Test
    public void toEntity_flattensRoutineForRoomStorage() {
        Routine routine = new Routine(
                null,
                "학교 가는 길",
                new Destination(
                        "숭실대학교",
                        "서울특별시 동작구 상도로 369",
                        37.4963,
                        126.9574
                ),
                LocalTime.of(9, 0),
                EnumSet.of(RepeatDay.WEDNESDAY, RepeatDay.MONDAY),
                TransportMode.TRANSIT,
                6,
                5
        );

        RoutineEntity entity = RoutineMapperKt.toEntity(routine, 1234L);

        assertEquals(0L, entity.getId());
        assertEquals("학교 가는 길", entity.getName());
        assertEquals("숭실대학교", entity.getDestinationName());
        assertEquals("서울특별시 동작구 상도로 369", entity.getDestinationAddress());
        assertEquals(37.4963, entity.getDestinationLatitude(), 0.0001);
        assertEquals(126.9574, entity.getDestinationLongitude(), 0.0001);
        assertEquals("09:00", entity.getTargetArrivalTime());
        assertEquals("MONDAY,WEDNESDAY", entity.getRepeatDays());
        assertEquals("TRANSIT", entity.getTransportMode());
        assertEquals(6, entity.getPersonalBufferMinutes());
        assertEquals(5, entity.getSafetyMarginMinutes());
        assertEquals(1234L, entity.getCreatedAtEpochMillis());
    }

    @Test
    public void toDomain_restoresRoutineFromRoomEntity() {
        RoutineEntity entity = new RoutineEntity(
                7L,
                "퇴근",
                "강남역",
                "서울특별시 강남구 강남대로 지하396",
                37.4979,
                127.0276,
                "18:30",
                "MONDAY,TUESDAY,FRIDAY",
                "CAR",
                10,
                15,
                5678L
        );

        Routine routine = RoutineMapperKt.toDomain(entity);

        assertEquals(Long.valueOf(7L), routine.getId());
        assertEquals("퇴근", routine.getName());
        assertEquals("강남역", routine.getDestination().getName());
        assertEquals("서울특별시 강남구 강남대로 지하396", routine.getDestination().getAddress());
        assertEquals(37.4979, routine.getDestination().getLatitude(), 0.0001);
        assertEquals(127.0276, routine.getDestination().getLongitude(), 0.0001);
        assertEquals(LocalTime.of(18, 30), routine.getTargetArrivalTime());
        assertEquals(
                EnumSet.of(RepeatDay.MONDAY, RepeatDay.TUESDAY, RepeatDay.FRIDAY),
                routine.getRepeatDays()
        );
        assertEquals(TransportMode.CAR, routine.getTransportMode());
        assertEquals(10, routine.getPersonalBufferMinutes());
        assertEquals(15, routine.getSafetyMarginMinutes());
    }
}
