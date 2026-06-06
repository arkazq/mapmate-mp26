package com.mapmate.presentation.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.common.EmptyStateCard
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.RoutineCard
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.ui.theme.MapMateTheme
import java.time.LocalTime

@Composable
fun RoutinesRoute(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    routeEstimateProvider: RouteEstimateProvider,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onPredictionClick: (Routine) -> Unit,
) {
    val viewModel: RoutinesViewModel = viewModel(
        factory = RoutinesViewModel.factory(
            routineRepository = routineRepository,
            routeEstimateProvider = routeEstimateProvider,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    RoutinesScreen(
        uiState = uiState,
        onRegisterRoutineClick = onRegisterRoutineClick,
        onEditRoutineClick = onEditRoutineClick,
        onDeleteRoutineClick = viewModel::deleteRoutine,
        onPredictionClick = onPredictionClick,
        modifier = Modifier.padding(contentPadding),
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
) {
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
                subtitle = "출발 루틴을 관리하고 추천 시간을 확인하세요.",
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
            RoutineStatusTabs()
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
                    RoutineCard(
                        recommendation = recommendation,
                        isDeleting = recommendation.routine.id == uiState.deletingRoutineId,
                        onEditClick = { onEditRoutineClick(recommendation.routine) },
                        onDeleteClick = { onDeleteRoutineClick(recommendation.routine) },
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
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MapMateIcon(
                icon = MapMateIconType.Add,
                contentDescription = "루틴 추가",
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun RoutineStatusTabs() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoutineTab(
            text = "활성 루틴",
            selected = true,
            modifier = Modifier.weight(1f),
        )
        RoutineTab(
            text = "비활성 루틴",
            selected = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RoutineTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(36.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
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
