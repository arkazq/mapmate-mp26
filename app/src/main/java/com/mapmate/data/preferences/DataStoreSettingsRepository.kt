package com.mapmate.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mapmate.domain.calculator.PersonalBufferOptimizer
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.mapMateSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mapmate_settings",
)

class DataStoreSettingsRepository(
    context: Context,
    private val personalBufferOptimizer: PersonalBufferOptimizer = PersonalBufferOptimizer(),
) : SettingsRepository {
    private val dataStore = context.applicationContext.mapMateSettingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                personalBufferMinutes = AppSettings.bufferMinutesOrDefault(
                    minutes = preferences[PERSONAL_BUFFER_MINUTES],
                    defaultMinutes = AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES,
                ),
                safetyMarginMinutes = AppSettings.bufferMinutesOrDefault(
                    minutes = preferences[SAFETY_MARGIN_MINUTES],
                    defaultMinutes = AppSettings.DEFAULT_SAFETY_MARGIN_MINUTES,
                ),
                notificationsEnabled = preferences[NOTIFICATIONS_ENABLED]
                    ?: AppSettings.DEFAULT_NOTIFICATIONS_ENABLED,
                defaultTransportMode = AppSettings.transportModeOrDefault(
                    storedName = preferences[DEFAULT_TRANSPORT_MODE],
                ),
            )
        }

    override suspend fun updatePersonalBufferMinutes(minutes: Int) {
        requireValidBufferMinutes(minutes)
        dataStore.edit { preferences ->
            preferences[PERSONAL_BUFFER_MINUTES] = minutes
        }
    }

    override suspend fun updatePersonalBufferForRecentArrivalDeltas(recentArrivalDeltaMinutes: List<Int>): Int {
        var updatedPersonalBufferMinutes = AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES
        dataStore.edit { preferences ->
            val currentMinutes = AppSettings.bufferMinutesOrDefault(
                minutes = preferences[PERSONAL_BUFFER_MINUTES],
                defaultMinutes = AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES,
            )
            updatedPersonalBufferMinutes = personalBufferOptimizer.optimize(
                currentPersonalBufferMinutes = currentMinutes,
                recentArrivalDeltaMinutes = recentArrivalDeltaMinutes,
            )
            preferences[PERSONAL_BUFFER_MINUTES] = updatedPersonalBufferMinutes
        }
        return updatedPersonalBufferMinutes
    }

    override suspend fun updateSafetyMarginMinutes(minutes: Int) {
        requireValidBufferMinutes(minutes)
        dataStore.edit { preferences ->
            preferences[SAFETY_MARGIN_MINUTES] = minutes
        }
    }

    override suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    override suspend fun updateDefaultTransportMode(transportMode: TransportMode) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_TRANSPORT_MODE] = transportMode.name
        }
    }

    private fun requireValidBufferMinutes(minutes: Int) {
        require(AppSettings.isValidBufferMinutes(minutes)) {
            "Buffer minutes must be between ${AppSettings.MIN_BUFFER_MINUTES} and ${AppSettings.MAX_BUFFER_MINUTES}."
        }
    }

    private companion object {
        val PERSONAL_BUFFER_MINUTES = intPreferencesKey("personal_buffer_minutes")
        val SAFETY_MARGIN_MINUTES = intPreferencesKey("safety_margin_minutes")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val DEFAULT_TRANSPORT_MODE = stringPreferencesKey("default_transport_mode")
    }
}
