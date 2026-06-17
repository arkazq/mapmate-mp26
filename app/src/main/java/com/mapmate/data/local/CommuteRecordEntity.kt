package com.mapmate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "commute_records",
    indices = [Index(value = ["routineId"])],
)
data class CommuteRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long?,
    val routineName: String,
    val originName: String,
    val destinationName: String,
    val transportMode: String,
    val targetArrivalTime: String,
    val targetArrivalAtEpochMillis: Long?,
    val recommendedDepartureTime: String,
    val routeDurationMinutes: Int,
    val routeSummary: String,
    val startedAtEpochMillis: Long,
    val arrivedAtEpochMillis: Long,
    val arrivalDeltaMinutes: Int,
)
