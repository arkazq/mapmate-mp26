package com.mapmate.data.local

import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.SegmentTimeAdjustment

fun SegmentTimeAdjustment.toEntity(): SegmentTimeAdjustmentEntity {
    return SegmentTimeAdjustmentEntity(
        id = id ?: 0,
        routineId = routineId,
        segmentType = segmentType.name,
        routeName = routeName,
        startName = startName,
        endName = endName,
        averageDelayMinutes = averageDelayMinutes,
        sampleCount = sampleCount,
        confidence = confidence,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun SegmentTimeAdjustmentEntity.toDomain(): SegmentTimeAdjustment {
    return SegmentTimeAdjustment(
        id = id,
        routineId = routineId,
        segmentType = RouteSegmentType.valueOf(segmentType),
        routeName = routeName,
        startName = startName,
        endName = endName,
        averageDelayMinutes = averageDelayMinutes,
        sampleCount = sampleCount,
        confidence = confidence,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
