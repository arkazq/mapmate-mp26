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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch

class RecordsViewModel(
    private val commuteRecordRepository: CommuteRecordRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordsUiState())
    val uiState: StateFlow<RecordsUiState> = _uiState.asStateFlow()
    private var observationJob: Job? = null

    init {
        observeRecords()
    }

    fun selectRoutineFilter(routineId: Long?) {
        val selectedId = routineId?.takeIf { id -> _uiState.value.records.any { it.routineId == id } }
        _uiState.update {
            it.copy(
                selectedRoutineId = selectedId,
                stats = RecordsStats.from(
                    selectedId?.let { id ->
                        it.records.filter { record -> record.routineId == id }
                    } ?: it.records,
                ),
            )
        }
    }

    private fun observeRecords() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            commuteRecordRepository.observeRecords().catch {
                _uiState.update { it.copy(isLoading = false, errorMessage = "이동 기록을 불러오지 못했습니다. 다시 시도해 주세요.") }
            }.collect { records ->
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
                        errorMessage = null,
                        stats = RecordsStats.from(filteredRecords),
                    )
                }
            }
        }
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        observeRecords()
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
