package com.mapmate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_realtime_snapshots",
    indices = [Index(value = ["expiresAtEpochMillis"])],
)
data class RouteRealtimeSnapshotEntity(
    @PrimaryKey
    val cacheKey: String,
    val baseRouteDurationMinutes: Int,
    val adjustedRouteDurationMinutes: Int,
    val realtimeDelayMinutes: Int,
    val providerName: String,
    val summary: String,
    val reason: String,
    val capturedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
)
