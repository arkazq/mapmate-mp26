package com.mapmate.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.common.BoardingAdviceSummaryCard
import com.mapmate.presentation.common.EmptyStateCard
import com.mapmate.presentation.common.MapMateElevation
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.NotificationCircle
import com.mapmate.presentation.common.RouteEstimateStatusMessage
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.transportModeIcon
import com.mapmate.ui.theme.MapMateTheme
import java.time.LocalTime
import kotlinx.coroutines.delay

@Composable
fun HomeRoute(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    commuteRecordRepository: CommuteRecordRepository,
    routeEstimateProvider: RouteEstimateProvider,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onPredictionClick: (Routine) -> Unit,
    onStartTrackingClick: (Routine) -> Unit,
    onRoutinesClick: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            routineRepository = routineRepository,
            commuteRecordRepository = commuteRecordRepository,
            routeEstimateProvider = routeEstimateProvider,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    HomeScreen(
        uiState = uiState,
        onRegisterRoutineClick = onRegisterRoutineClick,
        onEditRoutineClick = onEditRoutineClick,
        onPredictionClick = onPredictionClick,
        onStartTrackingClick = onStartTrackingClick,
        onRoutinesClick = onRoutinesClick,
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onPredictionClick: (Routine) -> Unit,
    onStartTrackingClick: (Routine) -> Unit,
    onRoutinesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 카운트다운/진행바 표시는 1초마다 갱신해 부드럽게 움직이게 한다.
    // 경로 재계산(getRouteEstimate, 60초)과 분리되어 추가 API 호출은 없다.
    var displayNowEpochMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            displayNowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
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
            ScreenHeader(
                title = "오늘의 출발 준비",
                subtitle = if (uiState.hasSavedRoutines) {
                    "저장된 루틴을 기준으로 오늘 언제 출발할지 확인합니다."
                } else {
                    "저장된 루틴을 기준으로 오늘 언제 출발할지 확인합니다."
                },
                trailingContent = { NotificationCircle() },
            )
        }

        item {
            HomeDashboardHero(
                uiState = uiState,
                onRegisterRoutineClick = onRegisterRoutineClick,
                onEditRoutineClick = onEditRoutineClick,
                onPredictionClick = onPredictionClick,
                onStartTrackingClick = onStartTrackingClick,
            )
        }

        uiState.dashboardRecommendation?.let { recommendation ->
            item {
                CountdownProgressCard(
                    recommendation = recommendation,
                    nowEpochMillis = displayNowEpochMillis,
                )
            }
            item {
                recommendation.boardingAdvice?.let { advice ->
                    BoardingAdviceSummaryCard(advice = advice)
                } ?: RecommendationReasonCard()
            }
            item {
                RouteEstimateStatusMessage(message = recommendation.routeStatusMessage)
            }
            item {
                RouteEstimateStatusMessage(message = recommendation.departureStatusMessage)
            }
            item {
                CompactMetricCards(recommendation = recommendation)
            }
            item {
                HomeActionButtons(
                    recommendation = recommendation,
                    onPredictionClick = onPredictionClick,
                    onStartTrackingClick = onStartTrackingClick,
                )
            }
        }

        if (uiState.savedRoutines.isNotEmpty()) {
            item {
                HomeRoutineListHeader(
                    routineCount = uiState.savedRoutines.size,
                    onManageClick = onRoutinesClick,
                )
            }
            items(
                items = uiState.savedRoutines,
                key = { it.id ?: it.name },
            ) { routine ->
                HomeRoutineSummaryCard(
                    routine = routine,
                    onDetailClick = { onPredictionClick(routine) },
                    onEditClick = { onEditRoutineClick(routine) },
                )
            }
        }

        uiState.successMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeRoutineListHeader(
    routineCount: Int,
    onManageClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "저장된 루틴",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "${routineCount}개 루틴을 관리 중입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = onManageClick,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("전체 관리")
        }
    }
}

@Composable
private fun HomeRoutineSummaryCard(
    routine: Routine,
    onDetailClick: () -> Unit,
    onEditClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                MapMateIcon(
                    icon = transportModeIcon(routine.transportMode),
                    contentDescription = null,
                    modifier = Modifier.padding(9.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = routine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${routine.targetArrivalTime} 도착 목표 · ${routine.destination.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onEditClick,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("수정")
            }
            Button(
                onClick = onDetailClick,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("예측")
            }
        }
    }
}

@Composable
private fun HomeDashboardHero(
    uiState: HomeUiState,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onPredictionClick: (Routine) -> Unit,
    onStartTrackingClick: (Routine) -> Unit,
) {
    val recommendation = uiState.dashboardRecommendation

    when {
        uiState.isLoading -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
            ) {
                Column(
                    modifier = Modifier.padding(MapMateSpacing.CardInner),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator()
                    Text("오늘의 루틴을 불러오는 중입니다.")
                }
            }
        }

        recommendation != null -> {
            CompactHomeHeroCard(recommendation = recommendation)
        }

        else -> {
            EmptyStateCard(
                title = "저장된 루틴이 없습니다",
                message = "등교/출근 루틴을 등록하면 권장 출발 시각을 계산할 수 있어요.",
                actionLabel = "루틴 등록하기",
                onActionClick = onRegisterRoutineClick,
            )
        }
    }
}

@Composable
private fun CompactHomeHeroCard(
    recommendation: RoutineRecommendationUiModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = MapMateElevation.Hero,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "오늘은",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                ) {
                    Text(
                        text = "${recommendation.targetArrivalTimeText} 도착 목표",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = recommendation.recommendedDepartureDisplayText,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "출발하세요! · ${recommendation.routine.name}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CountdownProgressCard(
    recommendation: RoutineRecommendationUiModel,
    nowEpochMillis: Long,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                text = recommendation.departureCountdownText(nowEpochMillis),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { recommendation.departureProgress(nowEpochMillis) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun RecommendationReasonCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "최근 기록과 실시간 정보를 반영했어요",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "최근 7일 평균 오차 +2분",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeActionButtons(
    recommendation: RoutineRecommendationUiModel,
    onPredictionClick: (Routine) -> Unit,
    onStartTrackingClick: (Routine) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onPredictionClick(recommendation.routine) },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            MapMateIcon(
                icon = MapMateIconType.Records,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "상세 예측 보기",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        OutlinedButton(
            onClick = { onStartTrackingClick(recommendation.routine) },
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            MapMateIcon(
                icon = MapMateIconType.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "이동 기록 시작",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CompactMetricCards(
    recommendation: RoutineRecommendationUiModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeMetricCard(
            label = "예상 이동 시간",
            value = "${recommendation.routeDurationMinutes}분",
            modifier = Modifier.weight(1f),
        )
        HomeMetricCard(
            label = "개인 보정",
            value = "+${recommendation.personalBufferMinutes}분",
            modifier = Modifier.weight(1f),
        )
        HomeMetricCard(
            label = "안전 여유",
            value = "${recommendation.safetyMarginMinutes}분",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MapMateTheme {
        HomeScreen(
            uiState = HomeUiState(
                savedRoutines = listOf(sampleRoutine),
                dashboardRecommendation = sampleRoutine.toFallbackRecommendationUiModel(),
                isLoading = false,
            ),
            onRegisterRoutineClick = {},
            onEditRoutineClick = {},
            onPredictionClick = {},
            onStartTrackingClick = {},
            onRoutinesClick = {},
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
