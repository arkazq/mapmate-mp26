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
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.ScheduleAwareRecommendationResolver
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
import kotlinx.coroutines.launch

class TrackingViewModel(
    private val routine: Routine,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val commuteRecordRepository: CommuteRecordRepository,
    private val routineRepository: RoutineRepository,
    private val settingsRepository: SettingsRepository,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy = DepartureAdjustmentPolicy(),
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.systemDefault()) },
    private val personalBufferOptimizer: PersonalBufferOptimizer = PersonalBufferOptimizer(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackingUiState(routine = routine))
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()
    private var startedAtEpochMillis: Long? = null
    private val recommendationResolver = ScheduleAwareRecommendationResolver(
        routeEstimateProvider = routeEstimateProvider,
        departureTimeCalculator = departureTimeCalculator,
        alarmPlanner = alarmPlanner,
        adjustmentPolicy = adjustmentPolicy,
    )

    init {
        loadTrackingSummary()
    }

    fun onPrimaryActionClick() {
        val state = _uiState.value
        if (state.hasRouteSegments && state.stage != TrackingStage.Arrived) {
            saveCompletedRecord()
            return
        }

        when (state.stage) {
            TrackingStage.Planned -> {
                startedAtEpochMillis = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        stage = TrackingStage.Boarded,
                        errorMessage = null,
                    )
                }
            }

            TrackingStage.Boarded -> saveCompletedRecord()
            TrackingStage.Arrived -> Unit
        }
    }

    private fun loadTrackingSummary() {
        viewModelScope.launch {
            val now = nowProvider()
            val recommendation = runCatching {
                recommendationResolver.resolve(
                    routine = routine,
                    now = now,
                ).recommendation
            }.getOrElse {
                recommendationResolver.fallback(
                    routine = routine,
                    now = now,
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
                )
            }
        }
    }

    fun onSegmentStart(segmentId: Long) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            val activeSegmentId = state.routeSegments.nextActionableSegmentId()
            if (state.stage == TrackingStage.Arrived || activeSegmentId != segmentId) return@update state

            if (startedAtEpochMillis == null) {
                startedAtEpochMillis = now
            }

            state.copy(
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
        }
    }

    fun onSegmentComplete(segmentId: Long) {
        val now = System.currentTimeMillis()
        _uiState.update { state ->
            if (state.stage == TrackingStage.Arrived) return@update state

            state.copy(
                routeSegments = state.routeSegments.completeSegmentAndMaybeStartNext(segmentId, now),
                errorMessage = null,
            )
        }
    }

    private fun saveCompletedRecord() {
        val state = _uiState.value
        val recommendation = state.recommendation ?: return
        if (state.isSavingRecord) return

        val arrivedAtEpochMillis = System.currentTimeMillis()
        val finalizedSegments = state.routeSegments.finalizeSkippedSegments(arrivedAtEpochMillis)
        val record = recommendation.toCommuteRecord(
            startedAtEpochMillis = startedAtEpochMillis ?: arrivedAtEpochMillis,
            arrivedAtEpochMillis = arrivedAtEpochMillis,
            routeSegments = finalizedSegments,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingRecord = true,
                    errorMessage = null,
                )
            }

            val saveResult = runCatching {
                val recordId = commuteRecordRepository.saveRecord(record)
                commuteRecordRepository.getRecord(recordId) ?: record.copy(id = recordId)
            }

            saveResult.onSuccess { savedRecord ->
                val adjustedPersonalBufferMinutes = runCatching {
                    val recentRecords = commuteRecordRepository.getRecentRecords(
                        limit = PersonalBufferOptimizer.RECENT_RECORD_LIMIT,
                        routineId = savedRecord.routineId,
                    ).ifEmpty { listOf(savedRecord) }
                    val recentArrivalDeltaMinutes = recentRecords.map { it.arrivalDeltaMinutes }
                    val updatedPersonalBufferMinutes = settingsRepository.updatePersonalBufferForRecentArrivalDeltas(
                        recentArrivalDeltaMinutes = recentArrivalDeltaMinutes,
                    )
                    updateRoutinePersonalBuffer(recentArrivalDeltaMinutes) ?: updatedPersonalBufferMinutes
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
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }

    private suspend fun updateRoutinePersonalBuffer(recentArrivalDeltaMinutes: List<Int>): Int? {
        val routineId = routine.id ?: return null
        val updatedPersonalBufferMinutes = personalBufferOptimizer.optimize(
            currentPersonalBufferMinutes = routine.personalBufferMinutes,
            recentArrivalDeltaMinutes = recentArrivalDeltaMinutes,
        )
        if (routine.personalBufferMinutes == updatedPersonalBufferMinutes) return updatedPersonalBufferMinutes

        routineRepository.saveRoutine(
            routine.copy(
                id = routineId,
                personalBufferMinutes = updatedPersonalBufferMinutes,
            ),
        )
        return updatedPersonalBufferMinutes
    }
}

private fun List<RouteSegment>.nextActionableSegmentId(): Long? {
    return firstOrNull {
        it.status == RouteSegmentStatus.NOT_STARTED ||
            it.status == RouteSegmentStatus.IN_PROGRESS
    }?.trackingSegmentId()
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
                actualDurationMinutes = segment.plannedDurationMinutes,
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

private fun RoutineRecommendationUiModel.toCommuteRecord(
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
        arrivalDeltaMinutes = routine.targetArrivalTime.arrivalDeltaMinutes(arrivedAtEpochMillis),
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
