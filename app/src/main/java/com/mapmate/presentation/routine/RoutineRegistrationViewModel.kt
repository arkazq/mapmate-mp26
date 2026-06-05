package com.mapmate.presentation.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.data.mock.MockPlaceSearchProvider
import com.mapmate.data.mock.MockRouteEstimateProvider
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class RoutineRegistrationViewModel(
    private val routineRepository: RoutineRepository,
    private val settingsRepository: SettingsRepository,
    private val placeSearchProvider: PlaceSearchProvider = MockPlaceSearchProvider(),
    private val routeEstimateProvider: RouteEstimateProvider = MockRouteEstimateProvider(),
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutineRegistrationUiState())
    val uiState: StateFlow<RoutineRegistrationUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        observeSettings()
        searchDestinationCandidates("")
        refreshSaveEnabled()
    }

    fun onEvent(event: RoutineRegistrationEvent) {
        when (event) {
            is RoutineRegistrationEvent.RoutineNameChanged -> onRoutineNameChanged(event.name)
            is RoutineRegistrationEvent.DestinationQueryChanged -> onDestinationQueryChanged(event.query)
            is RoutineRegistrationEvent.DestinationSelected -> onDestinationSelected(event.destination)
            is RoutineRegistrationEvent.ArrivalTimeChanged -> onArrivalTimeChanged(event.timeText)
            is RoutineRegistrationEvent.RepeatDayToggled -> onRepeatDayToggled(event.repeatDay)
            is RoutineRegistrationEvent.TransportModeSelected -> onTransportModeSelected(event.transportMode)
            is RoutineRegistrationEvent.PersonalBufferChanged -> onPersonalBufferChanged(event.minutesText)
            is RoutineRegistrationEvent.SafetyMarginChanged -> onSafetyMarginChanged(event.minutesText)
            RoutineRegistrationEvent.CalculateClicked -> onCalculateClicked()
            RoutineRegistrationEvent.SaveClicked -> onSaveClicked()
            RoutineRegistrationEvent.MessageCleared -> clearMessages()
        }
    }

    fun loadRoutineForEditing(routine: Routine) {
        _uiState.update {
            it.copy(
                editingRoutineId = routine.id,
                routineName = routine.name,
                destinationQuery = routine.destination.name,
                selectedDestination = routine.destination,
                targetArrivalTimeText = routine.targetArrivalTime.format(timeFormatter),
                selectedRepeatDays = routine.repeatDays,
                selectedTransportMode = routine.transportMode,
                personalBufferMinutes = routine.personalBufferMinutes.toString(),
                safetyMarginMinutes = routine.safetyMarginMinutes.toString(),
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            ).withSaveEnabled()
        }
        searchDestinationCandidates(routine.destination.name)
    }

    private fun onRoutineNameChanged(name: String) {
        updateState {
            copy(
                routineName = name,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun onDestinationQueryChanged(query: String) {
        updateState {
            copy(
                destinationQuery = query,
                selectedDestination = null,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
        searchDestinationCandidates(query)
    }

    private fun onDestinationSelected(destination: Destination) {
        updateState {
            copy(
                destinationQuery = destination.name,
                selectedDestination = destination,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun onArrivalTimeChanged(timeText: String) {
        updateState {
            copy(
                targetArrivalTimeText = timeText,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun onRepeatDayToggled(repeatDay: RepeatDay) {
        updateState {
            val nextDays = if (repeatDay in selectedRepeatDays) {
                selectedRepeatDays - repeatDay
            } else {
                selectedRepeatDays + repeatDay
            }

            copy(
                selectedRepeatDays = nextDays,
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun onTransportModeSelected(transportMode: TransportMode) {
        updateState {
            copy(
                selectedTransportMode = transportMode,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
        saveDefaultTransportMode(transportMode)
    }

    private fun onPersonalBufferChanged(minutesText: String) {
        val filteredMinutesText = minutesText.filter(Char::isDigit).take(2)
        updateState {
            copy(
                personalBufferMinutes = filteredMinutesText,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
        filteredMinutesText.toValidBufferMinutesOrNull()?.let(::savePersonalBufferSetting)
    }

    private fun onSafetyMarginChanged(minutesText: String) {
        val filteredMinutesText = minutesText.filter(Char::isDigit).take(2)
        updateState {
            copy(
                safetyMarginMinutes = filteredMinutesText,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
        filteredMinutesText.toValidBufferMinutesOrNull()?.let(::saveSafetyMarginSetting)
    }

    private fun onCalculateClicked() {
        val validatedInput = validateInput(_uiState.value) ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isCalculating = true, errorMessage = null, successMessage = null) }

            val routeEstimate = routeEstimateProvider.getRouteEstimate(
                destination = validatedInput.destination,
                transportMode = validatedInput.transportMode,
            )
            val recommendedDepartureTime = departureTimeCalculator.calculate(
                targetArrivalTime = validatedInput.targetArrivalTime,
                routeDurationMinutes = routeEstimate.estimatedMinutes,
                personalBufferMinutes = validatedInput.personalBufferMinutes,
                safetyMarginMinutes = validatedInput.safetyMarginMinutes,
            )

            _uiState.update {
                it.copy(
                    routeEstimate = routeEstimate,
                    recommendedDepartureTimeText = recommendedDepartureTime.format(timeFormatter),
                    isCalculating = false,
                    errorMessage = null,
                )
            }
            refreshSaveEnabled()
        }
    }

    private fun onSaveClicked() {
        val validatedInput = validateInput(_uiState.value) ?: return

        val routine = Routine(
            id = _uiState.value.editingRoutineId,
            name = validatedInput.routineName,
            destination = validatedInput.destination,
            targetArrivalTime = validatedInput.targetArrivalTime,
            repeatDays = validatedInput.repeatDays,
            transportMode = validatedInput.transportMode,
            personalBufferMinutes = validatedInput.personalBufferMinutes,
            safetyMarginMinutes = validatedInput.safetyMarginMinutes,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    isSaveEnabled = false,
                    successMessage = null,
                    errorMessage = null,
                )
            }

            val result = runCatching {
                routineRepository.saveRoutine(routine)
            }

            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        successMessage = if (it.isEditing) {
                            "'${routine.name}' 루틴이 수정되었습니다."
                        } else {
                            "'${routine.name}' 루틴이 저장되었습니다."
                        },
                        errorMessage = null,
                        isSaving = false,
                    )
                } else {
                    it.copy(
                        successMessage = null,
                        errorMessage = "루틴 저장에 실패했습니다. 다시 시도해 주세요.",
                        isSaving = false,
                    )
                }
            }
            refreshSaveEnabled()
        }
    }

    private fun searchDestinationCandidates(query: String) {
        viewModelScope.launch {
            val candidates = placeSearchProvider.search(query)
            _uiState.update { it.copy(destinationCandidates = candidates) }
            refreshSaveEnabled()
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    if (it.isEditing) return@update it.withSaveEnabled()

                    it.copy(
                        personalBufferMinutes = settings.personalBufferMinutes.toString(),
                        safetyMarginMinutes = settings.safetyMarginMinutes.toString(),
                        selectedTransportMode = settings.defaultTransportMode,
                        routeEstimate = null,
                        recommendedDepartureTimeText = "",
                    ).withSaveEnabled()
                }
            }
        }
    }

    private fun validateInput(state: RoutineRegistrationUiState): ValidatedRoutineInput? {
        val routineName = state.routineName.trim()
        if (routineName.isBlank()) {
            showValidationError("루틴 이름을 입력해 주세요.")
            return null
        }

        val destination = state.selectedDestination ?: state.destinationQuery.trim().takeIf { it.isNotBlank() }?.let {
            Destination(
                name = it,
                address = "직접 입력한 목적지",
                latitude = null,
                longitude = null,
            )
        }
        if (destination == null) {
            showValidationError("목적지를 입력하거나 선택해 주세요.")
            return null
        }

        val targetArrivalTime = parseArrivalTime(state.targetArrivalTimeText)
        if (targetArrivalTime == null) {
            showValidationError("도착 시각은 HH:mm 형식으로 입력해 주세요. 예: 09:00")
            return null
        }

        if (state.selectedRepeatDays.isEmpty()) {
            showValidationError("반복 요일을 하나 이상 선택해 주세요.")
            return null
        }

        val personalBufferMinutes = state.personalBufferMinutes.toValidBufferMinutesOrNull()
        if (personalBufferMinutes == null) {
            showValidationError("개인 버퍼는 0분부터 60분 사이로 입력해 주세요.")
            return null
        }

        val safetyMarginMinutes = state.safetyMarginMinutes.toValidBufferMinutesOrNull()
        if (safetyMarginMinutes == null) {
            showValidationError("안전 여유 시간은 0분부터 60분 사이로 입력해 주세요.")
            return null
        }

        return ValidatedRoutineInput(
            routineName = routineName,
            destination = destination,
            targetArrivalTime = targetArrivalTime,
            repeatDays = state.selectedRepeatDays,
            transportMode = state.selectedTransportMode,
            personalBufferMinutes = personalBufferMinutes,
            safetyMarginMinutes = safetyMarginMinutes,
        )
    }

    private fun parseArrivalTime(text: String): LocalTime? {
        val trimmedText = text.trim()
        val isExpectedFormat = Regex("""^([01]\d|2[0-3]):[0-5]\d$""").matches(trimmedText)
        if (!isExpectedFormat) return null

        return try {
            LocalTime.parse(trimmedText, timeFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun showValidationError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                successMessage = null,
                isCalculating = false,
            )
        }
        refreshSaveEnabled()
    }

    private fun clearMessages() {
        updateState {
            copy(
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    private fun savePersonalBufferSetting(minutes: Int) {
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.updatePersonalBufferMinutes(minutes)
            }
            if (result.isFailure) {
                showSettingsPersistenceError()
            }
        }
    }

    private fun saveSafetyMarginSetting(minutes: Int) {
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.updateSafetyMarginMinutes(minutes)
            }
            if (result.isFailure) {
                showSettingsPersistenceError()
            }
        }
    }

    private fun saveDefaultTransportMode(transportMode: TransportMode) {
        viewModelScope.launch {
            val result = runCatching {
                settingsRepository.updateDefaultTransportMode(transportMode)
            }
            if (result.isFailure) {
                showSettingsPersistenceError()
            }
        }
    }

    private fun showSettingsPersistenceError() {
        _uiState.update {
            it.copy(
                errorMessage = "설정 저장에 실패했습니다. 다시 시도해 주세요.",
                successMessage = null,
            )
        }
        refreshSaveEnabled()
    }

    private fun updateState(reducer: RoutineRegistrationUiState.() -> RoutineRegistrationUiState) {
        _uiState.update { state -> state.reducer().withSaveEnabled() }
    }

    private fun refreshSaveEnabled() {
        _uiState.update { it.withSaveEnabled() }
    }

    private fun RoutineRegistrationUiState.withSaveEnabled(): RoutineRegistrationUiState {
        return copy(
            isSaveEnabled = !isSaving &&
                routineName.isNotBlank() &&
                (selectedDestination != null || destinationQuery.isNotBlank()) &&
                parseArrivalTime(targetArrivalTimeText) != null &&
                selectedRepeatDays.isNotEmpty() &&
                personalBufferMinutes.toValidBufferMinutesOrNull() != null &&
                safetyMarginMinutes.toValidBufferMinutesOrNull() != null,
        )
    }

    private fun String.toValidBufferMinutesOrNull(): Int? {
        return toIntOrNull()?.takeIf {
            AppSettings.isValidBufferMinutes(it)
        }
    }

    private data class ValidatedRoutineInput(
        val routineName: String,
        val destination: Destination,
        val targetArrivalTime: LocalTime,
        val repeatDays: Set<RepeatDay>,
        val transportMode: TransportMode,
        val personalBufferMinutes: Int,
        val safetyMarginMinutes: Int,
    )

    companion object {
        fun factory(
            routineRepository: RoutineRepository,
            settingsRepository: SettingsRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RoutineRegistrationViewModel::class.java)) {
                        return RoutineRegistrationViewModel(
                            routineRepository = routineRepository,
                            settingsRepository = settingsRepository,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
