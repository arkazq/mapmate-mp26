package com.mapmate.presentation.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.model.CommuteRecord
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.presentation.common.ScheduleAwareRecommendationResolver
import com.mapmate.domain.alarm.completedArrivalEventsToExclude
import com.mapmate.domain.util.runCatchingCancellable
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class PredictionDetailViewModel(
    routine: Routine,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val commuteRecordRepository: CommuteRecordRepository,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy? = null,
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    private val scheduledRouteProvider: ScheduledRouteProvider? = null,
) : ViewModel() {
    private var routine = routine
    private var isActive = false
    private var refreshJob: Job? = null
    private val _uiState = MutableStateFlow(PredictionDetailUiState(routine = routine))
    val uiState: StateFlow<PredictionDetailUiState> = _uiState.asStateFlow()
    private val recommendationResolver = ScheduleAwareRecommendationResolver(
        routeEstimateProvider = routeEstimateProvider,
        departureTimeCalculator = departureTimeCalculator,
        alarmPlanner = alarmPlanner,
        adjustmentPolicy = adjustmentPolicy,
        scheduledRouteProvider = scheduledRouteProvider,
    )

    fun setActive(active: Boolean) {
        if (isActive == active) return
        isActive = active
        refreshJob?.cancel()
        if (active) startPredictionRefresh()
    }

    fun updateRoutine(updatedRoutine: Routine) {
        if (routine == updatedRoutine) return
        routine = updatedRoutine
        _uiState.update { it.copy(routine = updatedRoutine, isLoading = true) }
        refresh()
    }

    fun refresh() {
        if (!isActive) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            scheduledRouteProvider?.invalidate()
            startPredictionRefresh()
        }
    }

    // 실시간 도착/탑승 정보가 stale해지지 않도록 화면이 떠 있는 동안 주기적으로 다시 계산한다.
    private fun startPredictionRefresh() {
        refreshJob = viewModelScope.launch {
            combine(commuteRecordRepository.observeRecords(), flow {
                while (true) { emit(Unit); delay(REFRESH_INTERVAL_MILLIS) }
            }, scheduledRouteProvider?.revisions?.onStart { emit(0L) } ?: flowOf(0L)) { records, _, _ -> records }
                .catch {
                    _uiState.update { it.copy(isLoading = false, recommendation = null,
                        errorMessage = "이동 기록을 불러오지 못했습니다. 다시 시도해 주세요.") }
                }
                .collectLatest { records -> refreshPrediction(records) }
        }
    }

    private suspend fun refreshPrediction(records: List<CommuteRecord>) {
        val now = nowProvider()
        val excludedArrivalEvents = records.completedArrivalEventsToExclude(
            routine = routine,
            now = now,
        )
        val recommendation = runCatchingCancellable {
            recommendationResolver.resolve(
                routine = routine,
                now = now,
                excludedArrivalEvents = excludedArrivalEvents,
            ).recommendation
        }.getOrElse {
            recommendationResolver.fallback(
                routine = routine,
                now = now,
                excludedArrivalEvents = excludedArrivalEvents,
            ).recommendation
        }

        _uiState.update {
            it.copy(
                recommendation = recommendation,
                nowEpochMillis = now.toInstant().toEpochMilli(),
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MILLIS = 60_000L

        fun factory(
            routine: Routine,
            routeEstimateProvider: RouteEstimateProvider,
            commuteRecordRepository: CommuteRecordRepository,
            scheduledRouteProvider: ScheduledRouteProvider? = null,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(PredictionDetailViewModel::class.java)) {
                        return PredictionDetailViewModel(
                            routine = routine,
                            routeEstimateProvider = routeEstimateProvider,
                            commuteRecordRepository = commuteRecordRepository,
                            scheduledRouteProvider = scheduledRouteProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
