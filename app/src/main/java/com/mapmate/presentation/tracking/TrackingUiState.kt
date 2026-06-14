package com.mapmate.presentation.tracking

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.Routine
import com.mapmate.presentation.common.RoutineRecommendationUiModel

data class TrackingUiState(
    val routine: Routine,
    val recommendation: RoutineRecommendationUiModel? = null,
    val stage: TrackingStage = TrackingStage.Planned,
    val isLoading: Boolean = true,
    val isSavingRecord: Boolean = false,
    val errorMessage: String? = null,
    val completedRecord: CommuteRecord? = null,
    val adjustedPersonalBufferMinutes: Int? = null,
    val routeSegments: List<RouteSegment> = emptyList(),
) {
    val isCompleted: Boolean
        get() = completedRecord != null

    val hasRouteSegments: Boolean
        get() = routeSegments.isNotEmpty()

    val areRouteSegmentsCompleted: Boolean
        get() = routeSegments.isNotEmpty() &&
            routeSegments.all { it.status == RouteSegmentStatus.COMPLETED }

    val currentSegment: RouteSegment?
        get() = routeSegments.firstOrNull { it.status == RouteSegmentStatus.IN_PROGRESS }
            ?: routeSegments.firstOrNull { it.status == RouteSegmentStatus.NOT_STARTED }

    val currentSegmentIndex: Int
        get() = currentSegment?.let { segment ->
            routeSegments.indexOfFirst { it.trackingSegmentId() == segment.trackingSegmentId() }
                .takeIf { it >= 0 }
                ?.plus(1)
        } ?: totalSegmentCount

    val completedSegmentCount: Int
        get() = routeSegments.count { it.status == RouteSegmentStatus.COMPLETED }

    val finishedSegmentCount: Int
        get() = routeSegments.count {
            it.status == RouteSegmentStatus.COMPLETED ||
                it.status == RouteSegmentStatus.SKIPPED
        }

    val totalSegmentCount: Int
        get() = routeSegments.size

    val isAllSegmentsFinished: Boolean
        get() = routeSegments.isNotEmpty() && routeSegments.all {
            it.status == RouteSegmentStatus.COMPLETED ||
                it.status == RouteSegmentStatus.SKIPPED
        }
}

enum class TrackingStage {
    Planned,
    Boarded,
    Arrived,
}

internal fun RouteSegment.trackingSegmentId(): Long {
    return id ?: segmentIndex.toLong()
}
