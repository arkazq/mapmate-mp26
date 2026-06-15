package com.mapmate.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toRecommendationUiModel
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val routineRepository: RoutineRepository,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
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
            routineRepository.observeRoutines()
                .combine(minuteTicker()) { routines, now ->
                    routines to now
                }
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "저장된 루틴을 불러오지 못했습니다.",
                        )
                    }
                }
                .collect { (routines, now) ->
                    val dashboardRecommendation = routines.firstOrNull()
                        ?.toDashboardRecommendation(now)

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

    private fun minuteTicker() = flow {
        while (true) {
            emit(nowProvider())
            delay(MINUTE_MILLIS)
        }
    }

    private suspend fun Routine.toDashboardRecommendation(now: ZonedDateTime): RoutineRecommendationUiModel {
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
            )
            val routeEstimate = if (baseSchedule?.shouldApplyRealtime(now) == true) {
                routeEstimateProvider.getRouteEstimate(
                    origin = origin,
                    destination = destination,
                    transportMode = transportMode,
                    routineId = id,
                    scheduledDepartureEpochMillis = baseSchedule.triggerAtEpochMillis,
                )
            } else {
                baseRouteEstimate
            }
            val finalSchedule = alarmPlanner.nextAlarmForRoutine(
                routine = this,
                routeDurationMinutes = routeEstimate.estimatedMinutes,
                now = now,
            )
            toRecommendationUiModel(
                routeEstimate = routeEstimate,
                departureTimeCalculator = departureTimeCalculator,
                now = now.toLocalTime(),
                recommendedDepartureAtEpochMillis = finalSchedule?.triggerAtEpochMillis,
            )
        }.getOrElse {
            val fallbackRouteDurationMinutes = fallbackRouteDurationMinutes()
            val fallbackSchedule = alarmPlanner.nextAlarmForRoutine(
                routine = this,
                routeDurationMinutes = fallbackRouteDurationMinutes,
                now = now,
            )
            toFallbackRecommendationUiModel(
                departureTimeCalculator = departureTimeCalculator,
                now = now.toLocalTime(),
                recommendedDepartureAtEpochMillis = fallbackSchedule?.triggerAtEpochMillis,
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
            routeEstimateProvider: RouteEstimateProvider,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                        return HomeViewModel(
                            routineRepository = routineRepository,
                            routeEstimateProvider = routeEstimateProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
