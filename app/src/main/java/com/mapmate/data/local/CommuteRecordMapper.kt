package com.mapmate.data.local

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val commuteRecordTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun CommuteRecord.toEntity(): CommuteRecordEntity {
    return CommuteRecordEntity(
        id = id ?: 0,
        routineId = routineId,
        routineName = routineName,
        originName = originName,
        destinationName = destinationName,
        transportMode = transportMode.name,
        targetArrivalTime = targetArrivalTime.format(commuteRecordTimeFormatter),
        recommendedDepartureTime = recommendedDepartureTime.format(commuteRecordTimeFormatter),
        routeDurationMinutes = routeDurationMinutes,
        routeSummary = routeSummary,
        startedAtEpochMillis = startedAtEpochMillis,
        arrivedAtEpochMillis = arrivedAtEpochMillis,
        arrivalDeltaMinutes = arrivalDeltaMinutes,
    )
}

fun CommuteRecordEntity.toDomain(): CommuteRecord {
    return CommuteRecord(
        id = id,
        routineId = routineId,
        routineName = routineName,
        originName = originName,
        destinationName = destinationName,
        transportMode = TransportMode.valueOf(transportMode),
        targetArrivalTime = LocalTime.parse(targetArrivalTime, commuteRecordTimeFormatter),
        recommendedDepartureTime = LocalTime.parse(recommendedDepartureTime, commuteRecordTimeFormatter),
        routeDurationMinutes = routeDurationMinutes,
        routeSummary = routeSummary,
        startedAtEpochMillis = startedAtEpochMillis,
        arrivedAtEpochMillis = arrivedAtEpochMillis,
        arrivalDeltaMinutes = arrivalDeltaMinutes,
    )
}
