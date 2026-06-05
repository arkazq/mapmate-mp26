package com.mapmate.presentation.home

import com.mapmate.domain.model.Routine

data class HomeUiState(
    val savedRoutines: List<Routine> = emptyList(),
    val isLoading: Boolean = true,
    val deletingRoutineId: Long? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val hasSavedRoutines: Boolean
        get() = savedRoutines.isNotEmpty()
}
