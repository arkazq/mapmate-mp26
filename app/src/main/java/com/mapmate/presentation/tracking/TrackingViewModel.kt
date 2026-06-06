package com.mapmate.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toRecommendationUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val routine: Routine,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackingUiState(routine = routine))
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()

    init {
        loadTrackingSummary()
    }

    fun onPrimaryActionClick() {
        _uiState.update { state ->
            state.copy(
                stage = when (state.stage) {
                    TrackingStage.Planned -> TrackingStage.Boarded
                    TrackingStage.Boarded -> TrackingStage.Arrived
                    TrackingStage.Arrived -> TrackingStage.Arrived
                },
            )
        }
        // TODO: 실제 CommuteRecord 저장과 도착 오차 기반 보정은 데이터 계층 작업에서 연결한다.
    }

    private fun loadTrackingSummary() {
        viewModelScope.launch {
            val recommendation = runCatching {
                val routeEstimate = routeEstimateProvider.getRouteEstimate(
                    origin = routine.origin,
                    destination = routine.destination,
                    transportMode = routine.transportMode,
                )
                routine.toRecommendationUiModel(
                    routeEstimate = routeEstimate,
                    departureTimeCalculator = departureTimeCalculator,
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

    companion object {
        fun factory(
            routine: Routine,
            routeEstimateProvider: RouteEstimateProvider,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TrackingViewModel::class.java)) {
                        return TrackingViewModel(
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
