package com.mapmate.presentation.settings

import com.mapmate.domain.model.TransportMode

sealed interface SettingsEvent {
    data class PersonalBufferChanged(val minutesText: String) : SettingsEvent
    data class SafetyMarginChanged(val minutesText: String) : SettingsEvent
    data class NotificationsEnabledChanged(val enabled: Boolean) : SettingsEvent
    data class PredepartureStatusNotificationEnabledChanged(val enabled: Boolean) : SettingsEvent
    data object NotificationPermissionDenied : SettingsEvent
    data class DefaultTransportModeSelected(val transportMode: TransportMode) : SettingsEvent
    data object MessageCleared : SettingsEvent
}
