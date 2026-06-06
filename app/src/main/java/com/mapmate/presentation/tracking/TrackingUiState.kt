package com.mapmate.presentation.tracking

import com.mapmate.domain.model.Routine
import com.mapmate.presentation.common.RoutineRecommendationUiModel

data class TrackingUiState(
    val routine: Routine,
    val recommendation: RoutineRecommendationUiModel? = null,
    val stage: TrackingStage = TrackingStage.Planned,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val isCompleted: Boolean
        get() = stage == TrackingStage.Arrived
}

enum class TrackingStage {
    Planned,
    Boarded,
    Arrived,
}
