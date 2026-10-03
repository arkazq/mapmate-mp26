package com.mapmate.presentation.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.presentation.common.EmptyStateCard
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.IconCircleButton
import com.mapmate.presentation.common.ScreenLifecycleEffect
import com.mapmate.presentation.common.toKoreanShortLabel
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.presentation.common.RouteEndpoints
import com.mapmate.presentation.common.IconBadge
import com.mapmate.presentation.common.transportModeIcon
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.ui.theme.MapMateTheme
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RoutinesRoute(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    routeEstimateProvider: RouteEstimateProvider,
    commuteRecordRepository: CommuteRecordRepository,
    trackingSessionStore: TrackingSessionStore,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onPredictionClick: (Routine) -> Unit,
    scheduledRouteProvider: ScheduledRouteProvider? = null,
) {
    val viewModel: RoutinesViewModel = viewModel(
        factory = RoutinesViewModel.factory(
            routineRepository = routineRepository,
            routeEstimateProvider = routeEstimateProvider,
            commuteRecordRepository = commuteRecordRepository,
            trackingSessionStore = trackingSessionStore,
            scheduledRouteProvider = scheduledRouteProvider,
        ),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenLifecycleEffect(viewModel::setActive)

    RoutinesScreen(
        uiState = uiState,
        onRegisterRoutineClick = onRegisterRoutineClick,
        onEditRoutineClick = onEditRoutineClick,
        onDeleteRoutineClick = viewModel::deleteRoutine,
        onPredictionClick = onPredictionClick,
        modifier = Modifier.padding(contentPadding),
        onRefresh = viewModel::refresh,
    )
}

@Composable
fun RoutinesScreen(
    uiState: RoutinesUiState,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onDeleteRoutineClick: (Routine) -> Unit,
    onPredictionClick: (Routine) -> Unit,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    var routineToDelete by remember { mutableStateOf<Routine?>(null) }
    routineToDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("${routine.name} 루틴을 삭제할까요?") },
            text = { Text("출발 알림과 진행 중인 측정은 취소됩니다. 저장된 이동 기록은 유지됩니다.") },
            confirmButton = { TextButton(onClick = { routineToDelete = null; onDeleteRoutineClick(routine) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { routineToDelete = null }) { Text("취소") } },
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
            ScreenHeader(
                title = "내 루틴",
                subtitle = "저장한 이동 ${uiState.recommendations.size}개",
                eyebrow = "",
                trailingContent = {
                    AddRoutineButton(onClick = onRegisterRoutineClick)
                },
            )
        }

        uiState.errorMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRefresh) { Text("다시 시도") }
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

        when {
            uiState.isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            uiState.hasRoutines -> {
                items(
                    items = uiState.recommendations,
                    key = { it.routine.id ?: it.routine.name },
                ) { recommendation ->
                    RoutineListItem(
                        recommendation = recommendation,
                        isDeleting = recommendation.routine.id == uiState.deletingRoutineId,
                        onEditClick = { onEditRoutineClick(recommendation.routine) },
                        onDeleteClick = { routineToDelete = recommendation.routine },
                        onDetailClick = { onPredictionClick(recommendation.routine) },
                    )
                }
            }

            else -> {
                item {
                    EmptyStateCard(
                        title = "저장된 루틴이 없습니다",
                        message = "등교/출근 루틴을 등록하면 권장 출발 시각을 계산할 수 있어요.",
                        actionLabel = "루틴 등록하기",
                        onActionClick = onRegisterRoutineClick,
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AddRoutineButton(
    onClick: () -> Unit,
) {
    IconCircleButton(MapMateIconType.Add, "루틴 추가", onClick)
}

@Composable
private fun RoutineListItem(
    recommendation: RoutineRecommendationUiModel,
    isDeleting: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    val routine = recommendation.routine
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(transportModeIcon(routine.transportMode))
                Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onEditClick, enabled = !isDeleting) {
                    MapMateIcon(MapMateIconType.Edit, "${routine.name} 수정")
                }
            }
            RouteEndpoints(routine, Modifier.clickable(enabled = !isDeleting, onClick = onDetailClick))
            Text("${recommendation.targetArrivalTimeText} 도착 · ${routine.repeatDays.sortedBy { it.ordinal }.joinToString(" ") { it.toKoreanShortLabel() }}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val departure = recommendation.recommendedDepartureAtEpochMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (routine.repeatDays.isEmpty()) "반복 요일 없음" else "다음 출발 ${departure ?: "계산 중"}",
                    modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDetailClick, enabled = !isDeleting) {
                    MapMateIcon(MapMateIconType.Route, "${routine.name} 상세 경로")
                }
                IconButton(onClick = onDeleteClick, enabled = !isDeleting) {
                    MapMateIcon(MapMateIconType.Delete, "${routine.name} 삭제")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutinesScreenPreview() {
    MapMateTheme {
        val routines = listOf(
            sampleRoutine,
            sampleRoutine.copy(name = "출근", targetArrivalTime = LocalTime.of(8, 30)),
            sampleRoutine.copy(name = "학원", targetArrivalTime = LocalTime.of(18, 0)),
        )
        RoutinesScreen(
            uiState = RoutinesUiState(
                recommendations = routines.map { it.toFallbackRecommendationUiModel() },
                isLoading = false,
            ),
            onRegisterRoutineClick = {},
            onEditRoutineClick = {},
            onDeleteRoutineClick = {},
            onPredictionClick = {},
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
