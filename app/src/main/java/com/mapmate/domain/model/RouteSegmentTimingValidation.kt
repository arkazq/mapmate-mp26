package com.mapmate.domain.model

fun RouteSegment.hasValidTiming(): Boolean {
    if (segmentIndex < 0 || plannedDurationMinutes < 0) return false
    val start = actualStartedAtEpochMillis
    val end = actualEndedAtEpochMillis
    return when (status) {
        RouteSegmentStatus.NOT_STARTED, RouteSegmentStatus.SKIPPED ->
            start == null && end == null && actualDurationMinutes == null
        RouteSegmentStatus.IN_PROGRESS -> start != null && start >= 0 && end == null && actualDurationMinutes == null
        RouteSegmentStatus.COMPLETED -> start != null && start >= 0 && end != null && end >= start &&
            actualDurationMinutes?.toLong() == (end - start) / 60_000L
    }
}

fun List<RouteSegment>.hasChronologicalMeasuredSegments(): Boolean {
    var previousEnd: Long? = null
    for (segment in sortedBy { it.segmentIndex }) {
        val start = segment.actualStartedAtEpochMillis ?: continue
        if (previousEnd?.let { start < it } == true) return false
        val end = segment.actualEndedAtEpochMillis ?: continue
        if (end < start) return false
        previousEnd = end
    }
    return true
}
