package com.mapmate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_estimate_cache",
    indices = [Index(value = ["expiresAtEpochMillis"])],
)
data class RouteEstimateCacheEntity(
    @PrimaryKey val cacheKey: String,
    val cacheNamespace: String,
    val transportMode: String,
    val originName: String,
    val originAddress: String,
    val originLatitude: Double?,
    val originLongitude: Double?,
    val destinationName: String,
    val destinationAddress: String,
    val destinationLatitude: Double?,
    val destinationLongitude: Double?,
    val estimatedMinutes: Int,
    val summary: String,
    val providerName: String,
    val reason: String,
    val capturedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)
