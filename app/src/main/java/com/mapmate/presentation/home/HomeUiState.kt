package com.mapmate.presentation.home

import com.mapmate.domain.model.Routine
import com.mapmate.presentation.common.RoutineRecommendationUiModel

data class HomeUiState(
    val savedRoutines: List<Routine> = emptyList(),
    val dashboardRecommendation: RoutineRecommendationUiModel? = null,
    val nowEpochMillis: Long = System.currentTimeMillis(),
    val hasCompletedTodayCommute: Boolean = false,
    val isLoading: Boolean = true,
    val deletingRoutineId: Long? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val hasSavedRoutines: Boolean
        get() = savedRoutines.isNotEmpty()
}
