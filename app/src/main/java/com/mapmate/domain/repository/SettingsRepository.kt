package com.mapmate.domain.repository

import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.TransportMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun updatePersonalBufferMinutes(minutes: Int)

    suspend fun updatePersonalBufferForArrivalDelta(arrivalDeltaMinutes: Int): Int

    suspend fun updateSafetyMarginMinutes(minutes: Int)

    suspend fun updateNotificationsEnabled(enabled: Boolean)

    suspend fun updateDefaultTransportMode(transportMode: TransportMode)
}
