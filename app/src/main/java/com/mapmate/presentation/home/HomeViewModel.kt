package com.mapmate.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.applyBoardingSafeDeparture
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toRecommendationUiModel
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val routineRepository: RoutineRepository,
    private val commuteRecordRepository: CommuteRecordRepository,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy = DepartureAdjustmentPolicy(),
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeSavedRoutines()
    }

    fun deleteRoutine(routine: Routine) {
        val routineId = routine.id ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    deletingRoutineId = routineId,
                    errorMessage = null,
                    successMessage = null,
                )
            }

            val result = runCatching {
                routineRepository.deleteRoutine(routineId)
            }

            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        deletingRoutineId = null,
                        errorMessage = null,
                        successMessage = "'${routine.name}' 루틴을 삭제했습니다.",
                    )
                } else {
                    it.copy(
                        deletingRoutineId = null,
                        errorMessage = "루틴 삭제에 실패했습니다. 다시 시도해 주세요.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    private fun observeSavedRoutines() {
        viewModelScope.launch {
            combine(
                routineRepository.observeRoutines(),
                commuteRecordRepository.observeRecords(),
                minuteTicker(),
            ) { routines, records, now ->
                HomeSourceState(
                    routines = routines,
                    records = records,
                    now = now,
                )
            }
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "저장된 루틴을 불러오지 못했습니다.",
                        )
                    }
                }
                .collect { sourceState ->
                    val routines = sourceState.routines
                    val records = sourceState.records
                    val now = sourceState.now
                    val dashboardRecommendation = routines
                        .map { it.toDashboardRecommendation(now, records) }
                        .nextDepartureRecommendation()

                    _uiState.update {
                        it.copy(
                            savedRoutines = routines,
                            dashboardRecommendation = dashboardRecommendation,
                            nowEpochMillis = now.toInstant().toEpochMilli(),
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private fun List<RoutineRecommendationUiModel>.nextDepartureRecommendation(): RoutineRecommendationUiModel? {
        return filter { it.recommendedDepartureAtEpochMillis != null }
            .minByOrNull { it.recommendedDepartureAtEpochMillis ?: Long.MAX_VALUE }
            ?: firstOrNull()
    }

    private fun minuteTicker() = flow {
        while (true) {
            emit(nowProvider())
            delay(MINUTE_MILLIS)
        }
    }

    private suspend fun Routine.toDashboardRecommendation(
        now: ZonedDateTime,
        records: List<CommuteRecord>,
    ): RoutineRecommendationUiModel {
        val excludedArrivalAtEpochMillis = records.completedArrivalEventToExclude(
            routine = this,
            now = now,
        )
        return runCatching {
            val baseRouteEstimate = routeEstimateProvider.getRouteEstimate(
                origin = origin,
                destination = destination,
                transportMode = transportMode,
                routineId = id,
            )
            val baseSchedule = alarmPlanner.nextAlarmForRoutine(
                routine = this,
                routeDurationMinutes = baseRouteEstimate.estimatedMinutes,
                now = now,
                excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
            )
            val routeEstimate = if (baseSchedule?.shouldApplyRealtime(now) == true) {
                routeEstimateProvider.getRouteEstimate(
                    origin = origin,
                    destination = destination,
                    transportMode = transportMode,
                    routineId = id,
                    scheduledDepartureEpochMillis = baseSchedule.triggerAtEpochMillis,
                    targetArrivalEpochMillis = baseSchedule.targetArrivalAtEpochMillis,
                )
            } else {
                baseRouteEstimate
            }
            val finalSchedule = alarmPlanner.nextAlarmForRoutine(
                routine = this,
                routeDurationMinutes = routeEstimate.estimatedMinutes,
                now = now,
                excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
            )
            val displaySchedule = finalSchedule?.let {
                adjustmentPolicy.adjust(
                    previousSchedule = baseSchedule,
                    proposedSchedule = it,
                ).applyBoardingSafeDeparture(
                    boardingAdvice = routeEstimate.boardingAdvice,
                    nowEpochMillis = now.toInstant().toEpochMilli(),
                    zoneId = now.zone,
                )
            }
            toRecommendationUiModel(
                routeEstimate = routeEstimate,
                departureTimeCalculator = departureTimeCalculator,
                now = now.toLocalTime(),
                recommendedDepartureAtEpochMillis = displaySchedule?.triggerAtEpochMillis,
                displayedDepartureTime = displaySchedule?.recommendedDepartureTime,
                isImmediateDepartureOverride = displaySchedule?.triggerAtEpochMillis
                    ?.let { it <= now.toInstant().toEpochMilli() }
                    ?: false,
            )
        }.getOrElse {
            val fallbackRouteDurationMinutes = fallbackRouteDurationMinutes()
            val fallbackSchedule = alarmPlanner.nextAlarmForRoutine(
                routine = this,
                routeDurationMinutes = fallbackRouteDurationMinutes,
                now = now,
                excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
            )
            toFallbackRecommendationUiModel(
                departureTimeCalculator = departureTimeCalculator,
                now = now.toLocalTime(),
                recommendedDepartureAtEpochMillis = fallbackSchedule?.triggerAtEpochMillis,
                displayedDepartureTime = fallbackSchedule?.recommendedDepartureTime,
                isImmediateDepartureOverride = fallbackSchedule?.triggerAtEpochMillis
                    ?.let { it <= now.toInstant().toEpochMilli() }
                    ?: false,
            )
        }
    }

    private fun com.mapmate.domain.alarm.DepartureAlarmSchedule.shouldApplyRealtime(now: ZonedDateTime): Boolean {
        val minutesUntilDeparture = (triggerAtEpochMillis - now.toInstant().toEpochMilli()) / MILLIS_PER_MINUTE
        return minutesUntilDeparture in 0..REALTIME_LOOKAHEAD_MINUTES
    }

    private fun Routine.fallbackRouteDurationMinutes(): Int {
        return when (transportMode) {
            com.mapmate.domain.model.TransportMode.TRANSIT -> 42
            com.mapmate.domain.model.TransportMode.WALK -> 25
            com.mapmate.domain.model.TransportMode.CAR -> 30
        }
    }

    companion object {
        private const val MINUTE_MILLIS = 60_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val REALTIME_LOOKAHEAD_MINUTES = 30L

        fun factory(
            routineRepository: RoutineRepository,
            commuteRecordRepository: CommuteRecordRepository,
            routeEstimateProvider: RouteEstimateProvider,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                        return HomeViewModel(
                            routineRepository = routineRepository,
                            commuteRecordRepository = commuteRecordRepository,
                            routeEstimateProvider = routeEstimateProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }

    private data class HomeSourceState(
        val routines: List<Routine>,
        val records: List<CommuteRecord>,
        val now: ZonedDateTime,
    )
}

internal fun List<CommuteRecord>.completedArrivalEventToExclude(
    routine: Routine,
    now: ZonedDateTime,
): Long? {
    val routineId = routine.id ?: return null
    return asSequence()
        .filter { record -> record.routineId == routineId }
        .sortedByDescending { record -> record.arrivedAtEpochMillis }
        .map { record ->
            val arrivalDate = java.time.Instant.ofEpochMilli(record.arrivedAtEpochMillis)
                .atZone(now.zone)
                .toLocalDate()
            arrivalDate.atTime(record.targetArrivalTime)
                .atZone(now.zone)
        }
        .firstOrNull { targetArrivalAt ->
            !targetArrivalAt.isBefore(now)
        }
        ?.toInstant()
        ?.toEpochMilli()
}
