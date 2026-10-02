package com.mapmate.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.calculator.PersonalBufferOptimizer
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TrackingSession
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.domain.alarm.routineScheduleFingerprint
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.util.runCatchingCancellable
import com.mapmate.domain.alarm.completedArrivalEventsToExclude
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.ScheduleAwareRecommendationResolver
import com.mapmate.presentation.common.toRecommendationUiModel
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class TrackingViewModel(
    private val routine: Routine,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val commuteRecordRepository: CommuteRecordRepository,
    private val routineRepository: RoutineRepository,
    private val settingsRepository: SettingsRepository,
    private val trackingSessionStore: TrackingSessionStore,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy? = null,
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    private val personalBufferOptimizer: PersonalBufferOptimizer = PersonalBufferOptimizer(),
    scheduledRouteProvider: ScheduledRouteProvider? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackingUiState(routine = routine))
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()
    private var startedAtEpochMillis: Long? = null
    private var summaryJob: Job? = null
    private val recommendationResolver = ScheduleAwareRecommendationResolver(
        routeEstimateProvider = routeEstimateProvider,
        departureTimeCalculator = departureTimeCalculator,
        alarmPlanner = alarmPlanner,
        adjustmentPolicy = adjustmentPolicy,
        scheduledRouteProvider = scheduledRouteProvider,
    )

    init {
        loadTrackingSummary()
        scheduledRouteProvider?.let { provider ->
            viewModelScope.launch {
                provider.revisions.drop(1).collect {
                    val state = _uiState.value
                    if (startedAtEpochMillis == null && state.completedRecord == null &&
                        !state.isSavingSession && !state.isSavingRecord) loadTrackingSummary()
                }
            }
        }
    }

    fun onPrimaryActionClick() {
        val state = _uiState.value
        if (state.isLoading || state.isSavingRecord || state.isSavingSession || !state.canRecord || state.completedRecord != null) return
        if (state.hasRouteSegments && state.stage != TrackingStage.Arrived) {
            saveCompletedRecord()
            return
        }

        when (state.stage) {
            TrackingStage.Planned -> {
                commitMeasurement(state.copy(stage = TrackingStage.Boarded), nowProvider().toInstant().toEpochMilli())
            }

            TrackingStage.Boarded -> saveCompletedRecord()
            TrackingStage.Arrived -> Unit
        }
    }

    private fun loadTrackingSummary() {
        _uiState.update { it.copy(isLoading = true, isLoadError = false, errorMessage = null) }
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            val now = nowProvider()
            val loaded = runCatchingCancellable {
                commuteRecordRepository.getRecentRecords(50, routine.id) to
                    routine.id?.let { trackingSessionStore.read(it) }
            }.getOrElse {
                _uiState.update { it.copy(isLoading = false, isLoadError = true, canRecord = false,
                    errorMessage = "저장된 이동 기록을 불러오지 못했습니다. 다시 시도해 주세요.") }
                return@launch
            }
            val (records, session) = loaded
            if (session != null) {
                val completed = records.firstOrNull { it.targetArrivalAtEpochMillis == session.targetArrivalAtEpochMillis }
                if (completed != null) {
                    runCatchingCancellable { trackingSessionStore.clear(session.routineId) }
                    _uiState.update { it.copy(isLoading = false, stage = TrackingStage.Arrived, completedRecord = completed) }
                    return@launch
                }
                if (session.routineFingerprint == routineScheduleFingerprint(routine)) {
                    startedAtEpochMillis = session.startedAtEpochMillis
                    val departure = Instant.ofEpochMilli(session.recommendedDepartureAtEpochMillis).atZone(now.zone).toLocalTime()
                    val recommendation = routine.toRecommendationUiModel(
                        RouteEstimate(session.routeDurationMinutes, session.routeSummary, "SavedTracking", "",
                            isFallbackEstimate = session.isFallbackEstimate, segments = session.routeSegments),
                        now = now.toLocalTime(),
                        recommendedDepartureAtEpochMillis = session.recommendedDepartureAtEpochMillis,
                        targetArrivalAtEpochMillis = session.targetArrivalAtEpochMillis,
                        displayedDepartureTime = departure,
                    )
                    _uiState.update { it.copy(isLoading = false, recommendation = recommendation,
                        stage = TrackingStage.Boarded, routeSegments = session.routeSegments,
                        isRestoredSession = true, canRecord = true) }
                    return@launch
                }
                _uiState.update { it.copy(isLoading = false, isLoadError = true, canRecord = false,
                    errorMessage = "측정 중 루틴이 변경되었습니다. 기존 측정을 지우고 다시 시작해 주세요.") }
                return@launch
            }
            val completedEvents = records.completedArrivalEventsToExclude(routine, now)
            val recommendation = runCatchingCancellable {
                recommendationResolver.resolve(
                    routine = routine,
                    now = now,
                    excludedArrivalEvents = completedEvents,
                ).recommendation
            }.getOrElse {
                recommendationResolver.fallback(
                    routine = routine,
                    now = now,
                    excludedArrivalEvents = completedEvents,
                ).recommendation
            }

            _uiState.update {
                it.copy(
                    recommendation = recommendation,
                    isLoading = false,
                    errorMessage = null,
                    routeSegments = recommendation.routeSegments.map {
                        it.copy(routineId = routine.id)
                    },
                    canRecord = recommendation.recommendedDepartureAtEpochMillis?.let {
                        Instant.ofEpochMilli(it).atZone(now.zone).toLocalDate() <= now.toLocalDate()
                    } == true,
                )
            }
        }
    }

    fun retry() {
        if (_uiState.value.isLoadError) loadTrackingSummary()
    }

    fun discardSavedSession() {
        val state = _uiState.value
        if (state.isLoading || state.isSavingSession || state.isSavingRecord) return
        _uiState.update { it.copy(isSavingSession = true) }
        viewModelScope.launch {
            runCatchingCancellable { routine.id?.let { trackingSessionStore.clear(it) } }
                .onSuccess {
                    startedAtEpochMillis = null
                    _uiState.value = TrackingUiState(routine)
                    loadTrackingSummary()
                }.onFailure {
                    _uiState.update { it.copy(isSavingSession = false, errorMessage = "이전 측정을 지우지 못했습니다.") }
                }
        }
    }

    private fun commitMeasurement(nextState: TrackingUiState, startAtEpochMillis: Long) {
        val recommendation = nextState.recommendation ?: return
        _uiState.update { it.copy(isSavingSession = true, errorMessage = null) }
        viewModelScope.launch {
            val result = runCatchingCancellable {
                val session = TrackingSession(
                    routineId = requireNotNull(routine.id), routineFingerprint = routineScheduleFingerprint(routine),
                    targetArrivalAtEpochMillis = requireNotNull(recommendation.targetArrivalAtEpochMillis),
                    recommendedDepartureAtEpochMillis = requireNotNull(recommendation.recommendedDepartureAtEpochMillis),
                    routeDurationMinutes = recommendation.routeDurationMinutes,
                    routeSummary = recommendation.routeSummary, startedAtEpochMillis = startAtEpochMillis,
                    routeSegments = nextState.routeSegments, isFallbackEstimate = recommendation.isFallbackEstimate,
                )
                withContext(NonCancellable) { trackingSessionStore.save(session) }
            }
            result.onSuccess {
                startedAtEpochMillis = startAtEpochMillis
                _uiState.value = nextState.copy(isSavingSession = false, errorMessage = null)
            }.onFailure {
                _uiState.update { it.copy(isSavingSession = false, errorMessage = "구간 시간을 저장하지 못했습니다. 다시 눌러 주세요.") }
            }
        }
    }

    fun onSegmentStart(segmentId: Long) {
        val now = nowProvider().toInstant().toEpochMilli()
        val state = _uiState.value
            val activeSegmentId = state.routeSegments.nextActionableSegmentId()
            if (state.isLoading || !state.canRecord || state.isSavingSession || state.isSavingRecord ||
                state.stage == TrackingStage.Arrived || activeSegmentId != segmentId ||
                state.currentSegment?.status != RouteSegmentStatus.NOT_STARTED) return

            val next = state.copy(
                stage = TrackingStage.Boarded,
                routeSegments = state.routeSegments.map { segment ->
                    if (segment.trackingSegmentId() == segmentId &&
                        segment.status == RouteSegmentStatus.NOT_STARTED
                    ) {
                        segment.copy(
                            actualStartedAtEpochMillis = now,
                            actualEndedAtEpochMillis = null,
                            actualDurationMinutes = null,
                            status = RouteSegmentStatus.IN_PROGRESS,
                        )
                    } else {
                        segment
                    }
                },
                errorMessage = null,
            )
        commitMeasurement(next, startedAtEpochMillis ?: now)
    }

    fun onSegmentComplete(segmentId: Long) {
        val now = nowProvider().toInstant().toEpochMilli()
        val state = _uiState.value
        if (state.isLoading || !state.canRecord || state.isSavingSession || state.isSavingRecord ||
            state.stage == TrackingStage.Arrived || state.currentSegment?.trackingSegmentId() != segmentId) return
        val segments = state.routeSegments.completeSegmentAndMaybeStartNext(segmentId, now)
        if (segments == state.routeSegments) return
        commitMeasurement(state.copy(
                routeSegments = segments,
                errorMessage = null,
            ), startedAtEpochMillis ?: now)
    }

    private fun saveCompletedRecord() {
        val state = _uiState.value
        val recommendation = state.recommendation ?: return
        if (state.isSavingRecord || state.completedRecord != null) return

        val arrivedAtEpochMillis = nowProvider().toInstant().toEpochMilli()
        val finalizedSegments = state.routeSegments.finalizeSkippedSegments(arrivedAtEpochMillis)
        val record = recommendation.toCommuteRecord(
            startedAtEpochMillis = startedAtEpochMillis ?: arrivedAtEpochMillis,
            arrivedAtEpochMillis = arrivedAtEpochMillis,
            routeSegments = finalizedSegments,
        )

        _uiState.update {
                it.copy(
                    isSavingRecord = true,
                    errorMessage = null,
                )
        }

        viewModelScope.launch {
            val saveResult = runCatchingCancellable {
                val existing = commuteRecordRepository.getRecentRecords(50, routine.id)
                    .firstOrNull { it.targetArrivalAtEpochMillis == record.targetArrivalAtEpochMillis }
                if (existing != null) return@runCatchingCancellable existing
                val recordId = commuteRecordRepository.saveRecord(record)
                runCatchingCancellable { commuteRecordRepository.getRecord(recordId) }.getOrNull()
                    ?: record.copy(id = recordId)
            }

            saveResult.onSuccess { savedRecord ->
                runCatchingCancellable { routine.id?.let { trackingSessionStore.clear(it) } }
                val adjustedPersonalBufferMinutes = runCatchingCancellable {
                    val recentRecords = commuteRecordRepository.getRecentRecords(
                        limit = PersonalBufferOptimizer.RECENT_RECORD_LIMIT,
                        routineId = savedRecord.routineId,
                    ).ifEmpty { listOf(savedRecord) }
                    val recentArrivalDeltaMinutes = recentRecords
                        .filter { it.arrivedAtEpochMillis - it.startedAtEpochMillis >= 60_000L }
                        .map { it.arrivalDeltaMinutes }
                    updateRoutinePersonalBuffer(recentArrivalDeltaMinutes)
                }.getOrNull()

                _uiState.update {
                    it.copy(
                        stage = TrackingStage.Arrived,
                        isSavingRecord = false,
                        completedRecord = savedRecord,
                        adjustedPersonalBufferMinutes = adjustedPersonalBufferMinutes,
                        routeSegments = finalizedSegments,
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        isSavingRecord = false,
                        errorMessage = "기록 저장에 실패했습니다. 다시 시도해 주세요.",
                    )
                }
            }
        }
    }

    companion object {
        fun factory(
            routine: Routine,
            routeEstimateProvider: RouteEstimateProvider,
            commuteRecordRepository: CommuteRecordRepository,
            routineRepository: RoutineRepository,
            settingsRepository: SettingsRepository,
            trackingSessionStore: TrackingSessionStore,
            scheduledRouteProvider: ScheduledRouteProvider? = null,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TrackingViewModel::class.java)) {
                        return TrackingViewModel(
                            routine = routine,
                            routeEstimateProvider = routeEstimateProvider,
                            commuteRecordRepository = commuteRecordRepository,
                            routineRepository = routineRepository,
                            settingsRepository = settingsRepository,
                            trackingSessionStore = trackingSessionStore,
                            scheduledRouteProvider = scheduledRouteProvider,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }

    private suspend fun updateRoutinePersonalBuffer(recentArrivalDeltaMinutes: List<Int>): Int? {
        val routineId = routine.id ?: return null
        val current = routineRepository.observeRoutines().first().firstOrNull { it.id == routineId } ?: return null
        if (current.origin != routine.origin || current.destination != routine.destination ||
            current.transportMode != routine.transportMode || current.targetArrivalTime != routine.targetArrivalTime
        ) return null
        val updatedPersonalBufferMinutes = personalBufferOptimizer.optimize(
            currentPersonalBufferMinutes = current.personalBufferMinutes,
            recentArrivalDeltaMinutes = recentArrivalDeltaMinutes,
        )
        if (current.personalBufferMinutes == updatedPersonalBufferMinutes) return updatedPersonalBufferMinutes
        return updatedPersonalBufferMinutes.takeIf {
            routineRepository.updatePersonalBufferMinutes(current, it)
        }
    }
}

