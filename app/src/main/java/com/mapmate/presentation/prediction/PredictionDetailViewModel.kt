package com.mapmate.presentation.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.presentation.common.ScheduleAwareRecommendationResolver
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
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
    private val adjustmentPolicy: DepartureAdjustmentPolicy = DepartureAdjustmentPolicy(),
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(PredictionDetailUiState(routine = routine))
    val uiState: StateFlow<PredictionDetailUiState> = _uiState.asStateFlow()
    private val recommendationResolver = ScheduleAwareRecommendationResolver(
        routeEstimateProvider = routeEstimateProvider,
        departureTimeCalculator = departureTimeCalculator,
        alarmPlanner = alarmPlanner,
        adjustmentPolicy = adjustmentPolicy,
    )

    init {
        startPredictionRefresh()
    }

    // 실시간 도착/탑승 정보가 stale해지지 않도록 화면이 떠 있는 동안 주기적으로 다시 계산한다.
    private fun startPredictionRefresh() {
        viewModelScope.launch {
            while (true) {
                refreshPrediction()
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    private suspend fun refreshPrediction() {
        val recommendation = runCatching {
            val now = nowProvider()
            recommendationResolver.resolve(
                routine = routine,
                now = now,
            ).recommendation
        }.getOrElse {
            recommendationResolver.fallback(
                routine = routine,
                now = nowProvider(),
            ).recommendation
        }

        _uiState.update {
            it.copy(
                recommendation = recommendation,
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 60_000L

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
