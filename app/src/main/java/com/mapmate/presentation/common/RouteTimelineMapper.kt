package com.mapmate.presentation.common

import com.mapmate.domain.model.RouteSegmentType

fun RoutineRecommendationUiModel.routeTimelineItems(): List<RouteTimelineItem> {
    val departure = RouteTimelineItem(
        title = routine.origin.name,
        description = "출발",
        timeText = recommendedDepartureTimeText,
        icon = MapMateIconType.Location,
    )
    val arrival = RouteTimelineItem(
        title = routine.destination.name,
        description = "도착 목표",
        timeText = targetArrivalTimeText,
        icon = MapMateIconType.Flag,
    )
    val legs = routeSegments.sortedBy { it.segmentIndex }.map { segment ->
        val title = when (segment.segmentType) {
            RouteSegmentType.WALK_TO_TRANSIT -> "정류장·역까지 도보"
            RouteSegmentType.WAIT_FOR_BUS -> "버스 대기"
            RouteSegmentType.BUS_RIDE -> segment.routeName?.let { "${it}번 버스" } ?: "버스"
            RouteSegmentType.WAIT_FOR_SUBWAY -> "지하철 대기"
            RouteSegmentType.SUBWAY_RIDE -> segment.routeName?.let { "$it 지하철" } ?: "지하철"
            RouteSegmentType.TRANSFER_WALK -> "환승 이동"
            RouteSegmentType.WALK_TO_DESTINATION -> "목적지까지 도보"
            RouteSegmentType.UNKNOWN -> "이동 구간"
        }
        val icon = when (segment.segmentType) {
            RouteSegmentType.WALK_TO_TRANSIT, RouteSegmentType.TRANSFER_WALK,
            RouteSegmentType.WALK_TO_DESTINATION -> MapMateIconType.Walk
            RouteSegmentType.BUS_RIDE -> MapMateIconType.Bus
            RouteSegmentType.SUBWAY_RIDE -> MapMateIconType.Train
            RouteSegmentType.WAIT_FOR_BUS, RouteSegmentType.WAIT_FOR_SUBWAY -> MapMateIconType.Time
            RouteSegmentType.UNKNOWN -> MapMateIconType.Route
        }
        RouteTimelineItem(
            title = title,
            description = listOfNotNull(segment.startName, segment.endName)
                .filter(String::isNotBlank).joinToString(" → "),
            timeText = "${segment.plannedDurationMinutes}분",
            icon = icon,
        )
    }.ifEmpty {
        listOf(RouteTimelineItem(
            title = routine.transportMode.toKoreanDescription(),
            description = if (isFallbackEstimate) "기본 예상 시간" else "전체 이동",
            timeText = "${routeDurationMinutes}분",
            icon = transportModeIcon(routine.transportMode),
        ))
    }
    return listOf(departure) + legs + arrival
}
