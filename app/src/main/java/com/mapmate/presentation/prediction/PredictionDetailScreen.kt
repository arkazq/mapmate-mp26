package com.mapmate.presentation.prediction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.presentation.common.BoardingAdviceDetailCard
import com.mapmate.presentation.common.DetailTopBar
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.MetricRow
import com.mapmate.presentation.common.RouteEstimateStatusMessage
import com.mapmate.presentation.common.RouteTimeline
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.ScreenLifecycleEffect
import com.mapmate.presentation.common.routeTimelineItems
import com.mapmate.presentation.common.RouteEndpoints
import com.mapmate.presentation.common.JourneyOverview
import com.mapmate.presentation.common.JourneyItinerary
import com.mapmate.presentation.common.IconCircleButton
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.toRecommendationUiModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PredictionDetailRoute(
    contentPadding: PaddingValues,
    routine: Routine,
    routeEstimateProvider: RouteEstimateProvider,
    commuteRecordRepository: CommuteRecordRepository,
    onBackClick: () -> Unit,
    onStartTrackingClick: (Routine) -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    scheduledRouteProvider: ScheduledRouteProvider? = null,
) {
    val viewModel: PredictionDetailViewModel = viewModel(
        key = "prediction-${routine.id}",
        factory = PredictionDetailViewModel.factory(routine, routeEstimateProvider, commuteRecordRepository, scheduledRouteProvider),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenLifecycleEffect(viewModel::setActive)
    LaunchedEffect(routine) { viewModel.updateRoutine(routine) }
    PredictionDetailScreen(uiState, onBackClick, { onStartTrackingClick(routine) },
        { onEditRoutineClick(routine) }, onRefreshClick = viewModel::refresh, modifier = Modifier.padding(contentPadding))
}

@Composable
fun PredictionDetailScreen(
    uiState: PredictionDetailUiState,
    onBackClick: () -> Unit,
    onStartTrackingClick: () -> Unit,
    onEditRoutineClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshClick: () -> Unit = {},
) {
    val recommendation = uiState.recommendation
    var showCalculation by rememberSaveable { mutableStateOf(false) }
    var detailTab by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            DetailTopBar("상세 경로", onBackClick, Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp),
                trailingContent = { IconCircleButton(MapMateIconType.Edit, "루틴 수정", onEditRoutineClick) })
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (uiState.isLoading && recommendation == null) {
                    item { CircularProgressIndicator(Modifier.size(24.dp)) }
                }
                recommendation?.let { result ->
                    item {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            RouteEndpoints(result.routine)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            JourneyOverview(result)
                            Text(
                                "${result.departureWithDateText()} 출발 권장",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("${result.arrivalWithDateText()} 도착 목표", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RouteEstimateStatusMessage(result.routeStatusMessage)
                            if (result.isImmediateDepartureRecommended) Text("지금 출발하는 것이 좋아요", fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        SecondaryTabRow(selectedTabIndex = detailTab, containerColor = MaterialTheme.colorScheme.surface) {
                            listOf("이동 경로", "탑승·출발").forEachIndexed { index, title ->
                                Tab(selected = detailTab == index, onClick = { detailTab = index }, text = { Text(title) })
                            }
                        }
                    }
                    if (detailTab == 0) item {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            JourneyItinerary(result)
                        }
                    }
                    if (detailTab == 1) {
                    result.boardingAdvice?.let { advice -> item {
                        BoardingAdviceDetailCard(advice, Modifier.background(MaterialTheme.colorScheme.surface).padding(20.dp))
                    } }
                    item {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp)) {
                            TextButton(onClick = { showCalculation = !showCalculation }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                Text(if (showCalculation) "출발 시각 계산 접기" else "출발 시각 계산 근거 보기")
                            }
                            if (showCalculation) CalculationBasis(result)
                        }
                    }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${Instant.ofEpochMilli(uiState.nowEpochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))} 확인",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onRefreshClick) { Text("새로고침") }
                        }
                    }
                }
                uiState.errorMessage?.let { message -> item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        RouteEstimateStatusMessage(message)
                        TextButton(onClick = onRefreshClick) { Text("다시 시도") }
                    }
                } }
            }
            if (recommendation != null) Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (recommendation.isDepartureToday(uiState.nowEpochMillis)) {
                        Button(onStartTrackingClick, Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("이동 시작") }
                    } else {
                        Text("다음 출발 ${recommendation.departureWithDateText()}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculationBasis(recommendation: RoutineRecommendationUiModel) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("시간 계산", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        MetricRow("예상 이동", "${recommendation.routeDurationMinutes}분")
        MetricRow("개인 보정", "${recommendation.personalBufferMinutes}분")
        MetricRow("안전 여유", "${recommendation.safetyMarginMinutes}분")
        MetricRow("목표 도착 기준 출발", recommendation.calculatedDepartureTimeText)
        recommendation.boardingAdvice?.earlyDepartureRequiredMinutes?.takeIf { it > 0 }?.let {
            MetricRow("첫 버스 탑승을 위해", "${it}분 앞당김")
        }
        MetricRow("최종 권장 출발", recommendation.recommendedDepartureTimeText,
            valueColor = MaterialTheme.colorScheme.primary)
    }
}

private fun RoutineRecommendationUiModel.isDepartureToday(now: Long): Boolean {
    val epoch = recommendedDepartureAtEpochMillis ?: return false
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate() == Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@androidx.compose.ui.tooling.preview.Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PredictionDetailPreview() {
    val now = java.time.ZonedDateTime.now()
    val routine = Routine(
        name = "등교",
        origin = com.mapmate.domain.model.Destination("서울역", "서울 중구", null, null),
        destination = com.mapmate.domain.model.Destination("숭실대학교", "서울 동작구", null, null),
        targetArrivalTime = now.plusMinutes(48).toLocalTime(),
        repeatDays = com.mapmate.domain.model.RepeatDay.entries.toSet(),
        transportMode = com.mapmate.domain.model.TransportMode.TRANSIT,
        personalBufferMinutes = 3, safetyMarginMinutes = 5,
    )
    val estimate = com.mapmate.domain.model.RouteEstimate(20, "서울역 → 숭실대학교", "Preview", "", segments = listOf(
        com.mapmate.domain.model.RouteSegment(segmentIndex = 0, segmentType = com.mapmate.domain.model.RouteSegmentType.WALK_TO_TRANSIT,
            startName = "서울역", endName = "서울역버스환승센터", plannedDurationMinutes = 3),
        com.mapmate.domain.model.RouteSegment(segmentIndex = 1, segmentType = com.mapmate.domain.model.RouteSegmentType.BUS_RIDE,
            routeName = "753", startName = "서울역버스환승센터", endName = "숭실대입구역", plannedDurationMinutes = 15),
        com.mapmate.domain.model.RouteSegment(segmentIndex = 2, segmentType = com.mapmate.domain.model.RouteSegmentType.WALK_TO_DESTINATION,
            startName = "숭실대입구역", endName = "숭실대학교", plannedDurationMinutes = 2),
    ))
    val recommendation = routine.toRecommendationUiModel(estimate,
        recommendedDepartureAtEpochMillis = now.plusMinutes(20).toInstant().toEpochMilli(),
        targetArrivalAtEpochMillis = now.plusMinutes(48).toInstant().toEpochMilli(), displayedDepartureTime = now.plusMinutes(20).toLocalTime())
    com.mapmate.ui.theme.MapMateTheme {
        PredictionDetailScreen(PredictionDetailUiState(routine, recommendation, isLoading = false), {}, {}, {})
    }
}
