package com.mapmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CommuteRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CommuteRecordEntity): Long

    @Query(
        """
        SELECT * FROM commute_records
        WHERE (:routineId IS NULL OR routineId = :routineId)
        ORDER BY arrivedAtEpochMillis DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentRecords(
        limit: Int,
        routineId: Long?,
    ): List<CommuteRecordEntity>

    @Query("SELECT * FROM commute_records WHERE id = :recordId LIMIT 1")
    suspend fun getRecordById(recordId: Long): CommuteRecordEntity?

    @Query("SELECT id FROM commute_records WHERE routineId = :routineId AND targetArrivalAtEpochMillis = :targetAt LIMIT 1")
    suspend fun getRecordIdByArrivalEvent(routineId: Long, targetAt: Long): Long?

    @Transaction
    @Query("SELECT * FROM commute_records WHERE id = :recordId LIMIT 1")
    suspend fun getRecordWithSegments(recordId: Long): CommuteRecordWithSegments?

    @Transaction
    @Query("SELECT * FROM commute_records WHERE (:routineId IS NULL OR routineId = :routineId) ORDER BY arrivedAtEpochMillis DESC LIMIT :limit")
    suspend fun getRecentRecordsWithSegments(limit: Int, routineId: Long?): List<CommuteRecordWithSegments>

    @Transaction
    @Query("SELECT * FROM commute_records ORDER BY arrivedAtEpochMillis DESC")
    fun observeRecordsWithSegments(): Flow<List<CommuteRecordWithSegments>>

    @Query(
        """
        UPDATE commute_records
        SET startedAtEpochMillis = :startedAtEpochMillis,
            arrivedAtEpochMillis = :arrivedAtEpochMillis,
            arrivalDeltaMinutes = :arrivalDeltaMinutes
        WHERE id = :recordId
        """,
    )
    suspend fun updateActualTiming(
        recordId: Long,
        startedAtEpochMillis: Long,
        arrivedAtEpochMillis: Long,
        arrivalDeltaMinutes: Int,
    )

    @Query("SELECT * FROM commute_records ORDER BY arrivedAtEpochMillis DESC")
    fun observeRecords(): Flow<List<CommuteRecordEntity>>
}
