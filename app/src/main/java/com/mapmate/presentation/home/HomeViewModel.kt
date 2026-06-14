package com.mapmate.presentation.home

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

class HomeViewModel(
    private val routineRepository: RoutineRepository,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
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
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "저장된 루틴을 불러오지 못했습니다.",
                        )
                    }
                }
                .collect { routines ->
                    val dashboardRecommendation = routines.firstOrNull()
                        ?.toDashboardRecommendation()

                    _uiState.update {
                        it.copy(
                            savedRoutines = routines,
                            dashboardRecommendation = dashboardRecommendation,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
        }
    }

    private suspend fun Routine.toDashboardRecommendation(): RoutineRecommendationUiModel {
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
