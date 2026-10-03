package com.mapmate.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.mapmate.domain.model.CommuteRecord

data class CommuteRecordWithSegments(
    @Embedded val record: CommuteRecordEntity,
    @Relation(parentColumn = "id", entityColumn = "commuteRecordId")
    val segments: List<RouteSegmentEntity>,
) {
    fun toDomain(): CommuteRecord = record.toDomain().copy(
        routeSegments = segments.sortedBy { it.segmentIndex }.map { it.toDomain() },
    )
}
