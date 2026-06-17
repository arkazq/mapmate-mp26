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
        targetArrivalAtEpochMillis = targetArrivalAtEpochMillis,
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
        targetArrivalAtEpochMillis = targetArrivalAtEpochMillis,
        recommendedDepartureTime = LocalTime.parse(recommendedDepartureTime, commuteRecordTimeFormatter),
        routeDurationMinutes = routeDurationMinutes,
        routeSummary = routeSummary,
        startedAtEpochMillis = startedAtEpochMillis,
        arrivedAtEpochMillis = arrivedAtEpochMillis,
        arrivalDeltaMinutes = arrivalDeltaMinutes,
    )
}
