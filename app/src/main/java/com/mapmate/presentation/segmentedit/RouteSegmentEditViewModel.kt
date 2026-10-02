package com.mapmate.presentation.segmentedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.hasChronologicalMeasuredSegments
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.util.runCatchingCancellable
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RouteSegmentEditViewModel(
    private val initialRecord: CommuteRecord,
    private val commuteRecordRepository: CommuteRecordRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RouteSegmentEditUiState(
            record = initialRecord,
            segments = initialRecord.routeSegments.sortedBy { it.segmentIndex },
            isLoading = initialRecord.routeSegments.isEmpty() && initialRecord.id != null,
        ),
    )
    val uiState: StateFlow<RouteSegmentEditUiState> = _uiState.asStateFlow()
    private var originalSegments = initialRecord.routeSegments.associateBy { it.editSegmentId() }

    init {
        loadRecordIfNeeded()
    }

    fun onStartTimeSelected(
        segmentId: Long,
        hour: Int,
        minute: Int,
    ) {
        updateSegmentTime(
            segmentId = segmentId,
            isStart = true,
            hour = hour,
            minute = minute,
        )
    }

    fun onEndTimeSelected(
        segmentId: Long,
        hour: Int,
        minute: Int,
    ) {
        updateSegmentTime(
            segmentId = segmentId,
            isStart = false,
            hour = hour,
            minute = minute,
        )
    }

    fun onStartDateTimeSelected(segmentId: Long, epochMillis: Long) = updateSelectedEpoch(segmentId, true, epochMillis)
    fun onEndDateTimeSelected(segmentId: Long, epochMillis: Long) = updateSelectedEpoch(segmentId, false, epochMillis)

    private fun updateSelectedEpoch(segmentId: Long, isStart: Boolean, epochMillis: Long) {
        if (_uiState.value.isSaving) return
        if (epochMillis < 0) {
            _uiState.update { it.copy(errorMessage = "올바른 날짜와 시각을 선택해 주세요.") }
            return
        }
        _uiState.update { state ->
            state.copy(segments = state.segments.map { segment ->
                if (segment.editSegmentId() != segmentId) segment else {
                    val changed = if (isStart) segment.copy(actualStartedAtEpochMillis = epochMillis)
                        else segment.copy(actualEndedAtEpochMillis = epochMillis)
                    changed.withDraftDuration()
                }
            }, errorMessage = null)
        }
    }

    fun onSaveClick() {
        val state = _uiState.value
        if (state.isSaving) return
        val validationError = state.segments.firstNotNullOfOrNull { it.validationError() }
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        if (!state.segments.hasChronologicalMeasuredSegments()) {
            _uiState.update { it.copy(errorMessage = "구간 시간이 겹칩니다. 이전 구간 종료 후에 다음 구간이 시작해야 합니다.") }
            return
        }

        val updatedSegments = state.segments.filter { segment ->
            val original = originalSegments[segment.editSegmentId()]
            segment.actualStartedAtEpochMillis != original?.actualStartedAtEpochMillis ||
                segment.actualEndedAtEpochMillis != original?.actualEndedAtEpochMillis
        }.map { it.toSavedSegment() }
        val recordId = state.record.id
        if (updatedSegments.isEmpty()) {
            _uiState.update { it.copy(savedRecord = state.record) }
            return
        }
        if (recordId == null) {
            _uiState.update { it.copy(errorMessage = "저장된 이동 기록이 없습니다.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            val saveResult = runCatchingCancellable {
                commuteRecordRepository.updateRouteSegments(recordId, updatedSegments)
            }

            saveResult.onSuccess { savedRecord ->
                originalSegments = savedRecord.routeSegments.associateBy { it.editSegmentId() }
                _uiState.update {
                    it.copy(
                        record = savedRecord,
                        segments = savedRecord.routeSegments.sortedBy { segment -> segment.segmentIndex },
                        isSaving = false,
                        savedRecord = savedRecord,
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        errorMessage = "구간 시간을 저장하지 못했습니다. 다시 시도해 주세요.",
                    )
                }
            }
        }
    }

    private fun loadRecordIfNeeded() {
        val recordId = initialRecord.id ?: return
        if (initialRecord.routeSegments.isNotEmpty()) return

        viewModelScope.launch {
            val loadedRecord = runCatchingCancellable {
                commuteRecordRepository.getRecord(recordId)
            }.getOrNull()

            _uiState.update { state ->
                val record = loadedRecord ?: state.record
                originalSegments = record.routeSegments.associateBy { it.editSegmentId() }
                state.copy(
                    record = record,
                    segments = record.routeSegments.sortedBy { it.segmentIndex },
                    isLoading = false,
                    errorMessage = if (record.routeSegments.isEmpty()) {
                        "수정할 구간 기록이 없습니다."
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun updateSegmentTime(
        segmentId: Long,
        isStart: Boolean,
        hour: Int,
        minute: Int,
    ) {
        if (_uiState.value.isSaving) return
        if (hour !in 0..23 || minute !in 0..59) {
            _uiState.update { it.copy(errorMessage = "올바른 시각을 선택해 주세요.") }
            return
        }
        _uiState.update { state ->
            state.copy(
                segments = state.segments.map { segment ->
                    if (segment.editSegmentId() != segmentId) return@map segment
                    val baseEpochMillis = if (isStart) {
                        segment.actualStartedAtEpochMillis ?: state.record.startedAtEpochMillis
                    } else {
                        segment.actualEndedAtEpochMillis
                            ?: segment.actualStartedAtEpochMillis
                            ?: state.record.arrivedAtEpochMillis
                    }
                    val selectedEpochMillis = baseEpochMillis.withTime(hour, minute)
                        .adjustOvernightEndIfNeeded(
                            record = state.record,
                            segment = segment,
                            isStart = isStart,
                        )
                    if (isStart) {
                        segment.copy(actualStartedAtEpochMillis = selectedEpochMillis).withDraftDuration()
                    } else {
                        segment.copy(actualEndedAtEpochMillis = selectedEpochMillis).withDraftDuration()
                    }
                },
                errorMessage = null,
            )
        }
    }

    companion object {
        fun factory(
            record: CommuteRecord,
            commuteRecordRepository: CommuteRecordRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(RouteSegmentEditViewModel::class.java)) {
                        return RouteSegmentEditViewModel(
                            initialRecord = record,
                            commuteRecordRepository = commuteRecordRepository,
                        ) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}

data class RouteSegmentEditUiState(
    val record: CommuteRecord,
    val segments: List<RouteSegment> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedRecord: CommuteRecord? = null,
) {
    val canEdit: Boolean
        get() = segments.isNotEmpty()
    val hasUnsavedChanges: Boolean
        get() = segments.any { segment ->
            val original = record.routeSegments.firstOrNull { it.editSegmentId() == segment.editSegmentId() }
            segment.actualStartedAtEpochMillis != original?.actualStartedAtEpochMillis ||
                segment.actualEndedAtEpochMillis != original?.actualEndedAtEpochMillis
        }
}

private fun RouteSegment.withDraftDuration(): RouteSegment {
    val start = actualStartedAtEpochMillis
    val end = actualEndedAtEpochMillis
    return copy(actualDurationMinutes = if (start != null && end != null && end >= start)
        ((end - start) / 60_000L).toInt() else null)
}

internal fun RouteSegment.editSegmentId(): Long {
    return id ?: segmentIndex.toLong()
}

private fun RouteSegment.validationError(): String? {
    val startedAt = actualStartedAtEpochMillis
    val endedAt = actualEndedAtEpochMillis
    if (startedAt == null && endedAt == null && status == RouteSegmentStatus.SKIPPED) return null
    if (startedAt == null || endedAt == null) return "시작 시각과 종료 시각을 모두 입력해 주세요."
    if (endedAt < startedAt) return "종료 시각은 시작 시각보다 빠를 수 없습니다."
    return null
}

private fun RouteSegment.toSavedSegment(): RouteSegment {
    val startedAt = actualStartedAtEpochMillis
    val endedAt = actualEndedAtEpochMillis
    if (startedAt == null && endedAt == null && status == RouteSegmentStatus.SKIPPED) return this
    if (startedAt == null || endedAt == null) return this

    return copy(
        actualDurationMinutes = Duration.between(
            Instant.ofEpochMilli(startedAt),
            Instant.ofEpochMilli(endedAt),
        ).toMinutes().toInt().coerceAtLeast(0),
        isUserEdited = true,
        status = RouteSegmentStatus.COMPLETED,
    )
}

private fun Long.withTime(
    hour: Int,
    minute: Int,
): Long {
    val zoneId = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(this)
        .atZone(zoneId)
        .toLocalDate()
        .atTime(hour, minute)
    return LocalDateTime.from(dateTime)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}

private fun Long.adjustOvernightEndIfNeeded(
    record: CommuteRecord,
    segment: RouteSegment,
    isStart: Boolean,
): Long {
    if (isStart) return this
    val startedAt = segment.actualStartedAtEpochMillis ?: return this
    if (!record.spansMultipleDates()) return this
    return if (this < startedAt) this + Duration.ofDays(1).toMillis() else this
}

private fun CommuteRecord.spansMultipleDates(): Boolean {
    val zoneId = ZoneId.systemDefault()
    val startedDate = Instant.ofEpochMilli(startedAtEpochMillis)
        .atZone(zoneId)
        .toLocalDate()
    val arrivedDate = Instant.ofEpochMilli(arrivedAtEpochMillis)
        .atZone(zoneId)
        .toLocalDate()
    return arrivedDate.isAfter(startedDate)
}