private fun List<RouteSegment>.nextActionableSegmentId(): Long? {
    return (firstOrNull { it.status == RouteSegmentStatus.IN_PROGRESS }
        ?: firstOrNull { it.status == RouteSegmentStatus.NOT_STARTED })?.trackingSegmentId()
}

private fun List<RouteSegment>.completeSegmentAndMaybeStartNext(
    segmentId: Long,
    nowEpochMillis: Long,
): List<RouteSegment> {
    val completedIndex = indexOfFirst {
        it.trackingSegmentId() == segmentId &&
            it.status == RouteSegmentStatus.IN_PROGRESS
    }
    if (completedIndex < 0) return this

    val completedSegment = this[completedIndex]
    val shouldAutoStartNextRide = completedSegment.segmentType == RouteSegmentType.WAIT_FOR_BUS ||
        completedSegment.segmentType == RouteSegmentType.WAIT_FOR_SUBWAY
    return mapIndexed { index, segment ->
        when {
            index == completedIndex -> {
                val startedAt = segment.actualStartedAtEpochMillis ?: nowEpochMillis
                segment.copy(
                    actualStartedAtEpochMillis = startedAt,
                    actualEndedAtEpochMillis = nowEpochMillis,
                    actualDurationMinutes = elapsedMinutes(startedAt, nowEpochMillis),
                    status = RouteSegmentStatus.COMPLETED,
                )
            }
            shouldAutoStartNextRide &&
                index == completedIndex + 1 &&
                segment.status == RouteSegmentStatus.NOT_STARTED &&
                completedSegment.canAutoStart(segment) -> {
                segment.copy(
                    actualStartedAtEpochMillis = nowEpochMillis,
                    actualEndedAtEpochMillis = null,
                    actualDurationMinutes = null,
                    status = RouteSegmentStatus.IN_PROGRESS,
                )
            }
            else -> segment
        }
    }
}

