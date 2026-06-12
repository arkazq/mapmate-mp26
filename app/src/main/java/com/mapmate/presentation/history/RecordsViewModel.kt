package com.mapmate.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.repository.CommuteRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordsViewModel(
    private val commuteRecordRepository: CommuteRecordRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()

    init {
        observeRecords()
    }

    private fun observeRecords() {
        viewModelScope.launch {
            commuteRecordRepository.observeRecords().collect { records ->
                _uiState.update {
                    it.copy(
                        records = records,
                        isLoading = false,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            commuteRecordRepository: CommuteRecordRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RecordsViewModel::class.java)) {
                        return RecordsViewModel(
                            commuteRecordRepository = commuteRecordRepository,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
