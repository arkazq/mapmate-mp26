package com.mapmate.data.repository

import com.mapmate.data.local.RoutineDao
import com.mapmate.data.local.toDomain
import com.mapmate.data.local.toEntity
import com.mapmate.domain.model.Routine
import com.mapmate.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomRoutineRepository(
    private val routineDao: RoutineDao,
) : RoutineRepository {
    override suspend fun saveRoutine(routine: Routine): Long {
        return routineDao.insertRoutine(routine.toEntity())
    }

    override suspend fun deleteRoutine(id: Long) {
        routineDao.deleteRoutineById(id)
    }

    override fun observeRoutines(): Flow<List<Routine>> {
        return routineDao.observeRoutines()
            .map { entities -> entities.map { it.toDomain() } }
    }
}
