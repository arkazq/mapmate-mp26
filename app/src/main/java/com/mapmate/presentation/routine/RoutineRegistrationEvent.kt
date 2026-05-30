package com.mapmate.presentation.routine

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.TransportMode

sealed interface RoutineRegistrationEvent {
    data class RoutineNameChanged(val name: String) : RoutineRegistrationEvent
    data class DestinationQueryChanged(val query: String) : RoutineRegistrationEvent
    data class DestinationSelected(val destination: Destination) : RoutineRegistrationEvent
    data class ArrivalTimeChanged(val timeText: String) : RoutineRegistrationEvent
    data class RepeatDayToggled(val repeatDay: RepeatDay) : RoutineRegistrationEvent
    data class TransportModeSelected(val transportMode: TransportMode) : RoutineRegistrationEvent
    data class PersonalBufferChanged(val minutesText: String) : RoutineRegistrationEvent
    data class SafetyMarginChanged(val minutesText: String) : RoutineRegistrationEvent
    data object CalculateClicked : RoutineRegistrationEvent
    data object SaveClicked : RoutineRegistrationEvent
    data object MessageCleared : RoutineRegistrationEvent
}
