package com.mapmate.presentation.routine

import com.mapmate.presentation.common.RoutineRecommendationUiModel

data class RoutinesUiState(
    val recommendations: List<RoutineRecommendationUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val deletingRoutineId: Long? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val hasRoutines: Boolean
        get() = recommendations.isNotEmpty()
}
