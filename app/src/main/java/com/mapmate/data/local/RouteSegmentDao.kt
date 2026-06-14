package com.mapmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RouteSegmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<RouteSegmentEntity>): List<Long>

    @Query(
        """
        SELECT * FROM route_segments
        WHERE commuteRecordId = :commuteRecordId
        ORDER BY segmentIndex ASC, id ASC
        """,
    )
    suspend fun getSegmentsByCommuteRecordId(commuteRecordId: Long): List<RouteSegmentEntity>

    @Query(
        """
        SELECT * FROM route_segments
        WHERE routineId = :routineId
        AND actualDurationMinutes IS NOT NULL
        AND status = 'COMPLETED'
        ORDER BY COALESCE(actualEndedAtEpochMillis, 0) DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentCompletedSegments(
        routineId: Long,
        limit: Int,
    ): List<RouteSegmentEntity>

    @Query(
        """
        UPDATE route_segments
        SET actualStartedAtEpochMillis = :actualStartedAtEpochMillis,
            actualEndedAtEpochMillis = :actualEndedAtEpochMillis,
            actualDurationMinutes = :actualDurationMinutes,
            isUserEdited = :isUserEdited,
            status = :status
        WHERE id = :segmentId
        """,
    )
    suspend fun updateSegmentTiming(
        segmentId: Long,
        actualStartedAtEpochMillis: Long?,
        actualEndedAtEpochMillis: Long?,
        actualDurationMinutes: Int?,
        isUserEdited: Boolean,
        status: String,
    )
}
