package com.mapmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SegmentTimeAdjustmentDao {
    @Query("DELETE FROM segment_time_adjustments WHERE routineId = :routineId")
    suspend fun deleteByRoutineId(routineId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustments(adjustments: List<SegmentTimeAdjustmentEntity>)

    @Query("SELECT * FROM segment_time_adjustments WHERE routineId = :routineId")
    suspend fun getAdjustmentsForRoutine(routineId: Long): List<SegmentTimeAdjustmentEntity>
}
