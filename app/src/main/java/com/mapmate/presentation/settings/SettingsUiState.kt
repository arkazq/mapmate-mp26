package com.mapmate.presentation.settings

import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.TransportMode

data class SettingsUiState(
    val personalBufferMinutes: String = AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES.toString(),
    val safetyMarginMinutes: String = AppSettings.DEFAULT_SAFETY_MARGIN_MINUTES.toString(),
    val notificationsEnabled: Boolean = AppSettings.DEFAULT_NOTIFICATIONS_ENABLED,
    val predepartureStatusNotificationEnabled: Boolean =
        AppSettings.DEFAULT_PREDEPARTURE_STATUS_NOTIFICATION_ENABLED,
    val defaultTransportMode: TransportMode = AppSettings.DEFAULT_TRANSPORT_MODE,
    val errorMessage: String? = null,
)
