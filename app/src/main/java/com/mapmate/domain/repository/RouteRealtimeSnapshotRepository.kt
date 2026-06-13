package com.mapmate.domain.repository

import com.mapmate.domain.model.RouteRealtimeSnapshot

interface RouteRealtimeSnapshotRepository {
    suspend fun saveSnapshot(snapshot: RouteRealtimeSnapshot)

    suspend fun findFreshSnapshot(
        cacheKey: String,
        nowEpochMillis: Long,
    ): RouteRealtimeSnapshot?

    suspend fun deleteExpiredSnapshots(nowEpochMillis: Long)
}
