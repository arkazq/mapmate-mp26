package com.mapmate.presentation.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toRecommendationUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutinesViewModel(
    private val routineRepository: RoutineRepository,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutinesUiState())
    val uiState: StateFlow<RoutinesUiState> = _uiState.asStateFlow()

    init {
        observeRoutines()
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
                        successMessage = "'${routine.name}' 루틴을 삭제했습니다.",
                        errorMessage = null,
                    )
                } else {
                    it.copy(
                        deletingRoutineId = null,
                        successMessage = null,
                        errorMessage = "루틴 삭제에 실패했습니다. 다시 시도해 주세요.",
                    )
                }
            }
        }
    }

    private fun observeRoutines() {
        viewModelScope.launch {
            routineRepository.observeRoutines()
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "저장된 루틴을 불러오지 못했습니다.",
                        )
                    }
                }
                .collect { routines ->
                    val recommendations = routines.map { routine ->
                        routine.toRecommendation()
                    }

                    _uiState.update {
                        it.copy(
                            recommendations = recommendations,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private suspend fun Routine.toRecommendation(): RoutineRecommendationUiModel {
        return runCatching {
            val routeEstimate = routeEstimateProvider.getRouteEstimate(
                origin = origin,
                destination = destination,
                transportMode = transportMode,
                routineId = id,
            )
            toRecommendationUiModel(
                routeEstimate = routeEstimate,
                departureTimeCalculator = departureTimeCalculator,
            )
        }.getOrElse {
            toFallbackRecommendationUiModel(departureTimeCalculator)
        }
    }

    companion object {
        fun factory(
            routineRepository: RoutineRepository,
            routeEstimateProvider: RouteEstimateProvider,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RoutinesViewModel::class.java)) {
                        return RoutinesViewModel(
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
