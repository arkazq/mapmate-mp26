package com.mapmate.domain.repository

import com.mapmate.domain.model.Routine
import kotlinx.coroutines.flow.Flow

interface RoutineRepository {
    suspend fun saveRoutine(routine: Routine): Long

    suspend fun deleteRoutine(id: Long)

    fun observeRoutines(): Flow<List<Routine>>
}
