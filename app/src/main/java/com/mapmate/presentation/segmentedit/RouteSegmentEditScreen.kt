package com.mapmate.presentation.segmentedit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.TimeInput
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.presentation.common.DetailTopBar
import com.mapmate.presentation.common.IconBadge
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.MetricRow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun RouteSegmentEditRoute(
    contentPadding: PaddingValues,
    record: CommuteRecord,
    commuteRecordRepository: CommuteRecordRepository,
    onBackClick: () -> Unit,
    onSaveCompleted: (CommuteRecord) -> Unit,
) {
    val viewModel: RouteSegmentEditViewModel = viewModel(
        key = "segment-edit-${record.id ?: record.arrivedAtEpochMillis}",
        factory = RouteSegmentEditViewModel.factory(
            record = record,
            commuteRecordRepository = commuteRecordRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedRecord) {
        uiState.savedRecord?.let(onSaveCompleted)
    }

    RouteSegmentEditScreen(
        uiState = uiState,
        contentPadding = contentPadding,
        onBackClick = onBackClick,
        onStartTimeSelected = viewModel::onStartDateTimeSelected,
        onEndTimeSelected = viewModel::onEndDateTimeSelected,
        onSaveClick = viewModel::onSaveClick,
    )
}

@Composable
fun RouteSegmentEditScreen(
    uiState: RouteSegmentEditUiState,
    contentPadding: PaddingValues,
    onBackClick: () -> Unit,
    onStartTimeSelected: (Long, Long) -> Unit,
    onEndTimeSelected: (Long, Long) -> Unit,
    onSaveClick: () -> Unit,
) {
    var pickerTarget by remember { mutableStateOf<TimePickerTarget?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    fun requestBack() {
        if (uiState.isSaving) return
        if (uiState.hasUnsavedChanges) showDiscardDialog = true else onBackClick()
    }
    BackHandler { requestBack() }
    if (showDiscardDialog) AlertDialog(
        onDismissRequest = { showDiscardDialog = false },
        title = { Text("시간 수정을 버릴까요?") },
        confirmButton = { TextButton(onClick = { showDiscardDialog = false; onBackClick() }) { Text("버리기") } },
        dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("계속 수정") } },
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            horizontal = MapMateSpacing.ScreenHorizontal,
            vertical = MapMateSpacing.ScreenTop,
        ),
        verticalArrangement = Arrangement.spacedBy(MapMateSpacing.Section),
    ) {
        item {
            DetailTopBar(
                title = "구간별 시간 수정",
                onBackClick = ::requestBack,
            )
        }

        when {
            uiState.isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            !uiState.canEdit -> {
                item {
                    Text("수정할 구간 기록이 없습니다.")
                }
            }

            else -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiState.record.routineName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${uiState.record.originName} → ${uiState.record.destinationName}", style = MaterialTheme.typography.bodyMedium)
                        MetricRow(
                            icon = MapMateIconType.Time,
                            label = if (uiState.hasUnsavedChanges) "수정 전 전체 이동" else "전체 실제 이동",
                            value = "${uiState.record.totalActualDurationMinutes()}분",
                        )
                        MetricRow(
                            icon = MapMateIconType.Route,
                            label = "구간 수",
                            value = "${uiState.segments.size}개",
                        )
                    }
                }

                items(
                    items = uiState.segments,
                    key = { it.editSegmentId() },
                ) { segment ->
                    EditableRouteSegmentCard(
                        segment = segment,
                        onStartClick = { pickerTarget = segment.startPickerTarget(uiState.record) },
                        onEndClick = { pickerTarget = segment.endPickerTarget(uiState.record) },
                        enabled = !uiState.isSaving,
                    )
                }

                item {
                    Button(
                        onClick = onSaveClick,
                        enabled = !uiState.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = if (uiState.isSaving) "저장 중" else "수정 저장",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }

        uiState.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    pickerTarget?.let { target ->
        SegmentTimePickerDialog(
            target = target,
            onDismiss = { pickerTarget = null },
            onConfirm = { segmentId, isStart, epochMillis ->
                if (isStart) {
                    onStartTimeSelected(segmentId, epochMillis)
                } else {
                    onEndTimeSelected(segmentId, epochMillis)
                }
                pickerTarget = null
            },
        )
    }
}

