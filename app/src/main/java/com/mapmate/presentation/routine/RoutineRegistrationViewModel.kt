package com.mapmate.presentation.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.model.hasValidCoordinates
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.util.runCatchingCancellable
import com.mapmate.presentation.common.ScheduleAwareRecommendationResolver
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class RoutineRegistrationViewModel(
    private val routineRepository: RoutineRepository,
    private val settingsRepository: SettingsRepository,
    private val placeSearchProvider: PlaceSearchProvider,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val currentLocationProvider: CurrentLocationProvider = UnavailableCurrentLocationProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy? = null,
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    scheduledRouteProvider: ScheduledRouteProvider? = null,
) : ViewModel() {
    private val restoredDraft = savedStateHandle.get<String>(DRAFT_KEY)?.let {
        runCatching { Json.decodeFromString<RoutineRegistrationDraft>(it).toUiState() }.getOrNull()
    }
    private val _uiState = MutableStateFlow(restoredDraft ?: RoutineRegistrationUiState())
    val uiState: StateFlow<RoutineRegistrationUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private var latestSettings = AppSettings()
    private var isInitialized = restoredDraft != null
    private var originSearchJob: Job? = null
    private var destinationSearchJob: Job? = null
    private var calculationJob: Job? = null
    private var currentLocationJob: Job? = null
    private var hasReceivedSettings = restoredDraft != null
    private val recommendationResolver = ScheduleAwareRecommendationResolver(
        routeEstimateProvider = routeEstimateProvider,
        departureTimeCalculator = departureTimeCalculator,
        alarmPlanner = alarmPlanner,
        adjustmentPolicy = adjustmentPolicy,
        scheduledRouteProvider = scheduledRouteProvider,
    )

    init {
        observeSettings()
        if (_uiState.value.selectedOrigin?.hasValidCoordinates() != true) searchOriginCandidates(_uiState.value.originQuery)
        if (_uiState.value.selectedDestination?.hasValidCoordinates() != true) searchDestinationCandidates(_uiState.value.destinationQuery)
        refreshSaveEnabled()
    }

    fun onEvent(event: RoutineRegistrationEvent) {
        when (event) {
            is RoutineRegistrationEvent.RoutineNameChanged -> onRoutineNameChanged(event.name)
            is RoutineRegistrationEvent.OriginQueryChanged -> onOriginQueryChanged(event.query)
            is RoutineRegistrationEvent.OriginSelected -> onOriginSelected(event.origin)
            is RoutineRegistrationEvent.DestinationQueryChanged -> onDestinationQueryChanged(event.query)
            is RoutineRegistrationEvent.DestinationSelected -> onDestinationSelected(event.destination)
            is RoutineRegistrationEvent.ArrivalTimeChanged -> onArrivalTimeChanged(event.timeText)
            is RoutineRegistrationEvent.RepeatDayToggled -> onRepeatDayToggled(event.repeatDay)
            is RoutineRegistrationEvent.TransportModeSelected -> onTransportModeSelected(event.transportMode)
            is RoutineRegistrationEvent.PersonalBufferChanged -> onPersonalBufferChanged(event.minutesText)
            is RoutineRegistrationEvent.SafetyMarginChanged -> onSafetyMarginChanged(event.minutesText)
            RoutineRegistrationEvent.CurrentLocationClicked -> onCurrentLocationClicked()
            RoutineRegistrationEvent.CurrentLocationPermissionDenied -> onCurrentLocationPermissionDenied()
            RoutineRegistrationEvent.CalculateClicked -> onCalculateClicked()
            RoutineRegistrationEvent.SaveClicked -> onSaveClicked()
            RoutineRegistrationEvent.MessageCleared -> clearMessages()
        }
    }

    fun initialize(routine: Routine?) {
        if (isInitialized) return
        isInitialized = true
        if (routine == null) startNewRoutine() else loadRoutineForEditing(routine)
    }

    fun loadRoutineForEditing(routine: Routine) {
        originSearchJob?.cancel()
        destinationSearchJob?.cancel()
        _uiState.update {
            it.copy(
                editingRoutineId = routine.id,
                routineName = routine.name,
                originQuery = routine.origin.name,
                selectedOrigin = routine.origin,
                originCandidates = emptyList(),
                isSearchingOrigin = false,
                originSearchError = null,
                destinationQuery = routine.destination.name,
                selectedDestination = routine.destination,
                destinationCandidates = emptyList(),
                isSearchingDestination = false,
                destinationSearchError = null,
                targetArrivalTimeText = routine.targetArrivalTime.format(timeFormatter),
                selectedRepeatDays = routine.repeatDays,
                selectedTransportMode = routine.transportMode,
                personalBufferMinutes = routine.personalBufferMinutes.toString(),
                safetyMarginMinutes = routine.safetyMarginMinutes.toString(),
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
                isSaveCompleted = false,
            ).withSaveEnabled()
        }
        if (!routine.origin.hasValidCoordinates()) searchOriginCandidates(routine.origin.name)
        if (!routine.destination.hasValidCoordinates()) searchDestinationCandidates(routine.destination.name)
        persistDraft()
    }

    fun startNewRoutine() {
        _uiState.value = RoutineRegistrationUiState(
            selectedTransportMode = latestSettings.defaultTransportMode,
            personalBufferMinutes = latestSettings.personalBufferMinutes.toString(),
            safetyMarginMinutes = latestSettings.safetyMarginMinutes.toString(),
        )
        searchOriginCandidates("")
        searchDestinationCandidates("")
        refreshSaveEnabled()
        persistDraft()
    }

    private fun onRoutineNameChanged(name: String) {
        val safeName = name.toSafeSingleLineInput(maxLength = ROUTINE_NAME_MAX_LENGTH)
        updateState {
            copy(
                routineName = safeName,
                successMessage = null,
                errorMessage = null,
                isSaveCompleted = false,
            )
        }
    }

    private fun onOriginQueryChanged(query: String) {
        currentLocationJob?.cancel()
        val safeQuery = query.toSafeSingleLineInput(maxLength = PLACE_QUERY_MAX_LENGTH)
        updateState {
            copy(
                originQuery = safeQuery,
                selectedOrigin = null,
                isGettingCurrentLocation = false,
                originCandidates = emptyList(),
                originSearchError = null,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
        searchOriginCandidates(safeQuery)
    }

    private fun onOriginSelected(origin: Destination) {
        currentLocationJob?.cancel()
        originSearchJob?.cancel()
        updateState {
            copy(
                originQuery = origin.name,
                selectedOrigin = origin,
                isGettingCurrentLocation = false,
                originCandidates = emptyList(),
                isSearchingOrigin = false,
                originSearchError = null,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun onDestinationQueryChanged(query: String) {
        val safeQuery = query.toSafeSingleLineInput(maxLength = PLACE_QUERY_MAX_LENGTH)
        updateState {
            copy(
                destinationQuery = safeQuery,
                selectedDestination = null,
                destinationCandidates = emptyList(),
                destinationSearchError = null,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
        searchDestinationCandidates(safeQuery)
    }

    private fun onDestinationSelected(destination: Destination) {
        destinationSearchJob?.cancel()
        updateState {
            copy(
                destinationQuery = destination.name,
                selectedDestination = destination,
                destinationCandidates = emptyList(),
                isSearchingDestination = false,
                destinationSearchError = null,
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
    }

    private fun onPersonalBufferChanged(minutesText: String) {
        val filteredMinutesText = minutesText.filterAsciiDigits().take(2)
        updateState {
            copy(
                personalBufferMinutes = filteredMinutesText,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun onSafetyMarginChanged(minutesText: String) {
        val filteredMinutesText = minutesText.filterAsciiDigits().take(2)
        updateState {
            copy(
                safetyMarginMinutes = filteredMinutesText,
                routeEstimate = null,
                recommendedDepartureTimeText = "",
                successMessage = null,
                errorMessage = null,
            )
        }
    }

    private fun onCalculateClicked() {
        if (_uiState.value.isCalculating) return
        val validatedInput = validateInput(_uiState.value) ?: return
        val inputRoutine = validatedInput.toRoutine(id = _uiState.value.editingRoutineId)
        _uiState.update { it.copy(isCalculating = true, errorMessage = null, successMessage = null) }
        calculationJob = viewModelScope.launch {

            val result = runCatchingCancellable {
                recommendationResolver.resolve(
                    routine = inputRoutine,
                    now = nowProvider(),
                )
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { scheduleAwareRecommendation ->
                        val recommendation = scheduleAwareRecommendation.recommendation
                        state.copy(
                            routeEstimate = scheduleAwareRecommendation.routeEstimate,
                            recommendedDepartureTimeText = recommendation.recommendedDepartureTimeText,
                            calculatedDepartureTimeText = recommendation.calculatedDepartureTimeText,
                            isImmediateDepartureRecommended = recommendation.isImmediateDepartureRecommended,
                            isCalculating = false,
                            errorMessage = null,
                        )
                    },
                    onFailure = {
                        state.copy(
                            isCalculating = false,
                            errorMessage = "경로를 계산하지 못했어요. 연결 상태를 확인한 뒤 다시 시도해 주세요.",
                        )
                    },
                )
            }
            refreshSaveEnabled()
        }
    }

    private fun onSaveClicked() {
        if (_uiState.value.isSaving || _uiState.value.isSaveCompleted) return
        val validatedInput = validateInput(_uiState.value) ?: return

        val routine = Routine(
            id = _uiState.value.editingRoutineId,
            name = validatedInput.routineName,
            origin = validatedInput.origin,
            destination = validatedInput.destination,
            targetArrivalTime = validatedInput.targetArrivalTime,
            repeatDays = validatedInput.repeatDays,
            transportMode = validatedInput.transportMode,
            personalBufferMinutes = validatedInput.personalBufferMinutes,
            safetyMarginMinutes = validatedInput.safetyMarginMinutes,
        )

        _uiState.update {
                it.copy(
                    isSaving = true,
                    isSaveEnabled = false,
                    successMessage = null,
                    errorMessage = null,
                    isSaveCompleted = false,
                )
        }
        viewModelScope.launch {
            val result = runCatchingCancellable {
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
                        isSaveCompleted = true,
                    )
                } else {
                    it.copy(
                        successMessage = null,
                        errorMessage = "루틴 저장에 실패했습니다. 다시 시도해 주세요.",
                        isSaving = false,
                        isSaveCompleted = false,
                    )
                }
            }
            refreshSaveEnabled()
            if (result.isSuccess) {
                _uiState.update { it.copy(hasUnsavedChanges = false) }
                savedStateHandle.remove<String>(DRAFT_KEY)
            }
        }
    }

    private fun searchDestinationCandidates(query: String) {
        destinationSearchJob?.cancel()
        _uiState.update { it.copy(isSearchingDestination = true, destinationSearchError = null) }
        destinationSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(350L)
            val result = runCatchingCancellable { placeSearchProvider.search(query) }
            _uiState.update {
                if (it.destinationQuery == query) it.copy(
                    destinationCandidates = result.getOrDefault(emptyList()),
                    isSearchingDestination = false,
                    destinationSearchError = result.exceptionOrNull()?.let { "장소 검색에 실패했습니다. 다시 시도해 주세요." },
                ) else it
            }
            refreshSaveEnabled()
        }
    }

    private fun searchOriginCandidates(query: String) {
        originSearchJob?.cancel()
        _uiState.update { it.copy(isSearchingOrigin = true, originSearchError = null) }
        originSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(350L)
            val result = runCatchingCancellable { placeSearchProvider.search(query) }
            _uiState.update {
                if (it.originQuery == query) it.copy(
                    originCandidates = result.getOrDefault(emptyList()),
                    isSearchingOrigin = false,
                    originSearchError = result.exceptionOrNull()?.let { "장소 검색에 실패했습니다. 다시 시도해 주세요." },
                ) else it
            }
            refreshSaveEnabled()
        }
    }

    private fun onCurrentLocationClicked() {
        if (_uiState.value.isGettingCurrentLocation) return
        _uiState.update { it.copy(isGettingCurrentLocation = true) }
        currentLocationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGettingCurrentLocation = true,
                    successMessage = null,
                    errorMessage = null,
                )
            }

            val result = runCatchingCancellable {
                currentLocationProvider.getCurrentLocation()
            }

            _uiState.update { state ->
                result.fold(
                    onSuccess = { origin ->
                        state.copy(
                            originQuery = origin.name,
                            selectedOrigin = origin,
                            originCandidates = emptyList(),
                            isSearchingOrigin = false,
                            originSearchError = null,
                            routeEstimate = null,
                            recommendedDepartureTimeText = "",
                            isGettingCurrentLocation = false,
                            successMessage = "현재 위치를 출발지로 설정했습니다.",
                            errorMessage = null,
                        )
                    },
                    onFailure = {
                        state.copy(
                            isGettingCurrentLocation = false,
                            successMessage = null,
                            errorMessage = "현재 위치를 가져오지 못했습니다. 위치 설정을 확인하거나 출발지를 검색해 선택해 주세요.",
                        )
                    },
                )
            }
            refreshSaveEnabled()
            if (result.isSuccess) {
                _uiState.update { it.copy(hasUnsavedChanges = true) }
                persistDraft()
            }
        }
    }

    private fun onCurrentLocationPermissionDenied() {
        _uiState.update {
            it.copy(
                errorMessage = "현재 위치를 사용하려면 위치 권한을 허용해 주세요.",
                successMessage = null,
                isGettingCurrentLocation = false,
            )
        }
        refreshSaveEnabled()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.catch {
                showSettingsPersistenceError()
            }.collect { settings ->
                latestSettings = settings
                if (hasReceivedSettings) return@collect
                hasReceivedSettings = true
                _uiState.update {
                    if (it.isEditing || it.hasUnsavedChanges) return@update it.withSaveEnabled()

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

        val destination = state.selectedDestination?.takeIf { it.hasValidCoordinates() }
        if (destination == null) {
            showValidationError("검색 결과에서 목적지를 선택해 주세요.")
            return null
        }

        val origin = state.selectedOrigin?.takeIf { it.hasValidCoordinates() }
        if (origin == null) {
            showValidationError("출발지를 검색해 선택하거나 현재 위치를 사용해 주세요.")
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
            origin = origin,
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
        calculationJob?.cancel()
        _uiState.update { state ->
            val next = state.reducer().copy(isCalculating = false)
            next.copy(hasUnsavedChanges = state.hasUnsavedChanges || next.toDraft() != state.toDraft()).withSaveEnabled()
        }
        persistDraft()
    }

    private fun persistDraft() {
        savedStateHandle[DRAFT_KEY] = Json.encodeToString(_uiState.value.toDraft())
    }

    private fun refreshSaveEnabled() {
        _uiState.update { it.withSaveEnabled() }
    }

    private fun RoutineRegistrationUiState.withSaveEnabled(): RoutineRegistrationUiState {
        return copy(
            isSaveEnabled = !isSaving &&
                routineName.isNotBlank() &&
                selectedOrigin?.hasValidCoordinates() == true &&
                selectedDestination?.hasValidCoordinates() == true &&
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

    private fun String.filterAsciiDigits(): String {
        return filter { it in '0'..'9' }
    }

    private fun String.toSafeSingleLineInput(maxLength: Int): String {
        return map { character ->
            if (character.isISOControl()) ' ' else character
        }.joinToString(separator = "")
            .replace(Regex("""\s+"""), " ")
            .take(maxLength)
    }

    private data class ValidatedRoutineInput(
        val routineName: String,
        val origin: Destination,
        val destination: Destination,
        val targetArrivalTime: LocalTime,
        val repeatDays: Set<RepeatDay>,
        val transportMode: TransportMode,
        val personalBufferMinutes: Int,
        val safetyMarginMinutes: Int,
    )

    private fun ValidatedRoutineInput.toRoutine(id: Long?): Routine {
        return Routine(
            id = id,
            name = routineName,
            origin = origin,
            destination = destination,
            targetArrivalTime = targetArrivalTime,
            repeatDays = repeatDays,
            transportMode = transportMode,
            personalBufferMinutes = personalBufferMinutes,
            safetyMarginMinutes = safetyMarginMinutes,
        )
    }

    companion object {
        private const val ROUTINE_NAME_MAX_LENGTH = 30
        private const val PLACE_QUERY_MAX_LENGTH = 80
        private const val DRAFT_KEY = "registration_draft_v1"

        private object UnavailableCurrentLocationProvider : CurrentLocationProvider {
            override suspend fun getCurrentLocation(): Destination {
                error("Current location provider is unavailable.")
            }
        }

        fun factory(
            routineRepository: RoutineRepository,
            settingsRepository: SettingsRepository,
            placeSearchProvider: PlaceSearchProvider,
            routeEstimateProvider: RouteEstimateProvider,
            currentLocationProvider: CurrentLocationProvider = UnavailableCurrentLocationProvider,
            scheduledRouteProvider: ScheduledRouteProvider? = null,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return createRegistrationModel(modelClass, SavedStateHandle())
                }

                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    return createRegistrationModel(modelClass, extras.createSavedStateHandle())
                }

                @Suppress("UNCHECKED_CAST")
                private fun <T : ViewModel> createRegistrationModel(modelClass: Class<T>, handle: SavedStateHandle): T {
                    if (modelClass.isAssignableFrom(RoutineRegistrationViewModel::class.java)) {
                        return RoutineRegistrationViewModel(
                            routineRepository = routineRepository,
                            settingsRepository = settingsRepository,
                            placeSearchProvider = placeSearchProvider,
                            routeEstimateProvider = routeEstimateProvider,
                            currentLocationProvider = currentLocationProvider,
                            savedStateHandle = handle,
                            scheduledRouteProvider = scheduledRouteProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
