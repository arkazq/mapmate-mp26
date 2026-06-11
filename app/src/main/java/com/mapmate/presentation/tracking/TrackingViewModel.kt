package com.mapmate.presentation.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toRecommendationUiModel
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrackingUiState(routine = routine))
    val uiState: StateFlow<TrackingUiState> = _uiState.asStateFlow()
    private var startedAtEpochMillis: Long? = null

    init {
        loadTrackingSummary()
    }

    fun onPrimaryActionClick() {
        when (_uiState.value.stage) {
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
            val recommendation = runCatching {
                val routeEstimate = routeEstimateProvider.getRouteEstimate(
                    origin = routine.origin,
                    destination = routine.destination,
                    transportMode = routine.transportMode,
                )
                routine.toRecommendationUiModel(
                    routeEstimate = routeEstimate,
                    departureTimeCalculator = departureTimeCalculator,
                )
            }.getOrElse {
                routine.toFallbackRecommendationUiModel(departureTimeCalculator)
            }

            _uiState.update {
                it.copy(
                    recommendation = recommendation,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun saveCompletedRecord() {
        val state = _uiState.value
        val recommendation = state.recommendation ?: return
        if (state.isSavingRecord) return

        val arrivedAtEpochMillis = System.currentTimeMillis()
        val record = recommendation.toCommuteRecord(
            startedAtEpochMillis = startedAtEpochMillis ?: arrivedAtEpochMillis,
            arrivedAtEpochMillis = arrivedAtEpochMillis,
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingRecord = true,
                    errorMessage = null,
                )
            }

            runCatching {
                val recordId = commuteRecordRepository.saveRecord(record)
                record.copy(id = recordId)
            }.onSuccess { savedRecord ->
                _uiState.update {
                    it.copy(
                        stage = TrackingStage.Arrived,
                        isSavingRecord = false,
                        completedRecord = savedRecord,
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
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(TrackingViewModel::class.java)) {
                        return TrackingViewModel(
                            routine = routine,
                            routeEstimateProvider = routeEstimateProvider,
                            commuteRecordRepository = commuteRecordRepository,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

private val trackingTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun RoutineRecommendationUiModel.toCommuteRecord(
    startedAtEpochMillis: Long,
    arrivedAtEpochMillis: Long,
): CommuteRecord {
    return CommuteRecord(
        routineId = routine.id,
        routineName = routine.name,
        originName = routine.origin.name,
        destinationName = routine.destination.name,
        transportMode = routine.transportMode,
        targetArrivalTime = routine.targetArrivalTime,
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
