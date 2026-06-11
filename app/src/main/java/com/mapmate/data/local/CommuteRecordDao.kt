package com.mapmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommuteRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: CommuteRecordEntity): Long

    @Query("SELECT * FROM commute_records ORDER BY arrivedAtEpochMillis DESC")
    fun observeRecords(): Flow<List<CommuteRecordEntity>>
}
