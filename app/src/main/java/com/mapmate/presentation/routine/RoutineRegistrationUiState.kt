package com.mapmate.presentation.routine

import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode

data class RoutineRegistrationUiState(
    val editingRoutineId: Long? = null,
    val routineName: String = "",
    val originQuery: String = "",
    val selectedOrigin: Destination? = null,
    val originCandidates: List<Destination> = emptyList(),
    val destinationQuery: String = "",
    val selectedDestination: Destination? = null,
    val destinationCandidates: List<Destination> = emptyList(),
    val targetArrivalTimeText: String = "09:00",
    val selectedRepeatDays: Set<RepeatDay> = setOf(
        RepeatDay.MONDAY,
        RepeatDay.TUESDAY,
        RepeatDay.WEDNESDAY,
        RepeatDay.THURSDAY,
        RepeatDay.FRIDAY,
    ),
    val selectedTransportMode: TransportMode = AppSettings.DEFAULT_TRANSPORT_MODE,
    val personalBufferMinutes: String = AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES.toString(),
    val safetyMarginMinutes: String = AppSettings.DEFAULT_SAFETY_MARGIN_MINUTES.toString(),
    val routeEstimate: RouteEstimate? = null,
    val recommendedDepartureTimeText: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isGettingCurrentLocation: Boolean = false,
    val isCalculating: Boolean = false,
    val isSaving: Boolean = false,
    val isSaveEnabled: Boolean = false,
) {
    val isEditing: Boolean
        get() = editingRoutineId != null

    val hasCalculationResult: Boolean
        get() = routeEstimate != null && recommendedDepartureTimeText.isNotBlank()
}
