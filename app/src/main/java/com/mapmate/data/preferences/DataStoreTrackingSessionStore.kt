package com.mapmate.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mapmate.domain.model.TrackingSession
import com.mapmate.domain.repository.TrackingSessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.trackingSessionDataStore by preferencesDataStore(name = "tracking_sessions")

class DataStoreTrackingSessionStore(context: Context) : TrackingSessionStore {
    private val dataStore = context.applicationContext.trackingSessionDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeSessions() = dataStore.data.map { preferences ->
        preferences.asMap().filterKeys { it.name.startsWith("session_") }.map { (key, value) ->
            val routineId = requireNotNull(key.name.removePrefix("session_").toLongOrNull())
            decode(value as String, routineId)
        }
    }

    override suspend fun read(routineId: Long): TrackingSession? {
        val value = dataStore.data.first()[key(routineId)] ?: return null
        return decode(value, routineId)
    }

    private fun decode(value: String, routineId: Long): TrackingSession {
        val session = runCatching { json.decodeFromString<TrackingSession>(value) }.getOrNull()
        // Corrupted timing must not silently become a new measurement.
        check(session != null && session.isValid() && session.routineId == routineId) { "Invalid saved tracking session." }
        return session
    }

    override suspend fun save(session: TrackingSession) {
        require(session.isValid()) { "Invalid tracking session." }
        dataStore.edit { it[key(session.routineId)] = json.encodeToString(session) }
    }

    override suspend fun clear(routineId: Long) {
        dataStore.edit { it.remove(key(routineId)) }
    }

    private fun key(routineId: Long) = stringPreferencesKey("session_$routineId")
}
