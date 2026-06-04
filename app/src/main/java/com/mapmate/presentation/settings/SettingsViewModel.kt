package com.mapmate.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.PersonalBufferChanged -> onPersonalBufferChanged(event.minutesText)
            is SettingsEvent.SafetyMarginChanged -> onSafetyMarginChanged(event.minutesText)
            is SettingsEvent.NotificationsEnabledChanged -> onNotificationsEnabledChanged(event.enabled)
            is SettingsEvent.DefaultTransportModeSelected -> onDefaultTransportModeSelected(event.transportMode)
            SettingsEvent.MessageCleared -> clearMessage()
        }
    }

    private fun onPersonalBufferChanged(minutesText: String) {
        val filteredText = minutesText.filter(Char::isDigit).take(2)
        _uiState.update {
            it.copy(
                personalBufferMinutes = filteredText,
                errorMessage = null,
            )
        }
        saveBufferSetting(
            minutesText = filteredText,
            errorMessage = "개인 보정 시간은 0분부터 60분 사이로 입력해 주세요.",
            save = settingsRepository::updatePersonalBufferMinutes,
        )
    }

    private fun onSafetyMarginChanged(minutesText: String) {
        val filteredText = minutesText.filter(Char::isDigit).take(2)
        _uiState.update {
            it.copy(
                safetyMarginMinutes = filteredText,
                errorMessage = null,
            )
        }
        saveBufferSetting(
            minutesText = filteredText,
            errorMessage = "안전 여유 시간은 0분부터 60분 사이로 입력해 주세요.",
            save = settingsRepository::updateSafetyMarginMinutes,
        )
    }

    private fun onNotificationsEnabledChanged(enabled: Boolean) {
        _uiState.update {
            it.copy(
                notificationsEnabled = enabled,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.updateNotificationsEnabled(enabled)
            }
            if (result.isFailure) {
                showPersistenceError()
            }
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
            val result = runCatching {
                settingsRepository.updateDefaultTransportMode(transportMode)
            }
            if (result.isFailure) {
                showPersistenceError()
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        personalBufferMinutes = settings.personalBufferMinutes.toString(),
                        safetyMarginMinutes = settings.safetyMarginMinutes.toString(),
                        notificationsEnabled = settings.notificationsEnabled,
                        defaultTransportMode = settings.defaultTransportMode,
                    )
                }
            }
        }
    }

    private fun saveBufferSetting(
        minutesText: String,
        errorMessage: String,
        save: suspend (Int) -> Unit,
    ) {
        if (minutesText.isBlank()) return

        val minutes = minutesText.toValidBufferMinutesOrNull()
        if (minutes == null) {
            _uiState.update { it.copy(errorMessage = errorMessage) }
            return
        }

        viewModelScope.launch {
            val result = runCatching {
                save(minutes)
            }
            if (result.isFailure) {
                showPersistenceError()
            }
        }
    }

    private fun showPersistenceError() {
        _uiState.update {
            it.copy(errorMessage = "설정 저장에 실패했습니다. 다시 시도해 주세요.")
        }
    }

    private fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun String.toValidBufferMinutesOrNull(): Int? {
        return toIntOrNull()?.takeIf(AppSettings::isValidBufferMinutes)
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
