package com.mapmate.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TrackingSession(
    val routineId: Long,
    val routineFingerprint: String,
    val targetArrivalAtEpochMillis: Long,
    val recommendedDepartureAtEpochMillis: Long,
    val routeDurationMinutes: Int,
    val routeSummary: String,
    val startedAtEpochMillis: Long,
    val routeSegments: List<RouteSegment>,
    val isFallbackEstimate: Boolean = false,
) {
    fun isValid(): Boolean = routineId > 0 && targetArrivalAtEpochMillis > 0 &&
        recommendedDepartureAtEpochMillis > 0 && startedAtEpochMillis > 0 &&
        routeDurationMinutes >= 0 && routeSegments.size <= 100 &&
        routeSegments.map { it.segmentIndex }.distinct().size == routeSegments.size &&
        routeSegments.count { it.status == RouteSegmentStatus.IN_PROGRESS } <= 1 &&
        routeSegments.hasChronologicalMeasuredSegments() && routeSegments.all { segment ->
            segment.routineId == routineId && segment.hasValidTiming() &&
                (segment.actualStartedAtEpochMillis?.let { it >= startedAtEpochMillis } != false)
        }
}
