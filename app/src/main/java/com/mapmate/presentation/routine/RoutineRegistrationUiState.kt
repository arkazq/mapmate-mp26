package com.mapmate.presentation.routine

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode

data class RoutineRegistrationUiState(
    val savedRoutines: List<Routine> = emptyList(),
    val routineName: String = "",
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
    val selectedTransportMode: TransportMode = TransportMode.TRANSIT,
    val personalBufferMinutes: String = "6",
    val safetyMarginMinutes: String = "5",
    val routeEstimate: RouteEstimate? = null,
    val recommendedDepartureTimeText: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isCalculating: Boolean = false,
    val isSaving: Boolean = false,
    val isSaveEnabled: Boolean = false,
) {
    val hasCalculationResult: Boolean
        get() = routeEstimate != null && recommendedDepartureTimeText.isNotBlank()
}
