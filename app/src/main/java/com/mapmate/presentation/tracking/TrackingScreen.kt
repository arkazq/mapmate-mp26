package com.mapmate.presentation.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.presentation.common.DetailTopBar
import com.mapmate.presentation.common.IconBadge
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.MetricRow
import com.mapmate.presentation.common.RouteEstimateStatusMessage
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.SectionCard
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toKoreanDescription
import com.mapmate.presentation.common.toKoreanLabel
import com.mapmate.presentation.common.transportModeIcon
import com.mapmate.presentation.common.RouteEndpoints
import com.mapmate.presentation.common.journeyColor
import com.mapmate.ui.theme.MapMateTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun TrackingRoute(
    contentPadding: PaddingValues,
    routine: Routine,
    routeEstimateProvider: RouteEstimateProvider,
    commuteRecordRepository: CommuteRecordRepository,
    routineRepository: RoutineRepository,
    settingsRepository: SettingsRepository,
    trackingSessionStore: TrackingSessionStore,
    onBackClick: () -> Unit,
    onCompleted: (CommuteRecord) -> Unit,
    scheduledRouteProvider: ScheduledRouteProvider? = null,
) {
    val viewModel: TrackingViewModel = viewModel(
        key = "tracking-${routine.id ?: routine.name}",
        factory = TrackingViewModel.factory(
            routine = routine,
            routeEstimateProvider = routeEstimateProvider,
            commuteRecordRepository = commuteRecordRepository,
            routineRepository = routineRepository,
            settingsRepository = settingsRepository,
            trackingSessionStore = trackingSessionStore,
            scheduledRouteProvider = scheduledRouteProvider,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.completedRecord) {
        uiState.completedRecord?.let {
            onCompleted(it)
        }
    }

    TrackingScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onPrimaryActionClick = viewModel::onPrimaryActionClick,
        onSegmentStart = viewModel::onSegmentStart,
        onSegmentComplete = viewModel::onSegmentComplete,
        onRetry = viewModel::retry,
        onDiscardSession = viewModel::discardSavedSession,
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun TrackingScreen(
    uiState: TrackingUiState,
    onBackClick: () -> Unit,
    onPrimaryActionClick: () -> Unit,
    onSegmentStart: (Long) -> Unit,
    onSegmentComplete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onDiscardSession: () -> Unit = {},
) {
    val recommendation = uiState.recommendation
    var showDiscardDialog by remember { mutableStateOf(false) }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("기존 측정을 지울까요?") },
            text = { Text("아직 완료하지 않은 측정 시간은 삭제됩니다.") },
            confirmButton = { TextButton(onClick = { showDiscardDialog = false; onDiscardSession() }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("취소") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MapMateSpacing.ScreenHorizontal,
            vertical = MapMateSpacing.ScreenTop,
        ),
        verticalArrangement = Arrangement.spacedBy(MapMateSpacing.Section),
    ) {
        item {
            DetailTopBar(
                title = "이동 기록",
                onBackClick = onBackClick,
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

            recommendation != null -> {
                item {
                    TrackingRoutineSummaryCard(recommendation = recommendation)
                }

                if (uiState.isRestoredSession) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("이전 측정을 이어서 기록합니다", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { showDiscardDialog = true },
                            enabled = !uiState.isSavingRecord && !uiState.isSavingSession,
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("측정 지우고 다시 시작") }
                    }
                }
                if (!uiState.canRecord) item {
                    Text("오늘 남은 이동이 없습니다", style = MaterialTheme.typography.titleMedium)
                    Text("다음 출발 ${recommendation.recommendedDepartureAtEpochMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
                    }.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                }
                if (uiState.hasRouteSegments && uiState.canRecord) {
                    item {
                        CurrentRouteSegmentCard(
                            uiState = uiState,
                            isSavingRecord = uiState.isSavingRecord || uiState.isSavingSession,
                            onSegmentStart = onSegmentStart,
                            onSegmentComplete = onSegmentComplete,
                        )
                    }
                }

                if (!uiState.isCompleted && !uiState.hasRouteSegments && uiState.canRecord) {
                    item { TrackingTimelineCard(recommendation = recommendation, stage = uiState.stage) }
                    item {
                        Button(
                            onClick = onPrimaryActionClick,
                            enabled = !uiState.isSavingRecord && !uiState.isSavingSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            MapMateIcon(
                                icon = transportModeIcon(recommendation.routine.transportMode),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = if (uiState.isSavingRecord) {
                                    "기록 저장 중"
                                } else {
                                    uiState.stage.primaryActionLabel(recommendation.routine.transportMode)
                                },
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }

                if (!uiState.isCompleted && uiState.hasRouteSegments && uiState.canRecord) {
                    item {
                        Button(
                            onClick = onPrimaryActionClick,
                            enabled = !uiState.isSavingRecord && !uiState.isSavingSession,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isAllSegmentsFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (uiState.isAllSegmentsFinished) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            ),
                            border = if (uiState.isAllSegmentsFinished) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            MapMateIcon(
                                icon = MapMateIconType.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (uiState.isAllSegmentsFinished) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = if (uiState.isSavingRecord) {
                                    "기록 저장 중"
                                } else {
                                    "도착 완료"
                                },
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
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

        if (uiState.isLoadError) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry, enabled = !uiState.isSavingSession) { Text("다시 시도") }
                TextButton(onClick = { showDiscardDialog = true }, enabled = !uiState.isSavingSession) { Text("이전 측정 지우기") }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TrackingRoutineSummaryCard(
    recommendation: RoutineRecommendationUiModel,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(recommendation.routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        RouteEndpoints(recommendation.routine)
        MetricRow(label = "추천 출발", value = recommendation.departureWithDateText())
        MetricRow(label = "목표 도착", value = recommendation.arrivalWithDateText())
    }
}

@Composable
private fun TrackingTimelineCard(
    recommendation: RoutineRecommendationUiModel,
    stage: TrackingStage,
) {
    SectionCard(
        title = stage.title,
        subtitle = stage.subtitle(recommendation.recommendedDepartureDisplayText),
        leadingIcon = MapMateIconType.Records,
    ) {
        Text("현재 단계", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StageRow(
            title = "출발 예정",
            description = if (recommendation.isImmediateDepartureRecommended) {
                "지금 출발을 권장해요."
            } else {
                "${recommendation.recommendedDepartureDisplayText} 출발 권장"
            },
            isActive = stage == TrackingStage.Planned,
            icon = MapMateIconType.Time,
        )
        StageRow(
            title = "탑승",
            description = "${recommendation.routine.transportMode.toKoreanLabel()} 이용을 시작하면 기록을 남겨주세요.",
            isActive = stage == TrackingStage.Boarded,
            icon = transportModeIcon(recommendation.routine.transportMode),
        )
        StageRow(
            title = "도착",
            description = "목적지에 도착하면 기록을 마무리합니다.",
            isActive = stage == TrackingStage.Arrived,
            icon = MapMateIconType.Flag,
        )
    }
}

@Composable
private fun StageRow(
    title: String,
    description: String,
    isActive: Boolean,
    icon: MapMateIconType,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(
            icon = icon,
            modifier = Modifier.size(30.dp),
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isActive) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CurrentMovementCard(
    recommendation: RoutineRecommendationUiModel,
) {
    SectionCard(
        title = "현재 이동 정보",
        leadingIcon = transportModeIcon(recommendation.routine.transportMode),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = transportModeIcon(recommendation.routine.transportMode),
                modifier = Modifier.size(34.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = recommendation.routine.transportMode.toKoreanLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = recommendation.routine.transportMode.toKoreanDescription(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetricRow(label = "현재 위치", value = "${recommendation.routine.origin.name} 근처")
                MetricRow(label = "예상 소요 시간", value = "${recommendation.routeDurationMinutes}분")
                RouteEstimateStatusMessage(message = recommendation.routeStatusMessage)
            }
        }
    }
}

@Composable
private fun CurrentRouteSegmentCard(
    uiState: TrackingUiState,
    isSavingRecord: Boolean,
    onSegmentStart: (Long) -> Unit,
    onSegmentComplete: (Long) -> Unit,
) {
    val currentSegment = uiState.currentSegment
    val totalSegmentCount = uiState.totalSegmentCount

    if (currentSegment == null) {
        SectionCard(
            title = "모든 구간 측정 완료",
            leadingIcon = MapMateIconType.Check,
        ) {
            MetricRow(
                icon = MapMateIconType.Route,
                label = "완료된 구간",
                value = "${uiState.completedSegmentCount} / $totalSegmentCount",
            )
            MetricRow(
                icon = MapMateIconType.Check,
                label = "처리된 구간",
                value = "${uiState.finishedSegmentCount} / $totalSegmentCount",
            )
        }
        return
    }

    var nowEpochMillis by remember(
        currentSegment.trackingSegmentId(),
        currentSegment.actualStartedAtEpochMillis,
        currentSegment.status,
    ) {
        mutableStateOf(System.currentTimeMillis())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(
        currentSegment.trackingSegmentId(),
        currentSegment.actualStartedAtEpochMillis,
        currentSegment.status,
        lifecycleOwner,
    ) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (currentSegment.status == RouteSegmentStatus.IN_PROGRESS) {
                nowEpochMillis = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            uiState.routeSegments.sortedBy { it.segmentIndex }.forEach { segment ->
                Box(Modifier.weight(1f).height(6.dp).background(
                    if (segment.status == RouteSegmentStatus.COMPLETED || segment == currentSegment) segment.journeyColor()
                    else MaterialTheme.colorScheme.outlineVariant,
                    MaterialTheme.shapes.small))
            }
        }
        Text("현재 구간 ${uiState.currentSegmentIndex} / $totalSegmentCount", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    IconBadge(
                        icon = currentSegment.segmentIcon(),
                        modifier = Modifier.size(38.dp),
                        containerColor = currentSegment.journeyColor().copy(alpha = 0.12f),
                        contentColor = currentSegment.journeyColor(),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = currentSegment.displayTitle(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = listOfNotNull(
                                currentSegment.startName?.takeIf(String::isNotBlank),
                                currentSegment.endName?.takeIf(String::isNotBlank),
                            ).joinToString(" → ").ifBlank { "구간 정보 없음" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (currentSegment.status == RouteSegmentStatus.IN_PROGRESS) "측정 중" else "시작 전",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Text("예상 ${currentSegment.plannedDurationMinutes}분", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (currentSegment.status == RouteSegmentStatus.IN_PROGRESS) {
                    Text(currentSegment.actualStartedAtEpochMillis?.elapsedTimeText(nowEpochMillis) ?: "00:00",
                        fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }

                Button(
                    onClick = {
                        when (currentSegment.status) {
                            RouteSegmentStatus.NOT_STARTED -> {
                                onSegmentStart(currentSegment.trackingSegmentId())
                            }
                            RouteSegmentStatus.IN_PROGRESS -> {
                                onSegmentComplete(currentSegment.trackingSegmentId())
                            }
                            RouteSegmentStatus.COMPLETED,
                            RouteSegmentStatus.SKIPPED,
                            -> Unit
                        }
                    },
                    enabled = !isSavingRecord &&
                        (
                            currentSegment.status == RouteSegmentStatus.NOT_STARTED ||
                                currentSegment.status == RouteSegmentStatus.IN_PROGRESS
                            ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = when (currentSegment.status) {
                            RouteSegmentStatus.NOT_STARTED -> currentSegment.startButtonLabel()
                            RouteSegmentStatus.IN_PROGRESS -> currentSegment.completeButtonLabel()
                            RouteSegmentStatus.COMPLETED,
                            RouteSegmentStatus.SKIPPED,
                            -> "완료"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        MetricRow(
            icon = MapMateIconType.Check,
            label = "완료된 구간",
            value = "${uiState.completedSegmentCount} / $totalSegmentCount",
        )
    }
}

private fun RouteSegment.displayTitle(): String {
    return when (segmentType) {
        RouteSegmentType.WALK_TO_TRANSIT -> "\uC815\uB958\uC7A5/\uC5ED\uAE4C\uC9C0 \uB3C4\uBCF4"
        RouteSegmentType.WAIT_FOR_BUS -> listOfNotNull(
            routeName?.takeIf(String::isNotBlank),
            "\uBC84\uC2A4 \uB300\uAE30",
        ).joinToString(" ")
        RouteSegmentType.BUS_RIDE -> listOfNotNull(
            routeName?.takeIf(String::isNotBlank),
            "\uBC84\uC2A4 \uD0D1\uC2B9",
        ).joinToString(" ")
        RouteSegmentType.WAIT_FOR_SUBWAY -> listOfNotNull(
            routeName?.takeIf(String::isNotBlank),
            "\uC9C0\uD558\uCCA0 \uB300\uAE30",
        ).joinToString(" ")
        RouteSegmentType.SUBWAY_RIDE -> listOfNotNull(
            routeName?.takeIf(String::isNotBlank),
            "\uC9C0\uD558\uCCA0 \uD0D1\uC2B9",
        ).joinToString(" ")
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
        -> MapMateIconType.Bus
        RouteSegmentType.WAIT_FOR_SUBWAY,
        RouteSegmentType.SUBWAY_RIDE,
        -> MapMateIconType.Train
        RouteSegmentType.UNKNOWN -> MapMateIconType.Route
    }
}

private fun RouteSegment.startButtonLabel(): String {
    return when (segmentType) {
        RouteSegmentType.WAIT_FOR_BUS,
        RouteSegmentType.WAIT_FOR_SUBWAY,
        -> "\uB300\uAE30 \uC2DC\uC791"
        RouteSegmentType.BUS_RIDE,
        RouteSegmentType.SUBWAY_RIDE,
        -> "\uD0D1\uC2B9"
        else -> "\uC2DC\uC791"
    }
}

private fun RouteSegment.completeButtonLabel(): String {
    return when (segmentType) {
        RouteSegmentType.WAIT_FOR_BUS,
        RouteSegmentType.WAIT_FOR_SUBWAY,
        -> "\uD0D1\uC2B9"
        RouteSegmentType.BUS_RIDE,
        RouteSegmentType.SUBWAY_RIDE,
        -> "\uD558\uCC28"
        else -> "\uC644\uB8CC"
    }
}

@Composable
fun TrackingCompletionScreen(
    contentPadding: PaddingValues,
    record: CommuteRecord,
    onBackClick: () -> Unit,
    onEditSegmentsClick: () -> Unit,
    onRecordsClick: () -> Unit,
    onHomeClick: () -> Unit,
) {
    val actualArrivalTime = record.arrivedAtEpochMillis.toLocalTimeText()
    val deltaText = record.arrivalDeltaMinutes.toDeltaText()

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
                title = "기록 완료",
                onBackClick = onBackClick,
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MapMateSpacing.CardInner),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconBadge(
                        icon = MapMateIconType.Check,
                        modifier = Modifier.size(72.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "오늘 기록이 저장되었어요!",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = record.arrivalDeltaMinutes.toDeltaSentence(),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = "저장된 내역은 기록 화면에서 확인할 수 있어요.",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(MapMateSpacing.CardInner),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompletionMetric(
                        label = "도착 시각",
                        value = actualArrivalTime,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        modifier = Modifier.size(width = 1.dp, height = 48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
                        content = {},
                    )
                    CompletionMetric(
                        label = "오차",
                        value = deltaText,
                        emphasized = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (record.routeSegments.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = onEditSegmentsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("구간별 시간 수정")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRecordsClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("기록 보기")
                }
                Button(
                    onClick = onHomeClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("홈으로 돌아가기")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CompletionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private val TrackingStage.title: String
    get() = when (this) {
        TrackingStage.Planned -> "출발 예정"
        TrackingStage.Boarded -> "탑승"
        TrackingStage.Arrived -> "도착"
    }

private fun TrackingStage.subtitle(recommendedDepartureTimeText: String): String {
    return when (this) {
        TrackingStage.Planned -> if (recommendedDepartureTimeText == "지금 출발") {
            "지금 출발을 권장해요."
        } else {
            "권장 출발 시각은 ${recommendedDepartureTimeText}입니다."
        }
        TrackingStage.Boarded -> "이동 중입니다. 도착하면 기록을 완료해 주세요."
        TrackingStage.Arrived -> "오늘 이동 기록 흐름을 완료했습니다."
    }
}

private fun TrackingStage.primaryActionLabel(transportMode: TransportMode): String {
    return when (this) {
        TrackingStage.Planned -> when (transportMode) {
            TransportMode.TRANSIT -> "버스에 탑승했어요"
            TransportMode.WALK -> "걷기 시작했어요"
            TransportMode.CAR -> "차량 이동을 시작했어요"
        }

        TrackingStage.Boarded -> "목적지에 도착했어요"
        TrackingStage.Arrived -> "완료"
    }
}

private fun Long.toLocalTimeText(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun Long.elapsedTimeText(nowEpochMillis: Long): String {
    val totalSeconds = ((nowEpochMillis - this) / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

private fun Int.toDeltaText(): String {
    return when {
        this > 0 -> "+${this}분"
        this < 0 -> "${this}분"
        else -> "정시"
    }
}

private fun Int.toDeltaSentence(): String {
    return when {
        this > 0 -> "목표보다 ${this}분 늦게 도착했어요."
        this < 0 -> "목표보다 ${kotlin.math.abs(this)}분 일찍 도착했어요."
        else -> "목표 도착 시간에 맞춰 도착했어요."
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackingScreenPreview() {
    MapMateTheme {
        TrackingScreen(
            uiState = TrackingUiState(
                routine = sampleRoutine,
                recommendation = sampleRoutine.toFallbackRecommendationUiModel(),
                isLoading = false,
            ),
            onBackClick = {},
            onPrimaryActionClick = {},
            onSegmentStart = {},
            onSegmentComplete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrackingCompletionScreenPreview() {
    MapMateTheme {
        TrackingCompletionScreen(
            contentPadding = PaddingValues(),
            record = sampleRecord,
            onBackClick = {},
            onEditSegmentsClick = {},
            onRecordsClick = {},
            onHomeClick = {},
        )
    }
}

private val sampleRoutine = Routine(
    name = "going school",
    origin = Destination(
        name = "서울역",
        address = "서울특별시 중구",
        latitude = 37.5547,
        longitude = 126.9706,
    ),
    destination = Destination(
        name = "숭실대학교",
        address = "서울 동작구 상도로 369",
        latitude = 37.4963,
        longitude = 126.9574,
    ),
    targetArrivalTime = LocalTime.of(9, 0),
    repeatDays = setOf(
        RepeatDay.MONDAY,
        RepeatDay.TUESDAY,
        RepeatDay.WEDNESDAY,
        RepeatDay.THURSDAY,
        RepeatDay.FRIDAY,
    ),
    transportMode = TransportMode.TRANSIT,
    personalBufferMinutes = 6,
    safetyMarginMinutes = 5,
)

private val sampleRecord = CommuteRecord(
    routineId = 1L,
    routineName = "going school",
    originName = "서울역",
    destinationName = "숭실대학교",
    transportMode = TransportMode.TRANSIT,
    targetArrivalTime = LocalTime.of(9, 0),
    recommendedDepartureTime = LocalTime.of(8, 7),
    routeDurationMinutes = 42,
    routeSummary = "대중교통 기준 42분 예상",
    startedAtEpochMillis = 1_800_000L,
    arrivedAtEpochMillis = 2_000_000L,
    arrivalDeltaMinutes = 2,
)
