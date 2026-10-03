package com.mapmate.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureEvent
import com.mapmate.domain.alarm.DepartureScheduleState
import com.mapmate.domain.alarm.DepartureScheduleStore
import com.mapmate.domain.alarm.eventOrNull
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.departureScheduleDataStore by preferencesDataStore(name = "departure_schedule")

class DataStoreDepartureScheduleStore(
    context: Context,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : DepartureScheduleStore {
    private val dataStore = context.applicationContext.departureScheduleDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): DepartureScheduleState = decode(dataStore.data.first()[STATE_KEY])

    override suspend fun setSchedule(schedule: DepartureAlarmSchedule?, routineFingerprint: String?) {
        dataStore.edit { preferences ->
            val state = decode(preferences[STATE_KEY])
            preferences[STATE_KEY] = encode(state.copy(
                schedule = schedule,
                routineFingerprint = routineFingerprint,
            ))
        }
    }

    override suspend fun claimNotification(schedule: DepartureAlarmSchedule): Boolean {
        val event = schedule.eventOrNull() ?: return false
        var claimed = false
        dataStore.edit { preferences ->
            val state = decode(preferences[STATE_KEY])
            if (state.schedule == schedule && event !in state.notifiedEvents) {
                preferences[STATE_KEY] = encode(state.copy(
                    notifiedEvents = (state.notifiedEvents + event).filter {
                        it.targetArrivalAtEpochMillis >= nowEpochMillis() - 14 * 24 * 60 * 60_000L
                    },
                ))
                claimed = true
            }
        }
        return claimed
    }

    private fun decode(value: String?): DepartureScheduleState {
        if (value == null) return DepartureScheduleState()
        // Invalid/legacy metadata cannot authorize a notification; a new sync replaces it.
        return runCatching {
            val stored = json.decodeFromString<StoredState>(value)
            DepartureScheduleState(
                schedule = stored.schedule?.toDomain(),
                routineFingerprint = stored.routineFingerprint,
                notifiedEvents = stored.notifiedEvents.map {
                    DepartureEvent(it.routineId, it.targetArrivalAtEpochMillis)
                },
            )
        }.getOrDefault(DepartureScheduleState())
    }

    override suspend fun releaseNotificationClaim(schedule: DepartureAlarmSchedule) {
        val event = schedule.eventOrNull() ?: return
        dataStore.edit { preferences ->
            val state = decode(preferences[STATE_KEY])
            preferences[STATE_KEY] = encode(state.copy(notifiedEvents = state.notifiedEvents - event))
        }
    }

    private fun encode(state: DepartureScheduleState): String = json.encodeToString(StoredState(
        schedule = state.schedule?.let { schedule ->
            StoredSchedule(
                routineId = schedule.routineId,
                routineName = schedule.routineName,
                destinationName = schedule.destinationName,
                targetArrivalTime = schedule.targetArrivalTime.toString(),
                recommendedDepartureTime = schedule.recommendedDepartureTime.toString(),
                routeDurationMinutes = schedule.routeDurationMinutes,
                triggerAtEpochMillis = schedule.triggerAtEpochMillis,
                targetArrivalAtEpochMillis = schedule.targetArrivalAtEpochMillis,
            )
        },
        routineFingerprint = state.routineFingerprint,
        notifiedEvents = state.notifiedEvents.map {
            StoredEvent(it.routineId, it.targetArrivalAtEpochMillis)
        },
    ))

    @Serializable
    private data class StoredState(
        val schedule: StoredSchedule? = null,
        val routineFingerprint: String? = null,
        val notifiedEvents: List<StoredEvent> = emptyList(),
    )

    @Serializable
    private data class StoredEvent(val routineId: Long, val targetArrivalAtEpochMillis: Long)

    @Serializable
    private data class StoredSchedule(
        val routineId: Long,
        val routineName: String,
        val destinationName: String,
        val targetArrivalTime: String,
        val recommendedDepartureTime: String,
        val routeDurationMinutes: Int,
        val triggerAtEpochMillis: Long,
        val targetArrivalAtEpochMillis: Long? = null,
    ) {
        fun toDomain() = DepartureAlarmSchedule(
            routineId, routineName, destinationName,
            LocalTime.parse(targetArrivalTime), LocalTime.parse(recommendedDepartureTime),
            routeDurationMinutes, triggerAtEpochMillis, targetArrivalAtEpochMillis,
        )
    }

    private companion object {
        val STATE_KEY = stringPreferencesKey("schedule_state_v1")
    }
}
