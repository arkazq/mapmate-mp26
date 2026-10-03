package com.mapmate.domain.model

data class AlarmAccessState(
    val runtimeNotificationGranted: Boolean = true,
    val appNotificationsEnabled: Boolean = true,
    val departureChannelEnabled: Boolean = true,
    val statusChannelEnabled: Boolean = true,
    val exactAlarmAllowed: Boolean = true,
) {
    val canPostDeparture: Boolean
        get() = runtimeNotificationGranted && appNotificationsEnabled && departureChannelEnabled
    val canPostStatus: Boolean
        get() = runtimeNotificationGranted && appNotificationsEnabled && statusChannelEnabled
}
