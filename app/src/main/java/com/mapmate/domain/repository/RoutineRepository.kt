package com.mapmate.domain.repository

import com.mapmate.domain.model.Routine
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    suspend fun saveRoutine(routine: Routine): Long

    suspend fun deleteRoutine(id: Long)

    suspend fun updatePersonalBufferMinutes(expectedRoutine: Routine, minutes: Int): Boolean

    fun observeRoutines(): Flow<List<Routine>>
}