private fun RouteSegment.canAutoStart(nextSegment: RouteSegment): Boolean {
    return when (segmentType) {
        RouteSegmentType.WAIT_FOR_BUS -> nextSegment.segmentType == RouteSegmentType.BUS_RIDE
        RouteSegmentType.WAIT_FOR_SUBWAY -> nextSegment.segmentType == RouteSegmentType.SUBWAY_RIDE
        else -> false
    } &&
        routeName.normalizedAutoStartKey() == nextSegment.routeName.normalizedAutoStartKey() &&
        startName.normalizedAutoStartKey() == nextSegment.startName.normalizedAutoStartKey()
}

private fun String?.normalizedAutoStartKey(): String? {
    return this?.trim()?.lowercase()?.takeIf(String::isNotBlank)
}

private fun List<RouteSegment>.finalizeSkippedSegments(arrivedAtEpochMillis: Long): List<RouteSegment> {
    return map { segment ->
        when (segment.status) {
            RouteSegmentStatus.COMPLETED -> segment
            RouteSegmentStatus.IN_PROGRESS -> {
                val startedAt = segment.actualStartedAtEpochMillis ?: arrivedAtEpochMillis
                segment.copy(
                    actualStartedAtEpochMillis = startedAt,
                    actualEndedAtEpochMillis = arrivedAtEpochMillis,
                    actualDurationMinutes = elapsedMinutes(startedAt, arrivedAtEpochMillis),
                    status = RouteSegmentStatus.COMPLETED,
                )
            }
            RouteSegmentStatus.NOT_STARTED -> segment.copy(
                actualDurationMinutes = null,
                status = RouteSegmentStatus.SKIPPED,
            )
            RouteSegmentStatus.SKIPPED -> segment
        }
    }
}

