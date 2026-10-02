package com.mapmate.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.domain.model.TrackingSession
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.ScheduleAwareRecommendationResolver
import com.mapmate.domain.alarm.completedArrivalEventsToExclude
import com.mapmate.domain.alarm.hasCompletedCommuteToday
import com.mapmate.domain.util.runCatchingCancellable
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val routineRepository: RoutineRepository,
    private val commuteRecordRepository: CommuteRecordRepository,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy? = null,
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    private val scheduledRouteProvider: ScheduledRouteProvider? = null,
    private val trackingSessionStore: TrackingSessionStore? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val isActive = MutableStateFlow(false)
    private val refreshCount = MutableStateFlow(0L)
    private val recommendationResolver = ScheduleAwareRecommendationResolver(
        routeEstimateProvider = routeEstimateProvider,
        departureTimeCalculator = departureTimeCalculator,
        alarmPlanner = alarmPlanner,
        adjustmentPolicy = adjustmentPolicy,
        scheduledRouteProvider = scheduledRouteProvider,
    )

    init {
        observeSavedRoutines()
    }

    fun setActive(active: Boolean) {
        isActive.value = active
        if (!active) _uiState.update { it.copy(isRefreshing = false) }
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            scheduledRouteProvider?.invalidate()
            refreshCount.update { it + 1 }
        }
    }

    fun deleteRoutine(routine: Routine) {
        val routineId = routine.id ?: return

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
            }

            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        deletingRoutineId = null,
                        errorMessage = null,
                        successMessage = "'${routine.name}' 루틴을 삭제했습니다.",
                    )
                } else {
                    it.copy(
                        deletingRoutineId = null,
                        errorMessage = "루틴 삭제에 실패했습니다. 다시 시도해 주세요.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    private fun observeSavedRoutines() {
        viewModelScope.launch {
            combine(isActive, refreshCount) { active, _ -> active }.collectLatest { active ->
                if (!active) return@collectLatest
                combine(
                routineRepository.observeRoutines(),
                commuteRecordRepository.observeRecords(),
                minuteTicker(),
                scheduledRouteProvider?.revisions?.onStart { emit(0L) } ?: flowOf(0L),
                observeTrackingSessions(),
            ) { routines, records, _, _, sessionState ->
                HomeSourceState(
                    routines = routines,
                    records = records,
                    now = nowProvider(),
                    sessions = sessionState.sessions,
                    sessionError = sessionState.errorMessage,
                )
            }
                .catch {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = "저장된 루틴을 불러오지 못했습니다.",
                        )
                    }
                }
                .collectLatest { sourceState ->
                    _uiState.update { it.copy(isRefreshing = true) }
                    val routines = sourceState.routines
                    val records = sourceState.records
                    val now = sourceState.now
                    val dashboardRecommendation = routines
                        .filter { it.repeatDays.isNotEmpty() }
                        .map { it.toDashboardRecommendation(now, records) }
                        .nextDepartureRecommendation()

                    _uiState.update {
                        it.copy(
                            savedRoutines = routines,
                            dashboardRecommendation = dashboardRecommendation,
                            nowEpochMillis = now.toInstant().toEpochMilli(),
                            hasCompletedTodayCommute = records.hasCompletedCommuteToday(now),
                            pendingTrackingRoutines = sourceState.sessions.sortedByDescending { it.startedAtEpochMillis }
                                .filter { session -> records.none { it.routineId == session.routineId &&
                                    it.targetArrivalAtEpochMillis == session.targetArrivalAtEpochMillis } }
                                .mapNotNull { session -> routines.firstOrNull { it.id == session.routineId } },
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = sourceState.sessionError,
                        )
                    }
                }
            }
        }
    }

    private fun observeTrackingSessions() = flow {
        val store = trackingSessionStore
        if (store == null) {
            emit(TrackingSessionObservation())
        } else {
            emitAll(store.observeSessions().map { TrackingSessionObservation(sessions = it) })
        }
    }.catch {
        emit(TrackingSessionObservation(errorMessage = "진행 중인 측정 내역을 불러오지 못했습니다. 새로고침하거나 이동 기록에서 복구해 주세요."))
    }

    private fun List<RoutineRecommendationUiModel>.nextDepartureRecommendation(): RoutineRecommendationUiModel? {
        return filter { it.recommendedDepartureAtEpochMillis != null }
            .minByOrNull { it.recommendedDepartureAtEpochMillis ?: Long.MAX_VALUE }
            ?: firstOrNull()
    }

    private fun minuteTicker() = flow {
        while (true) {
            emit(nowProvider())
            delay(MINUTE_MILLIS)
        }
    }

    private suspend fun Routine.toDashboardRecommendation(
        now: ZonedDateTime,
        records: List<CommuteRecord>,
    ): RoutineRecommendationUiModel {
        val excludedArrivalEvents = records.completedArrivalEventsToExclude(
            routine = this,
            now = now,
        )
        return runCatchingCancellable {
            recommendationResolver.resolve(
                routine = this,
                now = now,
                excludedArrivalEvents = excludedArrivalEvents,
            ).recommendation
        }.getOrElse {
            recommendationResolver.fallback(
                routine = this,
                now = now,
                excludedArrivalEvents = excludedArrivalEvents,
            ).recommendation
        }
    }

    companion object {
        private const val MINUTE_MILLIS = 60_000L

        fun factory(
            routineRepository: RoutineRepository,
            commuteRecordRepository: CommuteRecordRepository,
            routeEstimateProvider: RouteEstimateProvider,
            scheduledRouteProvider: ScheduledRouteProvider? = null,
            trackingSessionStore: TrackingSessionStore? = null,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                        return HomeViewModel(
                            routineRepository = routineRepository,
                            commuteRecordRepository = commuteRecordRepository,
                            routeEstimateProvider = routeEstimateProvider,
                            scheduledRouteProvider = scheduledRouteProvider,
                            trackingSessionStore = trackingSessionStore,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }

    private data class HomeSourceState(
        val routines: List<Routine>,
        val records: List<CommuteRecord>,
        val now: ZonedDateTime,
        val sessions: List<TrackingSession>,
        val sessionError: String?,
    )

    private data class TrackingSessionObservation(
        val sessions: List<TrackingSession> = emptyList(),
        val errorMessage: String? = null,
    )
}
