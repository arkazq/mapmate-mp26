package com.mapmate.data.repository

import com.mapmate.data.local.RouteRealtimeSnapshotDao
import com.mapmate.data.local.toDomain
import com.mapmate.data.local.toEntity
import com.mapmate.domain.model.RouteRealtimeSnapshot
import com.mapmate.domain.repository.RouteRealtimeSnapshotRepository

class RoomRouteRealtimeSnapshotRepository(
    private val routeRealtimeSnapshotDao: RouteRealtimeSnapshotDao,
) : RouteRealtimeSnapshotRepository {
    override suspend fun saveSnapshot(snapshot: RouteRealtimeSnapshot) {
        routeRealtimeSnapshotDao.upsertSnapshot(snapshot.toEntity())
    }

    override suspend fun findFreshSnapshot(
        cacheKey: String,
        nowEpochMillis: Long,
    ): RouteRealtimeSnapshot? {
        return routeRealtimeSnapshotDao.findFreshSnapshot(
            cacheKey = cacheKey,
            nowEpochMillis = nowEpochMillis,
        )?.toDomain()
    }

    override suspend fun deleteExpiredSnapshots(nowEpochMillis: Long) {
        routeRealtimeSnapshotDao.deleteExpiredSnapshots(nowEpochMillis)
    }
}
