package com.mapmate.presentation.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.model.Routine
import com.mapmate.domain.repository.RoutineRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class NavigationDataViewModel(private val repository: RoutineRepository) : ViewModel() {
    private val state = MutableStateFlow(NavigationDataState())
    val uiState = state.asStateFlow()
    private var loadingJob: Job? = null

    init { retry() }

    fun retry() {
        loadingJob?.cancel()
        state.value = NavigationDataState()
        loadingJob = viewModelScope.launch {
            repository.observeRoutines()
                .catch { state.value = NavigationDataState(errorMessage = "루틴을 불러오지 못했어요.") }
                .collect { state.value = NavigationDataState(routines = it) }
        }
    }

    companion object {
        fun factory(repository: RoutineRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(NavigationDataViewModel::class.java))
                return NavigationDataViewModel(repository) as T
            }
        }
    }
}

data class NavigationDataState(val routines: List<Routine>? = null, val errorMessage: String? = null)
