package com.mapmate.domain.provider

import com.mapmate.domain.model.AlarmAccessState

interface AlarmAccessProvider {
    fun read(): AlarmAccessState
    fun openSettings(destination: AlarmSettingsDestination): Boolean
}

enum class AlarmSettingsDestination { NOTIFICATIONS, EXACT_ALARM }
