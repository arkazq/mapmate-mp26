package com.mapmate.presentation.routine

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.TransportMode
import kotlinx.serialization.Serializable

@Serializable
internal data class RoutineRegistrationDraft(
    val editingRoutineId: Long?,
    val routineName: String,
    val originQuery: String,
    val origin: DraftPlace?,
    val destinationQuery: String,
    val destination: DraftPlace?,
    val arrivalTime: String,
    val repeatDays: List<String>,
    val transportMode: String,
    val personalBufferMinutes: String,
    val safetyMarginMinutes: String,
    val hasUnsavedChanges: Boolean,
) {
    fun toUiState() = RoutineRegistrationUiState(
        editingRoutineId = editingRoutineId,
        routineName = routineName.take(30), originQuery = originQuery.take(80),
        selectedOrigin = origin?.toDomain(), destinationQuery = destinationQuery.take(80),
        selectedDestination = destination?.toDomain(), targetArrivalTimeText = arrivalTime,
        selectedRepeatDays = repeatDays.mapNotNull { name -> RepeatDay.entries.firstOrNull { it.name == name } }.toSet(),
        selectedTransportMode = TransportMode.entries.firstOrNull { it.name == transportMode } ?: TransportMode.TRANSIT,
        personalBufferMinutes = personalBufferMinutes, safetyMarginMinutes = safetyMarginMinutes,
        hasUnsavedChanges = hasUnsavedChanges,
    )
}

@Serializable
internal data class DraftPlace(val name: String, val address: String, val latitude: Double?, val longitude: Double?) {
    fun toDomain() = Destination(name, address, latitude, longitude)
}

internal fun RoutineRegistrationUiState.toDraft() = RoutineRegistrationDraft(
    editingRoutineId, routineName, originQuery, selectedOrigin?.toDraftPlace(),
    destinationQuery, selectedDestination?.toDraftPlace(), targetArrivalTimeText,
    selectedRepeatDays.map { it.name }.sorted(), selectedTransportMode.name,
    personalBufferMinutes, safetyMarginMinutes, hasUnsavedChanges,
)

private fun Destination.toDraftPlace() = DraftPlace(
    name, address, latitude?.takeIf { it.isFinite() }, longitude?.takeIf { it.isFinite() },
)
