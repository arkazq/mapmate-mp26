package com.mapmate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_segments",
    indices = [
        Index(value = ["commuteRecordId"]),
        Index(value = ["routineId"]),
        Index(value = ["routineId", "segmentType", "routeName", "startName", "endName"]),
    ],
)
data class RouteSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val commuteRecordId: Long,
    val routineId: Long?,
    val segmentIndex: Int,
    val segmentType: String,
    val trafficType: Int?,
    val routeName: String?,
    val startName: String?,
    val endName: String?,
    val plannedDurationMinutes: Int,
    val actualStartedAtEpochMillis: Long?,
    val actualEndedAtEpochMillis: Long?,
    val actualDurationMinutes: Int?,
    val isUserEdited: Boolean,
    val status: String,
)
