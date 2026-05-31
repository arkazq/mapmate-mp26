package com.mapmate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val destinationName: String,
    val destinationAddress: String,
    val destinationLatitude: Double?,
    val destinationLongitude: Double?,
    val targetArrivalTime: String,
    val repeatDays: String,
    val transportMode: String,
    val personalBufferMinutes: Int,
    val safetyMarginMinutes: Int,
    val createdAtEpochMillis: Long,
)
