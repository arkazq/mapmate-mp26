package com.mapmate.data.remote.provider

import com.mapmate.data.remote.dto.OdsayPath
import com.mapmate.data.remote.dto.OdsaySubPath
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType

internal fun OdsayPath.toRouteSegments(routineId: Long?): List<RouteSegment> {
    return subPath.flatMapIndexed { index, subPath ->
        val plannedDurationMinutes = subPath.sectionTime?.takeIf { it >= 0 } ?: return@flatMapIndexed emptyList()
        val segment = RouteSegment(
            routineId = routineId,
            segmentIndex = index,
            segmentType = subPath.toSegmentType(index),
            trafficType = subPath.trafficType,
            routeName = subPath.routeName(),
            startName = subPath.startName,
            endName = subPath.endName,
            plannedDurationMinutes = plannedDurationMinutes,
        )
        val waitSegment = subPath.toTransitWaitSegment(routineId = routineId, fallbackIndex = index)
        listOfNotNull(waitSegment, segment)
    }
        .markLastWalkToDestination()
        .mapIndexed { index, segment -> segment.copy(segmentIndex = index) }
}

private fun OdsaySubPath.toSegmentType(index: Int): RouteSegmentType {
    return when (trafficType) {
        TRAFFIC_TYPE_SUBWAY -> RouteSegmentType.SUBWAY_RIDE
        TRAFFIC_TYPE_BUS -> RouteSegmentType.BUS_RIDE
        TRAFFIC_TYPE_WALK -> if (index == 0) {
            RouteSegmentType.WALK_TO_TRANSIT
        } else {
            RouteSegmentType.TRANSFER_WALK
        }
        else -> RouteSegmentType.UNKNOWN
    }
}

private fun OdsaySubPath.routeName(): String? {
    val firstLane = lane.firstOrNull()
    return firstLane?.busNo
        ?: firstLane?.routeNm
        ?: firstLane?.name
        ?: firstLane?.subwayCode?.toString()
}

private fun OdsaySubPath.toTransitWaitSegment(
    routineId: Long?,
    fallbackIndex: Int,
): RouteSegment? {
    val waitSegmentType = when (trafficType) {
        TRAFFIC_TYPE_BUS -> RouteSegmentType.WAIT_FOR_BUS
        TRAFFIC_TYPE_SUBWAY -> RouteSegmentType.WAIT_FOR_SUBWAY
        else -> return null
    }
    return RouteSegment(
        routineId = routineId,
        segmentIndex = fallbackIndex,
        segmentType = waitSegmentType,
        trafficType = trafficType,
        routeName = routeName(),
        startName = startName,
        endName = startName,
        plannedDurationMinutes = PLANNED_TRANSIT_WAIT_MINUTES,
    )
}

private fun List<RouteSegment>.markLastWalkToDestination(): List<RouteSegment> {
    val lastWalkIndex = indexOfLast {
        it.segmentType == RouteSegmentType.WALK_TO_TRANSIT ||
            it.segmentType == RouteSegmentType.TRANSFER_WALK
    }
    if (lastWalkIndex <= 0) return this

    return mapIndexed { index, segment ->
        if (index == lastWalkIndex) {
            segment.copy(segmentType = RouteSegmentType.WALK_TO_DESTINATION)
        } else {
            segment
        }
    }
}

private const val TRAFFIC_TYPE_SUBWAY = 1
private const val TRAFFIC_TYPE_BUS = 2
private const val TRAFFIC_TYPE_WALK = 3
private const val PLANNED_TRANSIT_WAIT_MINUTES = 5
