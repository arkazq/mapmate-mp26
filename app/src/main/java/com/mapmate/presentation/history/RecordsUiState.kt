package com.mapmate.presentation.history

import com.mapmate.domain.model.CommuteRecord

data class RecordsUiState(
    val records: List<CommuteRecord> = emptyList(),
    val isLoading: Boolean = true,
)
