package com.mapmate.testing

import com.mapmate.domain.model.TrackingSession
import com.mapmate.domain.repository.TrackingSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

class TestTrackingSessionStore : TrackingSessionStore {
    val sessions = mutableMapOf<Long, TrackingSession>()
    private val changes = MutableStateFlow(0L)
    override fun observeSessions() = flow {
        changes.collect { emit(sessions.values.toList()) }
    }
    var failWrites = false
    override suspend fun read(routineId: Long) = sessions[routineId]
    override suspend fun save(session: TrackingSession) {
        check(!failWrites) { "write failed" }
        require(session.isValid())
        sessions[session.routineId] = session
        changes.value += 1
    }
    override suspend fun clear(routineId: Long) { sessions.remove(routineId); changes.value += 1 }
}
