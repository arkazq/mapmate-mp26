package com.mapmate.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StoredRecordViewModel(
    private val repository: CommuteRecordRepository,
    private val recordId: Long,
) : ViewModel() {
    private val state = MutableStateFlow(StoredRecordState())
    val uiState = state.asStateFlow()
    private var loadingJob: Job? = null

    init { retry() }

    fun retry() {
        loadingJob?.cancel()
        state.value = StoredRecordState()
        loadingJob = viewModelScope.launch {
            val result = runCatchingCancellable { repository.getRecord(recordId) }
            state.value = StoredRecordState(
                isLoading = false,
                record = result.getOrNull(),
                errorMessage = if (result.isFailure) "이동 기록을 불러오지 못했어요." else null,
            )
        }
    }

    companion object {
        fun factory(repository: CommuteRecordRepository, recordId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(StoredRecordViewModel::class.java))
                return StoredRecordViewModel(repository, recordId) as T
            }
        }
    }
}

data class StoredRecordState(
    val isLoading: Boolean = true,
    val record: CommuteRecord? = null,
    val errorMessage: String? = null,
)
