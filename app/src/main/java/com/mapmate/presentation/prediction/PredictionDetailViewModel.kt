package com.mapmate.presentation.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toRecommendationUiModel
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PredictionDetailViewModel(
    private val routine: Routine,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(PredictionDetailUiState(routine = routine))
    val uiState: StateFlow<PredictionDetailUiState> = _uiState.asStateFlow()

    init {
        loadPrediction()
    }

    private fun loadPrediction() {
        viewModelScope.launch {
            val recommendation = runCatching {
                val now = nowProvider()
                val baseRouteEstimate = routeEstimateProvider.getRouteEstimate(
                    origin = routine.origin,
                    destination = routine.destination,
                    transportMode = routine.transportMode,
                    routineId = routine.id,
                )
                val baseSchedule = alarmPlanner.nextAlarmForRoutine(
                    routine = routine,
                    routeDurationMinutes = baseRouteEstimate.estimatedMinutes,
                    now = now,
                )
                val routeEstimate = if (baseSchedule?.shouldApplyRealtime(now) == true) {
                    routeEstimateProvider.getRouteEstimate(
                        origin = routine.origin,
                        destination = routine.destination,
                        transportMode = routine.transportMode,
                        routineId = routine.id,
                        scheduledDepartureEpochMillis = baseSchedule.triggerAtEpochMillis,
                    )
                } else {
                    baseRouteEstimate
                }
                val finalSchedule = alarmPlanner.nextAlarmForRoutine(
                    routine = routine,
                    routeDurationMinutes = routeEstimate.estimatedMinutes,
                    now = now,
                )
                routine.toRecommendationUiModel(
                    routeEstimate = routeEstimate,
                    departureTimeCalculator = departureTimeCalculator,
                    now = now.toLocalTime(),
                    recommendedDepartureAtEpochMillis = finalSchedule?.triggerAtEpochMillis,
                )
            }.getOrElse {
                routine.toFallbackRecommendationUiModel(departureTimeCalculator)
            }

            _uiState.update {
                it.copy(
                    recommendation = recommendation,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun com.mapmate.domain.alarm.DepartureAlarmSchedule.shouldApplyRealtime(now: ZonedDateTime): Boolean {
        val minutesUntilDeparture = (triggerAtEpochMillis - now.toInstant().toEpochMilli()) / MILLIS_PER_MINUTE
        return minutesUntilDeparture in 0..REALTIME_LOOKAHEAD_MINUTES
    }

    companion object {
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val REALTIME_LOOKAHEAD_MINUTES = 30L

        fun factory(
            routine: Routine,
            routeEstimateProvider: RouteEstimateProvider,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(PredictionDetailViewModel::class.java)) {
                        return PredictionDetailViewModel(
                            routine = routine,
                            routeEstimateProvider = routeEstimateProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