private fun elapsedMinutes(
    startedAtEpochMillis: Long,
    endedAtEpochMillis: Long,
): Int {
    return Duration.between(
        Instant.ofEpochMilli(startedAtEpochMillis),
        Instant.ofEpochMilli(endedAtEpochMillis),
    ).toMinutes().toInt().coerceAtLeast(0)
}

private val trackingTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun RoutineRecommendationUiModel.toCommuteRecord(
    startedAtEpochMillis: Long,
    arrivedAtEpochMillis: Long,
    routeSegments: List<RouteSegment>,
): CommuteRecord {
    return CommuteRecord(
        routineId = routine.id,
        routineName = routine.name,
        originName = routine.origin.name,
        destinationName = routine.destination.name,
        transportMode = routine.transportMode,
        targetArrivalTime = routine.targetArrivalTime,
        targetArrivalAtEpochMillis = targetArrivalAtEpochMillis,
        recommendedDepartureTime = recommendedDepartureTimeText.toLocalTimeOrDefault(
            defaultValue = routine.targetArrivalTime.minusMinutes(
                (routeDurationMinutes + personalBufferMinutes + safetyMarginMinutes).toLong(),
            ),
        ),
        routeDurationMinutes = routeDurationMinutes,
        routeSummary = routeSummary,
        startedAtEpochMillis = startedAtEpochMillis,
        arrivedAtEpochMillis = arrivedAtEpochMillis,
        arrivalDeltaMinutes = targetArrivalAtEpochMillis?.let { targetAt ->
            Duration.between(Instant.ofEpochMilli(targetAt), Instant.ofEpochMilli(arrivedAtEpochMillis)).toMinutes().toInt()
        } ?: routine.targetArrivalTime.arrivalDeltaMinutes(arrivedAtEpochMillis),
        routeSegments = routeSegments.map {
            it.copy(routineId = routine.id)
        },
    )
}

private fun String.toLocalTimeOrDefault(defaultValue: LocalTime): LocalTime {
    return runCatching {
        LocalTime.parse(this, trackingTimeFormatter)
    }.getOrDefault(defaultValue)
}

private fun LocalTime.arrivalDeltaMinutes(arrivedAtEpochMillis: Long): Int {
    val arrivedTime = Instant.ofEpochMilli(arrivedAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    val rawDelta = Duration.between(this, arrivedTime).toMinutes().toInt()
    return when {
        rawDelta > 12 * 60 -> rawDelta - 24 * 60
        rawDelta < -12 * 60 -> rawDelta + 24 * 60
        else -> rawDelta
    }
}
