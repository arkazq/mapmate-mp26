package com.mapmate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "segment_time_adjustments",
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["routineId", "segmentType", "routeName", "startName", "endName"], unique = true),
    ],
)
data class SegmentTimeAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long,
    val segmentType: String,
    val routeName: String?,
    val startName: String?,
    val endName: String?,
    val averageDelayMinutes: Int,
    val sampleCount: Int,
    val confidence: Double,
    val updatedAtEpochMillis: Long,
)
