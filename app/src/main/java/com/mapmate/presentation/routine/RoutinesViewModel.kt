package com.mapmate.presentation.routine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.alarm.completedArrivalEventsToExclude
import com.mapmate.domain.util.runCatchingCancellable
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.ScheduleAwareRecommendationResolver
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutinesViewModel(
    private val routineRepository: RoutineRepository,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val commuteRecordRepository: CommuteRecordRepository,
    private val trackingSessionStore: TrackingSessionStore,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy? = null,
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    private val scheduledRouteProvider: ScheduledRouteProvider? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutinesUiState())
    val uiState: StateFlow<RoutinesUiState> = _uiState.asStateFlow()
    private val isActive = MutableStateFlow(false)
    private val refreshVersion = MutableStateFlow(0L)
    private val recommendationResolver = ScheduleAwareRecommendationResolver(
        routeEstimateProvider = routeEstimateProvider,
        departureTimeCalculator = departureTimeCalculator,
        alarmPlanner = alarmPlanner,
        adjustmentPolicy = adjustmentPolicy,
        scheduledRouteProvider = scheduledRouteProvider,
    )

    init {
        observeRoutines()
    }

    fun deleteRoutine(routine: Routine) {
        val routineId = routine.id ?: return
        if (_uiState.value.deletingRoutineId != null) return
        _uiState.update { it.copy(deletingRoutineId = routineId, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    deletingRoutineId = routineId,
                    errorMessage = null,
                    successMessage = null,
                )
            }

            val result = runCatchingCancellable {
                routineRepository.deleteRoutine(routineId)
                runCatchingCancellable { trackingSessionStore.clear(routineId) }
            }

            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        deletingRoutineId = null,
                        successMessage = "'${routine.name}' 루틴을 삭제했습니다.",
                        errorMessage = null,
                    )
                } else {
                    it.copy(
                        deletingRoutineId = null,
                        successMessage = null,
                        errorMessage = "루틴 삭제에 실패했습니다. 다시 시도해 주세요.",
                    )
                }
            }
        }
    }

    private fun observeRoutines() {
        viewModelScope.launch {
            combine(isActive, refreshVersion) { active, _ -> active }.collectLatest { active ->
                if (!active) return@collectLatest
                combine(routineRepository.observeRoutines(), commuteRecordRepository.observeRecords(),
                    scheduledRouteProvider?.revisions?.onStart { emit(0L) } ?: flowOf(0L)) { routines, records, _ ->
                    routines to records
                }
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "저장된 루틴을 불러오지 못했습니다.",
                        )
                    }
                }
                .collectLatest { (routines, records) ->
                    val now = nowProvider()
                    val recommendations = routines.map { routine ->
                        routine.toRecommendation(now, records)
                    }.sortedBy { it.recommendedDepartureAtEpochMillis ?: Long.MAX_VALUE }

                    _uiState.update {
                        it.copy(
                            recommendations = recommendations,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
            }
        }
    }

    fun setActive(active: Boolean) { isActive.value = active }
    fun refresh() {
        viewModelScope.launch {
            scheduledRouteProvider?.invalidate()
            refreshVersion.update { it + 1 }
        }
    }

    private suspend fun Routine.toRecommendation(now: ZonedDateTime, records: List<CommuteRecord>): RoutineRecommendationUiModel {
        val excluded = records.completedArrivalEventsToExclude(this, now)
        if (repeatDays.isEmpty()) return recommendationResolver.fallback(this, now).recommendation
        return runCatchingCancellable {
            recommendationResolver.resolve(
                routine = this,
                now = now,
                excludedArrivalEvents = excluded,
            ).recommendation
        }.getOrElse {
            recommendationResolver.fallback(
                routine = this,
                now = now,
                excludedArrivalEvents = excluded,
            ).recommendation
        }
    }

    companion object {
        fun factory(
            routineRepository: RoutineRepository,
            routeEstimateProvider: RouteEstimateProvider,
            commuteRecordRepository: CommuteRecordRepository,
            trackingSessionStore: TrackingSessionStore,
            scheduledRouteProvider: ScheduledRouteProvider? = null,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RoutinesViewModel::class.java)) {
                        return RoutinesViewModel(
                            routineRepository = routineRepository,
                            routeEstimateProvider = routeEstimateProvider,
                            commuteRecordRepository = commuteRecordRepository,
                            trackingSessionStore = trackingSessionStore,
                            scheduledRouteProvider = scheduledRouteProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
