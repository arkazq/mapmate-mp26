package com.mapmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RouteEstimateCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RouteEstimateCacheEntity)

    @Query(
        """
        SELECT * FROM route_estimate_cache
        WHERE cacheKey = :cacheKey AND expiresAtEpochMillis > :nowEpochMillis
        LIMIT 1
        """,
    )
    suspend fun findFresh(
        cacheKey: String,
        nowEpochMillis: Long,
    ): RouteEstimateCacheEntity?

    @Query("DELETE FROM route_estimate_cache WHERE expiresAtEpochMillis <= :nowEpochMillis")
    suspend fun deleteExpired(nowEpochMillis: Long)
}
