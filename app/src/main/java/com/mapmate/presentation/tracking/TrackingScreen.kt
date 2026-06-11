package com.mapmate.presentation.tracking

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.presentation.common.DetailTopBar
import com.mapmate.presentation.common.IconBadge
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.MetricRow
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.SectionCard
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toKoreanDescription
import com.mapmate.presentation.common.toKoreanLabel
import com.mapmate.presentation.common.transportModeIcon
import com.mapmate.ui.theme.MapMateTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrackingRoute(
    contentPadding: PaddingValues,
    routine: Routine,
    routeEstimateProvider: RouteEstimateProvider,
    commuteRecordRepository: CommuteRecordRepository,
    onBackClick: () -> Unit,
    onCompleted: (CommuteRecord) -> Unit,
) {
    val viewModel: TrackingViewModel = viewModel(
        key = "tracking-${routine.id ?: routine.name}",
        factory = TrackingViewModel.factory(
            routine = routine,
            routeEstimateProvider = routeEstimateProvider,
            commuteRecordRepository = commuteRecordRepository,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.completedRecord) {
        uiState.completedRecord?.let {
            onCompleted(it)
        }
    }

    TrackingScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onPrimaryActionClick = viewModel::onPrimaryActionClick,
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun TrackingScreen(
    uiState: TrackingUiState,
    onBackClick: () -> Unit,
    onPrimaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recommendation = uiState.recommendation

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

                item {
                    TrackingTimelineCard(
                        recommendation = recommendation,
                        stage = uiState.stage,
                    )
                }

                item {
                    CurrentMovementCard(recommendation = recommendation)
                }

                if (!uiState.isCompleted) {
                    item {
                        Button(
                            onClick = onPrimaryActionClick,
                            enabled = !uiState.isSavingRecord,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
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
            Text(
                text = "기록은 더 정확한 추천을 위해 사용돼요.",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = if (recommendation.routine.name.contains("출근")) {
                    MapMateIconType.Work
                } else {
                    MapMateIconType.School
                },
                modifier = Modifier.size(38.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = recommendation.routine.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "목표 도착 시간",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = recommendation.targetArrivalTimeText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "추천 출발 시간",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = recommendation.recommendedDepartureTimeText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackingTimelineCard(
    recommendation: RoutineRecommendationUiModel,
    stage: TrackingStage,
) {
    SectionCard(
        title = stage.title,
        subtitle = stage.subtitle(recommendation.recommendedDepartureTimeText),
        leadingIcon = MapMateIconType.Records,
    ) {
        AssistChip(
            onClick = {},
            label = { Text("현재 단계") },
        )
        StageRow(
            title = "출발 예정",
            description = "${recommendation.recommendedDepartureTimeText} 이후 출발을 권장해요.",
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
            }
        }
    }
}

@Composable
fun TrackingCompletionScreen(
    contentPadding: PaddingValues,
    record: CommuteRecord,
    onBackClick: () -> Unit,
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
                        text = "이번 기록은 다음 추천에 반영됩니다.",
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
        TrackingStage.Planned -> "${recommendedDepartureTimeText} 이후 출발을 권장해요."
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