@Composable
private fun EditableRouteSegmentCard(
    segment: RouteSegment,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                IconBadge(
                    icon = segment.segmentIcon(),
                    modifier = Modifier.size(30.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = segment.displayTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = listOfNotNull(
                            segment.startName?.takeIf(String::isNotBlank),
                            segment.endName?.takeIf(String::isNotBlank),
                        ).joinToString(" -> ").ifBlank { "구간 정보 없음" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(segment.status.toEditingLabel(), style = MaterialTheme.typography.labelMedium)
            }

            MetricRow(label = "노선", value = segment.routeName ?: "-")
            MetricRow(label = "예상 소요시간", value = "${segment.plannedDurationMinutes}분")
            MetricRow(label = "실제 소요시간", value = segment.actualDurationMinutes?.let { "${it}분" } ?: "-")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onStartClick,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "${segment.segmentIndex + 1}구간 시작 시각 수정" },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("시작 ${segment.actualStartedAtEpochMillis.toTimeTextOrInput()}")
                }
                OutlinedButton(
                    onClick = onEndClick,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "${segment.segmentIndex + 1}구간 종료 시각 수정" },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("종료 ${segment.actualEndedAtEpochMillis.toTimeTextOrInput()}")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentTimePickerDialog(
    target: TimePickerTarget,
    onDismiss: () -> Unit,
    onConfirm: (Long, Boolean, Long) -> Unit,
) {
    val initialTime = target.initialEpochMillis.toLocalHourMinute()
    var selectedDate by remember(target) { mutableStateOf(Instant.ofEpochMilli(target.initialEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val compactPicker = LocalDensity.current.fontScale >= 1.5f || LocalConfiguration.current.screenHeightDp < 600
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.first,
        initialMinute = initialTime.second,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (target.isStart) "시작 시각 선택" else "종료 시각 선택")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(selectedDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                }
                if (compactPicker) TimeInput(state = timePickerState) else TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        target.segmentId,
                        target.isStart,
                        selectedDate.atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    )
                },
            ) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1970..2100, initialDisplayMode = if (compactPicker) DisplayMode.Input else DisplayMode.Picker)
        DatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                dateState.selectedDateMillis?.let { selectedDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                showDatePicker = false
            }, enabled = dateState.selectedDateMillis != null) { Text("날짜 선택") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("취소") } }) {
            DatePicker(state = dateState)
        }
    }
}

private fun RouteSegmentStatus.toEditingLabel(): String = when (this) {
    RouteSegmentStatus.NOT_STARTED -> "시작 전"
    RouteSegmentStatus.IN_PROGRESS -> "측정 중"
    RouteSegmentStatus.COMPLETED -> "완료"
    RouteSegmentStatus.SKIPPED -> "건너뜀"
}

private data class TimePickerTarget(
    val segmentId: Long,
    val isStart: Boolean,
    val initialEpochMillis: Long,
)

private fun RouteSegment.startPickerTarget(record: CommuteRecord): TimePickerTarget {
    return TimePickerTarget(
        segmentId = editSegmentId(),
        isStart = true,
        initialEpochMillis = actualStartedAtEpochMillis ?: record.startedAtEpochMillis,
    )
}

private fun RouteSegment.endPickerTarget(record: CommuteRecord): TimePickerTarget {
    return TimePickerTarget(
        segmentId = editSegmentId(),
        isStart = false,
        initialEpochMillis = actualEndedAtEpochMillis
            ?: actualStartedAtEpochMillis
            ?: record.arrivedAtEpochMillis,
    )
}

private fun RouteSegment.displayTitle(): String {
    return when (segmentType) {
        RouteSegmentType.WALK_TO_TRANSIT -> "\uC815\uB958\uC7A5/\uC5ED\uAE4C\uC9C0 \uB3C4\uBCF4"
        RouteSegmentType.WAIT_FOR_BUS -> listOfNotNull(routeName?.takeIf(String::isNotBlank), "\uBC84\uC2A4 \uB300\uAE30")
            .joinToString(" ")
        RouteSegmentType.BUS_RIDE -> listOfNotNull(routeName?.takeIf(String::isNotBlank), "\uBC84\uC2A4 \uD0D1\uC2B9")
            .joinToString(" ")
        RouteSegmentType.WAIT_FOR_SUBWAY -> listOfNotNull(routeName?.takeIf(String::isNotBlank), "\uC9C0\uD558\uCCA0 \uB300\uAE30")
            .joinToString(" ")
        RouteSegmentType.SUBWAY_RIDE -> listOfNotNull(routeName?.takeIf(String::isNotBlank), "\uC9C0\uD558\uCCA0 \uD0D1\uC2B9")
            .joinToString(" ")
        RouteSegmentType.TRANSFER_WALK -> "\uD658\uC2B9 \uC774\uB3D9"
        RouteSegmentType.WALK_TO_DESTINATION -> "\uBAA9\uC801\uC9C0\uAE4C\uC9C0 \uB3C4\uBCF4"
        RouteSegmentType.UNKNOWN -> "\uC774\uB3D9 \uAD6C\uAC04"
    }
}

private fun RouteSegment.segmentIcon(): MapMateIconType {
    return when (segmentType) {
        RouteSegmentType.WALK_TO_TRANSIT,
        RouteSegmentType.TRANSFER_WALK,
        RouteSegmentType.WALK_TO_DESTINATION,
        -> MapMateIconType.Walk
        RouteSegmentType.WAIT_FOR_BUS,
        RouteSegmentType.BUS_RIDE,
        RouteSegmentType.WAIT_FOR_SUBWAY,
        RouteSegmentType.SUBWAY_RIDE,
        -> MapMateIconType.Bus
        RouteSegmentType.UNKNOWN -> MapMateIconType.Route
    }
}

private fun CommuteRecord.totalActualDurationMinutes(): Int {
    return java.time.Duration.between(
        Instant.ofEpochMilli(startedAtEpochMillis),
        Instant.ofEpochMilli(arrivedAtEpochMillis),
    ).toMinutes().toInt().coerceAtLeast(0)
}

private fun Long?.toTimeTextOrInput(): String {
    return this?.toTimeText() ?: "입력"
}

private fun Long.toTimeText(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}

private fun Long.toLocalHourMinute(): Pair<Int, Int> {
    val time = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
    return time.hour to time.minute
}
