package com.mapmate.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.presentation.common.RoutineSummaryCard
import com.mapmate.presentation.routine.SectionBlock
import com.mapmate.ui.theme.MapMateTheme
import java.time.LocalTime

@Composable
fun HomeRoute(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(routineRepository = routineRepository),
    )
    val uiState by viewModel.uiState.collectAsState()

    HomeScreen(
        uiState = uiState,
        onRegisterRoutineClick = onRegisterRoutineClick,
        onEditRoutineClick = onEditRoutineClick,
        onDeleteRoutineClick = viewModel::deleteRoutine,
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onDeleteRoutineClick: (Routine) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HomeHeader()
        }

        item {
            SavedRoutineSection(
                uiState = uiState,
                onRegisterRoutineClick = onRegisterRoutineClick,
                onEditRoutineClick = onEditRoutineClick,
                onDeleteRoutineClick = onDeleteRoutineClick,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "MapMate",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "홈",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "저장된 이동 루틴을 한눈에 확인합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SavedRoutineSection(
    uiState: HomeUiState,
    onRegisterRoutineClick: () -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
    onDeleteRoutineClick: (Routine) -> Unit,
) {
    SectionBlock(
        title = "저장된 루틴",
        subtitle = if (uiState.hasSavedRoutines) {
            "총 ${uiState.savedRoutines.size}개의 루틴"
        } else {
            null
        },
    ) {
        HomeMessageArea(uiState = uiState)

        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
            }

            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            uiState.hasSavedRoutines -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.savedRoutines.forEach { routine ->
                        RoutineSummaryCard(
                            routine = routine,
                            isDeleting = routine.id == uiState.deletingRoutineId,
                            onEditClick = { onEditRoutineClick(routine) },
                            onDeleteClick = { onDeleteRoutineClick(routine) },
                        )
                    }
                }
            }

            else -> {
                EmptyRoutineState(onRegisterRoutineClick = onRegisterRoutineClick)
            }
        }
    }
}

@Composable
private fun HomeMessageArea(uiState: HomeUiState) {
    uiState.errorMessage?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    uiState.successMessage?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EmptyRoutineState(
    onRegisterRoutineClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "아직 저장된 루틴이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRegisterRoutineClick,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("루틴 등록")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MapMateTheme {
        HomeScreen(
            uiState = HomeUiState(
                savedRoutines = listOf(
                    Routine(
                        name = "학교 가는 길",
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
                    ),
                ),
                isLoading = false,
            ),
            onRegisterRoutineClick = {},
            onEditRoutineClick = {},
            onDeleteRoutineClick = {},
        )
    }
}
