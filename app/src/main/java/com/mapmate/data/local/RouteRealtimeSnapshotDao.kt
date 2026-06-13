package com.mapmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RouteRealtimeSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: RouteRealtimeSnapshotEntity)

    @Query(
        """
        SELECT * FROM route_realtime_snapshots
        WHERE cacheKey = :cacheKey
            AND expiresAtEpochMillis >= :nowEpochMillis
        LIMIT 1
        """,
    )
    suspend fun findFreshSnapshot(
        cacheKey: String,
        nowEpochMillis: Long,
    ): RouteRealtimeSnapshotEntity?

    @Query("DELETE FROM route_realtime_snapshots WHERE expiresAtEpochMillis < :nowEpochMillis")
    suspend fun deleteExpiredSnapshots(nowEpochMillis: Long)
}
