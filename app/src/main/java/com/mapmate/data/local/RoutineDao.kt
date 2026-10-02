package com.mapmate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mapmate.domain.model.Routine
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getRoutineById(id: Long): RoutineEntity?

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutineById(id: Long)

    @Query("UPDATE routines SET personalBufferMinutes = :minutes WHERE id = :id AND personalBufferMinutes = :expectedCurrentMinutes")
    suspend fun updatePersonalBufferMinutes(id: Long, minutes: Int, expectedCurrentMinutes: Int): Int

    @Transaction
    suspend fun updatePersonalBufferIfUnchanged(expectedRoutine: Routine, minutes: Int): Boolean {
        val id = expectedRoutine.id ?: return false
        if (getRoutineById(id)?.toDomain() != expectedRoutine) return false
        return updatePersonalBufferMinutes(id, minutes, expectedRoutine.personalBufferMinutes) > 0
    }

    @Query("SELECT * FROM routines ORDER BY id DESC")
    fun observeRoutines(): Flow<List<RoutineEntity>>
}
