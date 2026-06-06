package com.mapmate.presentation.prediction

import com.mapmate.domain.model.Routine
import com.mapmate.presentation.common.RoutineRecommendationUiModel

data class PredictionDetailUiState(
    val routine: Routine,
    val recommendation: RoutineRecommendationUiModel? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
