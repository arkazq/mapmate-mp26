package com.mapmate.data.local

import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType

fun RouteSegment.toEntity(commuteRecordId: Long): RouteSegmentEntity {
    return RouteSegmentEntity(
        id = id ?: 0,
        commuteRecordId = commuteRecordId,
        routineId = routineId,
        segmentIndex = segmentIndex,
        segmentType = segmentType.name,
        trafficType = trafficType,
        routeName = routeName,
        startName = startName,
        endName = endName,
        plannedDurationMinutes = plannedDurationMinutes,
        actualStartedAtEpochMillis = actualStartedAtEpochMillis,
        actualEndedAtEpochMillis = actualEndedAtEpochMillis,
        actualDurationMinutes = actualDurationMinutes,
        isUserEdited = isUserEdited,
        status = status.name,
    )
}

fun RouteSegmentEntity.toDomain(): RouteSegment {
    return RouteSegment(
        id = id,
        commuteRecordId = commuteRecordId,
        routineId = routineId,
        segmentIndex = segmentIndex,
        segmentType = RouteSegmentType.valueOf(segmentType),
        trafficType = trafficType,
        routeName = routeName,
        startName = startName,
        endName = endName,
        plannedDurationMinutes = plannedDurationMinutes,
        actualStartedAtEpochMillis = actualStartedAtEpochMillis,
        actualEndedAtEpochMillis = actualEndedAtEpochMillis,
        actualDurationMinutes = actualDurationMinutes,
        isUserEdited = isUserEdited,
        status = RouteSegmentStatus.valueOf(status),
    )
}
