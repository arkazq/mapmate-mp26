package com.mapmate.presentation.tracking

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Routine
import com.mapmate.presentation.common.RoutineRecommendationUiModel

data class TrackingUiState(
    val routine: Routine,
    val recommendation: RoutineRecommendationUiModel? = null,
    val stage: TrackingStage = TrackingStage.Planned,
    val isLoading: Boolean = true,
    val isSavingRecord: Boolean = false,
    val errorMessage: String? = null,
    val completedRecord: CommuteRecord? = null,
    val adjustedPersonalBufferMinutes: Int? = null,
) {
    val isCompleted: Boolean
        get() = completedRecord != null
}

enum class TrackingStage {
    Planned,
    Boarded,
    Arrived,
}
