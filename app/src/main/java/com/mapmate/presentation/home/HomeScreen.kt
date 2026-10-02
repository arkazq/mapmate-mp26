package com.mapmate.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.common.BoardingAdviceSummaryCard
import com.mapmate.presentation.common.EmptyStateCard
import com.mapmate.presentation.common.IconCircleButton
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.NotificationCircle
import com.mapmate.presentation.common.RouteEstimateStatusMessage
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.presentation.common.ScreenLifecycleEffect
import com.mapmate.presentation.common.transportModeIcon
import com.mapmate.presentation.common.RouteEndpoints
import com.mapmate.presentation.common.JourneyOverview
import com.mapmate.ui.theme.MapMateTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    onSettingsClick: () -> Unit,
    scheduledRouteProvider: ScheduledRouteProvider? = null,
    trackingSessionStore: TrackingSessionStore? = null,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(routineRepository, commuteRecordRepository, routeEstimateProvider, scheduledRouteProvider, trackingSessionStore),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenLifecycleEffect(viewModel::setActive)
    HomeScreen(
        uiState = uiState,
        onRegisterRoutineClick = onRegisterRoutineClick,
        onEditRoutineClick = onEditRoutineClick,
        onPredictionClick = onPredictionClick,
        onStartTrackingClick = onStartTrackingClick,
        onRoutinesClick = onRoutinesClick,
        onSettingsClick = onSettingsClick,
        onRefreshClick = viewModel::refresh,
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
    onSettingsClick: () -> Unit = {},
    onRefreshClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var displayNowEpochMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isActive by remember { androidx.compose.runtime.mutableStateOf(false) }
    ScreenLifecycleEffect { isActive = it }
    LaunchedEffect(isActive) {
        if (isActive) while (true) {
            displayNowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val recommendation = uiState.dashboardRecommendation
    val largeFont = LocalDensity.current.fontScale > 1.3f

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 20.dp, end = 12.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        val dateText = displayNowEpochMillis.toLocalDate().format(DateTimeFormatter.ofPattern("M.d E요일", Locale.KOREAN))
                        Column(Modifier.weight(1f)) {
                            Text("MapMate", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            if (largeFont) Text(dateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!largeFont) Text(dateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        NotificationCircle(onClick = onSettingsClick)
                    }
                }
                uiState.errorMessage?.let { message ->
                    item {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            RouteEstimateStatusMessage(message)
                            TextButton(onClick = onRefreshClick) { Text("다시 시도") }
                        }
                    }
                }
                items(uiState.pendingTrackingRoutines, key = { "pending-${it.id}" }) { routine ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MapMateIcon(MapMateIconType.Walk, null)
                        Column(Modifier.weight(1f)) {
                            Text("측정 중", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                            Text(routine.name, style = MaterialTheme.typography.titleSmall)
                        }
                        TextButton(onClick = { onStartTrackingClick(routine) },
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("측정 이어하기") }
                    }
                }
                when {
                    uiState.isLoading -> item {
                        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("다음 출발을 확인하고 있어요")
                        }
                    }
                    recommendation != null -> {
                        item {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RouteEndpoints(recommendation.routine, Modifier.weight(1f))
                                    IconCircleButton(MapMateIconType.Edit, "${recommendation.routine.name} 경로 수정",
                                        { onEditRoutineClick(recommendation.routine) })
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                DepartureOverview(recommendation, displayNowEpochMillis, uiState.hasCompletedTodayCommute)
                            }
                        }
                        item {
                            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                JourneyOverview(recommendation)
                                recommendation.boardingAdvice?.let { BoardingAdviceSummaryCard(it) }
                                recommendation.routeStatusMessage?.let { RouteEstimateStatusMessage(it) }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (uiState.isRefreshing) "경로 확인 중" else "${uiState.nowEpochMillis.toLocalTimeText()} 조회 기준",
                                        Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = onRefreshClick, enabled = !uiState.isRefreshing) { Text("새로고침") }
                                }
                            }
                        }
                    }
                    uiState.errorMessage == null -> item {
                        EmptyStateCard(
                            title = if (uiState.savedRoutines.isEmpty()) "등록된 루틴이 없어요" else "출발 예정인 루틴이 없어요",
                            message = if (uiState.savedRoutines.isEmpty()) "출발지와 도착 목표를 설정해 주세요." else "반복 요일이 설정된 루틴이 없습니다.",
                            actionLabel = if (uiState.savedRoutines.isEmpty()) "루틴 등록" else "루틴 관리",
                            onActionClick = if (uiState.savedRoutines.isEmpty()) onRegisterRoutineClick else onRoutinesClick,
                        )
                    }
                }
                if (uiState.savedRoutines.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("내 루틴 ${uiState.savedRoutines.size}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton(onClick = onRoutinesClick) { Text("전체 보기") }
                            IconCircleButton(MapMateIconType.Add, "루틴 추가", onRegisterRoutineClick)
                        }
                    }
                    items(uiState.savedRoutines, key = { it.id ?: it.name }) { routine ->
                        Box(Modifier.padding(horizontal = 20.dp)) {
                            HomeRoutineItem(routine, { onPredictionClick(routine) }, { onEditRoutineClick(routine) })
                        }
                    }
                }
                uiState.successMessage?.let { message -> item { Text(message) } }
            }
            if (recommendation != null && !uiState.isLoading) {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        HomeActions(recommendation, displayNowEpochMillis, { onPredictionClick(recommendation.routine) },
                            { onStartTrackingClick(recommendation.routine) },
                            uiState.pendingTrackingRoutines.any { it.id == recommendation.routine.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureOverview(recommendation: RoutineRecommendationUiModel, now: Long, completedToday: Boolean) {
    val nextDay = !recommendation.isDepartureToday(now)
    val finished = completedToday && nextDay
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (finished) "오늘 이동 완료" else "${recommendation.routine.name} · ${if (nextDay) "다음 출발" else "권장 출발"}",
            style = MaterialTheme.typography.labelLarge,
            color = if (finished) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (finished) Text("수고하셨습니다", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
        else {
            val largeFont = LocalDensity.current.fontScale > 1.3f
            if (largeFont) {
                DepartureClock(recommendation)
                Text("${recommendation.arrivalRelativeToTodayText(now)} 도착 목표", style = MaterialTheme.typography.bodyMedium)
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { DepartureClock(recommendation) }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                    Text("도착 목표", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(recommendation.arrivalRelativeToTodayText(now), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            if (nextDay) "다음 출발은 ${recommendation.departureDateTimeText(now)}" else recommendation.departureCountdownText(now),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DepartureClock(recommendation: RoutineRecommendationUiModel) {
    Text(recommendation.recommendedDepartureTimeText, fontSize = 36.sp, lineHeight = 42.sp,
        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
}


@Composable
private fun HomeActions(recommendation: RoutineRecommendationUiModel, now: Long, onDetail: () -> Unit, onTracking: () -> Unit,
    hasPendingMeasurement: Boolean) {
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recommendation.isDepartureToday(now) && !hasPendingMeasurement) {
                Button(onClick = onTracking, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                    MapMateIcon(MapMateIconType.Play, null, Modifier.size(18.dp))
                    Text("이동 시작", Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(onClick = onDetail, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("상세 경로") }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) { Text("상세 경로") }
        if (recommendation.isDepartureToday(now) && !hasPendingMeasurement) {
            Button(onClick = onTracking, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = MaterialTheme.shapes.medium) {
                MapMateIcon(MapMateIconType.Play, null, Modifier.size(18.dp))
                Text("이동 시작", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun HomeRoutineItem(routine: Routine, onDetail: () -> Unit, onEdit: () -> Unit) {
    Surface(
        onClick = onDetail,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MapMateIcon(transportModeIcon(routine.transportMode), null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(routine.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${routine.targetArrivalTime} 도착 · ${routine.destination.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconCircleButton(MapMateIconType.Edit, "${routine.name} 수정", onEdit)
        }
    }
}

private fun RoutineRecommendationUiModel.isDepartureToday(now: Long): Boolean =
    recommendedDepartureAtEpochMillis?.toLocalDate()?.let { it == now.toLocalDate() } ?: false

private fun RoutineRecommendationUiModel.departureDateTimeText(now: Long): String {
    val date = recommendedDepartureAtEpochMillis?.toLocalDate() ?: return recommendedDepartureTimeText
    val today = now.toLocalDate()
    val label = when (date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> date.format(DateTimeFormatter.ofPattern("M/d E요일", Locale.KOREAN))
    }
    return "$label $recommendedDepartureTimeText"
}

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
private fun Long.toLocalTimeText(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("HH:mm"))

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MapMateTheme { HomeScreen(HomeUiState(isLoading = false), {}, {}, {}, {}, {}) }
}
