package com.mapmate.presentation

import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.domain.model.TrackingSession
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow

internal class NavigationTestData(
    initialRoutines: List<Routine> = emptyList(),
    private val estimate: RouteEstimate = RouteEstimate(20, "테스트 경로", "Test", "테스트 예상 시간"),
) {
    val trackingSessionStore = object : TrackingSessionStore {
        private val sessions = MutableStateFlow(emptyMap<Long, TrackingSession>())
        override fun observeSessions() = kotlinx.coroutines.flow.flow { sessions.collect { emit(it.values.toList()) } }
        override suspend fun read(routineId: Long) = sessions.value[routineId]
        override suspend fun save(session: TrackingSession) { sessions.value = sessions.value + (session.routineId to session) }
        override suspend fun clear(routineId: Long) { sessions.value = sessions.value - routineId }
    }
    val routines = MutableStateFlow(initialRoutines)
    val records = MutableStateFlow(emptyList<CommuteRecord>())
    val settings = MutableStateFlow(AppSettings(notificationsEnabled = false))
    val routineRepository = object : RoutineRepository {
        override fun observeRoutines() = routines
        override suspend fun saveRoutine(routine: Routine): Long {
            val id = routine.id ?: (routines.value.maxOfOrNull { it.id ?: 0 } ?: 0) + 1
            routines.value = routines.value.filterNot { it.id == id } + routine.copy(id = id)
            return id
        }
        override suspend fun deleteRoutine(id: Long) { routines.value = routines.value.filterNot { it.id == id } }
        override suspend fun updatePersonalBufferMinutes(expectedRoutine: Routine, minutes: Int): Boolean {
            if (expectedRoutine !in routines.value) return false
            routines.value = routines.value.map {
                if (it.id == expectedRoutine.id) expectedRoutine.copy(personalBufferMinutes = minutes) else it
            }
            return true
        }
    }
    val recordRepository = object : CommuteRecordRepository {
        override fun observeRecords() = records
        override suspend fun saveRecord(record: CommuteRecord): Long {
            val id = (records.value.maxOfOrNull { it.id ?: 0 } ?: 0) + 1
            records.value = records.value + record.copy(id = id, routeSegments = record.routeSegments.mapIndexed { index, segment ->
                segment.copy(id = id * 100 + index + 1, commuteRecordId = id)
            })
            return id
        }
        override suspend fun getRecord(recordId: Long) = records.value.firstOrNull { it.id == recordId }
        override suspend fun getRecentRecords(limit: Int, routineId: Long?) = records.value
            .filter { routineId == null || it.routineId == routineId }.takeLast(limit)
        override suspend fun updateRouteSegment(segment: RouteSegment) {
            updateRouteSegments(requireNotNull(segment.commuteRecordId), listOf(segment))
        }
        override suspend fun updateRouteSegments(recordId: Long, segments: List<RouteSegment>): CommuteRecord {
            val record = requireNotNull(getRecord(recordId))
            val updated = record.copy(routeSegments = record.routeSegments.map { original ->
                segments.firstOrNull { it.id == original.id } ?: original
            })
            records.value = records.value.map { if (it.id == recordId) updated else it }
            return updated
        }
    }
    val settingsRepository = object : SettingsRepository {
        override val settings = this@NavigationTestData.settings
        override suspend fun updatePersonalBufferMinutes(minutes: Int) { settings.value = settings.value.copy(personalBufferMinutes = minutes) }
        override suspend fun updatePersonalBufferForRecentArrivalDeltas(recentArrivalDeltaMinutes: List<Int>) = settings.value.personalBufferMinutes
        override suspend fun updateSafetyMarginMinutes(minutes: Int) { settings.value = settings.value.copy(safetyMarginMinutes = minutes) }
        override suspend fun updateBufferDefaults(personalBufferMinutes: Int, safetyMarginMinutes: Int) {
            settings.value = settings.value.copy(personalBufferMinutes = personalBufferMinutes, safetyMarginMinutes = safetyMarginMinutes)
        }
        override suspend fun updateNotificationsEnabled(enabled: Boolean) { settings.value = settings.value.copy(notificationsEnabled = enabled) }
        override suspend fun updatePredepartureStatusNotificationEnabled(enabled: Boolean) { settings.value = settings.value.copy(predepartureStatusNotificationEnabled = enabled) }
        override suspend fun updateDefaultTransportMode(transportMode: TransportMode) { settings.value = settings.value.copy(defaultTransportMode = transportMode) }
    }
    val placeSearchProvider = object : PlaceSearchProvider {
        override suspend fun search(query: String) = emptyList<Destination>()
    }
    val routeProvider = object : RouteEstimateProvider {
        override suspend fun getRouteEstimate(origin: Destination, destination: Destination, transportMode: TransportMode,
            routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?) =
            estimate
    }
    val locationProvider = object : CurrentLocationProvider {
        override suspend fun getCurrentLocation() = Destination("서울역", "서울 중구", 37.55, 126.97)
    }
}

internal fun testRoutine(id: Long = 1, name: String = "등교") = Routine(
    id = id,
    name = name,
    origin = Destination("서울역", "서울 중구", 37.55, 126.97),
    destination = Destination("숭실대학교", "서울 동작구", 37.50, 126.96),
    targetArrivalTime = LocalTime.now().plusHours(1).withSecond(0).withNano(0),
    repeatDays = RepeatDay.entries.toSet(),
    transportMode = TransportMode.TRANSIT,
    personalBufferMinutes = 3,
    safetyMarginMinutes = 5,
)
