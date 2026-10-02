package com.mapmate.domain.repository

import com.mapmate.domain.model.TrackingSession
import kotlinx.coroutines.flow.Flow

interface TrackingSessionStore {
    fun observeSessions(): Flow<List<TrackingSession>>
    suspend fun read(routineId: Long): TrackingSession?
    suspend fun save(session: TrackingSession)
    suspend fun clear(routineId: Long)
}
