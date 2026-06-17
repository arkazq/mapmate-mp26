package com.mapmate.domain.model

import java.time.LocalTime

data class CommuteRecord(
    val id: Long? = null,
    val routineId: Long?,
    val routineName: String,
    val originName: String,
    val destinationName: String,
    val transportMode: TransportMode,
    val targetArrivalTime: LocalTime,
    val targetArrivalAtEpochMillis: Long? = null,
    val recommendedDepartureTime: LocalTime,
    val routeDurationMinutes: Int,
    val routeSummary: String,
    val startedAtEpochMillis: Long,
    val arrivedAtEpochMillis: Long,
    val arrivalDeltaMinutes: Int,
    val routeSegments: List<RouteSegment> = emptyList(),
)
