package com.mapmate.data.local

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val routineTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Routine.toEntity(
    createdAtEpochMillis: Long = System.currentTimeMillis(),
): RoutineEntity {
    return RoutineEntity(
        id = id ?: 0,
        name = name,
        originName = origin.name,
        originAddress = origin.address,
        originLatitude = origin.latitude,
        originLongitude = origin.longitude,
        destinationName = destination.name,
        destinationAddress = destination.address,
        destinationLatitude = destination.latitude,
        destinationLongitude = destination.longitude,
        targetArrivalTime = targetArrivalTime.format(routineTimeFormatter),
        repeatDays = repeatDays
            .sortedBy(RepeatDay::ordinal)
            .joinToString(separator = ",") { it.name },
        transportMode = transportMode.name,
        personalBufferMinutes = personalBufferMinutes,
        safetyMarginMinutes = safetyMarginMinutes,
        createdAtEpochMillis = createdAtEpochMillis,
    )
}

fun RoutineEntity.toDomain(): Routine {
    return Routine(
        id = id,
        name = name,
        origin = Destination(
            name = originName,
            address = originAddress,
            latitude = originLatitude,
            longitude = originLongitude,
        ),
        destination = Destination(
            name = destinationName,
            address = destinationAddress,
            latitude = destinationLatitude,
            longitude = destinationLongitude,
        ),
        targetArrivalTime = LocalTime.parse(targetArrivalTime, routineTimeFormatter),
        repeatDays = repeatDays
            .splitToSequence(",")
            .filter(String::isNotBlank)
            .map { RepeatDay.valueOf(it) }
            .toSet(),
        transportMode = TransportMode.valueOf(transportMode),
        personalBufferMinutes = personalBufferMinutes,
        safetyMarginMinutes = safetyMarginMinutes,
    )
}
