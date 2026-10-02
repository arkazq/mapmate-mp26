package com.mapmate.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true, isSettingsAvailable = false))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var persistedSettings = AppSettings()
    private var observationJob: Job? = null

    init {
        observeSettings()
    }

    fun onEvent(event: SettingsEvent) {
        if (!_uiState.value.isSettingsAvailable) return
        when (event) {
            is SettingsEvent.PersonalBufferChanged -> onPersonalBufferChanged(event.minutesText)
            is SettingsEvent.SafetyMarginChanged -> onSafetyMarginChanged(event.minutesText)
            is SettingsEvent.NotificationsEnabledChanged -> onNotificationsEnabledChanged(event.enabled)
            is SettingsEvent.PredepartureStatusNotificationEnabledChanged ->
                onPredepartureStatusNotificationEnabledChanged(event.enabled)
            SettingsEvent.NotificationPermissionDenied -> onNotificationPermissionDenied()
            is SettingsEvent.DefaultTransportModeSelected -> onDefaultTransportModeSelected(event.transportMode)
            SettingsEvent.MessageCleared -> clearMessage()
            SettingsEvent.SaveBufferDefaultsClicked -> saveBufferDefaults()
        }
    }

    private fun onPersonalBufferChanged(minutesText: String) {
        if (_uiState.value.isSavingBufferDefaults) return
        val filteredText = minutesText.filterAsciiDigits().take(2)
        _uiState.update {
            it.copy(
                personalBufferMinutes = filteredText,
                errorMessage = null,
                hasUnsavedBufferDefaults = true,
            )
        }
    }

    private fun onSafetyMarginChanged(minutesText: String) {
        if (_uiState.value.isSavingBufferDefaults) return
        val filteredText = minutesText.filterAsciiDigits().take(2)
        _uiState.update {
            it.copy(
                safetyMarginMinutes = filteredText,
                errorMessage = null,
                hasUnsavedBufferDefaults = true,
            )
        }
    }

    private fun onNotificationsEnabledChanged(enabled: Boolean) {
        _uiState.update {
            it.copy(
                notificationsEnabled = enabled,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                settingsRepository.updateNotificationsEnabled(enabled)
            }
            if (result.isFailure) {
                showPersistenceError()
            }
        }
    }

    private fun onPredepartureStatusNotificationEnabledChanged(enabled: Boolean) {
        _uiState.update {
            it.copy(
                predepartureStatusNotificationEnabled = enabled,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                settingsRepository.updatePredepartureStatusNotificationEnabled(enabled)
            }
            if (result.isFailure) {
                showPersistenceError()
            }
        }
    }

    private fun onNotificationPermissionDenied() {
        _uiState.update {
            it.copy(
                errorMessage = "Android 알림 권한이 꺼져 있습니다. 시스템 알림 설정을 확인해 주세요.",
            )
        }
    }

    private fun onDefaultTransportModeSelected(transportMode: TransportMode) {
        _uiState.update {
            it.copy(
                defaultTransportMode = transportMode,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                settingsRepository.updateDefaultTransportMode(transportMode)
            }
            if (result.isFailure) {
                showPersistenceError()
            }
        }
    }

    private fun observeSettings() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            settingsRepository.settings.catch {
                _uiState.update { it.copy(isLoading = false, isSettingsAvailable = false,
                    errorMessage = "설정을 불러오지 못했습니다. 다시 시도해 주세요.") }
            }.collect { settings ->
                persistedSettings = settings
                _uiState.update {
                    it.copy(
                        personalBufferMinutes = if (it.hasUnsavedBufferDefaults) it.personalBufferMinutes else settings.personalBufferMinutes.toString(),
                        safetyMarginMinutes = if (it.hasUnsavedBufferDefaults) it.safetyMarginMinutes else settings.safetyMarginMinutes.toString(),
                        notificationsEnabled = settings.notificationsEnabled,
                        predepartureStatusNotificationEnabled =
                            settings.predepartureStatusNotificationEnabled,
                        defaultTransportMode = settings.defaultTransportMode,
                        isLoading = false,
                        isSettingsAvailable = true,
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeSettings()
    }

    private fun saveBufferDefaults() {
        val state = _uiState.value
        if (state.isSavingBufferDefaults) return
        val personal = state.personalBufferMinutes.toValidBufferMinutesOrNull()
        val safety = state.safetyMarginMinutes.toValidBufferMinutesOrNull()
        if (personal == null || safety == null) {
            _uiState.update { it.copy(errorMessage = "보정 시간은 각각 0분부터 60분 사이로 입력해 주세요.") }
            return
        }
        _uiState.update { it.copy(isSavingBufferDefaults = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                settingsRepository.updateBufferDefaults(personal, safety)
            }
            _uiState.update {
                it.copy(isSavingBufferDefaults = false, hasUnsavedBufferDefaults = result.isFailure,
                    errorMessage = if (result.isFailure) "설정 저장에 실패했습니다. 다시 시도해 주세요." else null)
            }
        }
    }

    private fun showPersistenceError() {
        _uiState.update {
            it.copy(
                notificationsEnabled = persistedSettings.notificationsEnabled,
                predepartureStatusNotificationEnabled = persistedSettings.predepartureStatusNotificationEnabled,
                defaultTransportMode = persistedSettings.defaultTransportMode,
                errorMessage = "설정 저장에 실패했습니다. 다시 시도해 주세요.",
            )
        }
    }

    private fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun String.toValidBufferMinutesOrNull(): Int? {
        return toIntOrNull()?.takeIf(AppSettings::isValidBufferMinutes)
    }

    private fun String.filterAsciiDigits(): String {
        return filter { it in '0'..'9' }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        return SettingsViewModel(settingsRepository = settingsRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
