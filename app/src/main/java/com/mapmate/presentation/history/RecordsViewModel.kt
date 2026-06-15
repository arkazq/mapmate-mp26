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

    fun selectRoutineFilter(routineId: Long?) {
        _uiState.update {
            it.copy(
                selectedRoutineId = routineId,
                stats = RecordsStats.from(
                    routineId?.let { selectedId ->
                        it.records.filter { record -> record.routineId == selectedId }
                    } ?: it.records,
                ),
            )
        }
    }

    private fun observeRecords() {
        viewModelScope.launch {
            commuteRecordRepository.observeRecords().collect { records ->
                _uiState.update {
                    val selectedRoutineId = it.selectedRoutineId
                        ?.takeIf { routineId -> records.any { record -> record.routineId == routineId } }
                    val filteredRecords = selectedRoutineId?.let { routineId ->
                        records.filter { record -> record.routineId == routineId }
                    } ?: records
                    it.copy(
                        records = records,
                        selectedRoutineId = selectedRoutineId,
                        isLoading = false,
                        stats = RecordsStats.from(filteredRecords),
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
