package com.mapmate.testing

import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

class TestRoutineRepository(initial: List<Routine> = emptyList()) : RoutineRepository {
    val routines = MutableStateFlow(initial)
    var saveCount = 0
    var saveGate: CompletableDeferred<Unit>? = null
    override suspend fun saveRoutine(routine: Routine): Long {
        saveCount++
        saveGate?.await()
        val id = routine.id ?: (routines.value.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
        routines.value = routines.value.filterNot { it.id == id } + routine.copy(id = id)
        return id
    }
    override suspend fun deleteRoutine(id: Long) {
        routines.value = routines.value.filterNot { it.id == id }
    }
    override suspend fun updatePersonalBufferMinutes(expectedRoutine: Routine, minutes: Int): Boolean {
        if (expectedRoutine !in routines.value) return false
        routines.value = routines.value.map {
            if (it.id == expectedRoutine.id) expectedRoutine.copy(personalBufferMinutes = minutes) else it
        }
        return true
    }
    override fun observeRoutines() = routines
}

class TestCommuteRecordRepository(initial: List<CommuteRecord> = emptyList()) : CommuteRecordRepository {
    val records = MutableStateFlow(initial)
    var savedCount = 0
    val batches = mutableListOf<List<RouteSegment>>()
    override suspend fun saveRecord(record: CommuteRecord): Long {
        savedCount++
        val id = record.id ?: (records.value.maxOfOrNull { it.id ?: 0L } ?: 0L) + 1L
        records.value = records.value.filterNot { it.id == id } + record.copy(id = id)
        return id
    }
    override suspend fun getRecentRecords(limit: Int, routineId: Long?): List<CommuteRecord> =
        records.value.filter { routineId == null || it.routineId == routineId }
            .sortedByDescending { it.arrivedAtEpochMillis }.take(limit)
    override suspend fun getRecord(recordId: Long) = records.value.firstOrNull { it.id == recordId }
    override suspend fun updateRouteSegment(segment: RouteSegment) {
        updateRouteSegments(requireNotNull(segment.commuteRecordId), listOf(segment))
    }
    override suspend fun updateRouteSegments(recordId: Long, segments: List<RouteSegment>): CommuteRecord {
        batches += segments
        val current = requireNotNull(getRecord(recordId))
        val updated = current.copy(routeSegments = current.routeSegments.map { original ->
            segments.firstOrNull { it.id == original.id } ?: original
        })
        records.value = records.value.map { if (it.id == recordId) updated else it }
        return updated
    }
    override fun observeRecords() = records
}

class TestSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())
    override suspend fun updatePersonalBufferMinutes(minutes: Int) {
        settings.value = settings.value.copy(personalBufferMinutes = minutes)
    }
    override suspend fun updatePersonalBufferForRecentArrivalDeltas(recentArrivalDeltaMinutes: List<Int>) =
        settings.value.personalBufferMinutes
    override suspend fun updateSafetyMarginMinutes(minutes: Int) {
        settings.value = settings.value.copy(safetyMarginMinutes = minutes)
    }
    override suspend fun updateBufferDefaults(personalBufferMinutes: Int, safetyMarginMinutes: Int) {
        settings.value = settings.value.copy(personalBufferMinutes = personalBufferMinutes, safetyMarginMinutes = safetyMarginMinutes)
    }
    override suspend fun updateNotificationsEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(notificationsEnabled = enabled)
    }
    override suspend fun updatePredepartureStatusNotificationEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(predepartureStatusNotificationEnabled = enabled)
    }
    override suspend fun updateDefaultTransportMode(transportMode: TransportMode) {
        settings.value = settings.value.copy(defaultTransportMode = transportMode)
    }
}

fun sampleRoutine(id: Long = 1L, name: String = "Commute") = Routine(
    id = id,
    name = name,
    origin = Destination("Origin", "Origin address", 37.5, 127.0),
    destination = Destination("Destination", "Destination address", 37.6, 127.1),
    targetArrivalTime = LocalTime.of(9, 0),
    repeatDays = RepeatDay.entries.toSet(),
    transportMode = TransportMode.TRANSIT,
    personalBufferMinutes = 3,
    safetyMarginMinutes = 5,
)
