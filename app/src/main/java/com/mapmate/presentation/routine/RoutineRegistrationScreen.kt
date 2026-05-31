package com.mapmate.presentation.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.ui.theme.MapMateTheme
import java.time.format.DateTimeFormatter

@Composable
fun RoutineRegistrationRoute(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
) {
    val viewModel: RoutineRegistrationViewModel = viewModel(
        factory = RoutineRegistrationViewModel.factory(routineRepository),
    )
    val uiState by viewModel.uiState.collectAsState()

    RoutineRegistrationScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun RoutineRegistrationScreen(
    uiState: RoutineRegistrationUiState,
    onEvent: (RoutineRegistrationEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header()
        }

        item {
            SavedRoutineOverview(routines = uiState.savedRoutines)
        }

        item {
            SectionBlock(
                title = "기본 정보",
                subtitle = "루틴 이름과 도착할 장소를 정합니다.",
            ) {
                OutlinedTextField(
                    value = uiState.routineName,
                    onValueChange = { onEvent(RoutineRegistrationEvent.RoutineNameChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("루틴 이름") },
                    placeholder = { Text("예: 학교 가는 길") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = uiState.destinationQuery,
                    onValueChange = { onEvent(RoutineRegistrationEvent.DestinationQueryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("목적지 검색") },
                    placeholder = { Text("예: 숭실대학교") },
                    singleLine = true,
                )

                DestinationCandidateList(
                    candidates = uiState.destinationCandidates,
                    selectedDestination = uiState.selectedDestination,
                    onDestinationSelected = { onEvent(RoutineRegistrationEvent.DestinationSelected(it)) },
                )
            }
        }

        item {
            SectionBlock(
                title = "도착 목표",
                subtitle = "발표 데모에서는 HH:mm 형식으로 입력합니다.",
            ) {
                OutlinedTextField(
                    value = uiState.targetArrivalTimeText,
                    onValueChange = { onEvent(RoutineRegistrationEvent.ArrivalTimeChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("목표 도착 시각") },
                    placeholder = { Text("09:00") },
                    supportingText = { Text("예: 08:30, 09:00") },
                    singleLine = true,
                )
            }
        }

        item {
            SectionBlock(title = "반복 요일") {
                RepeatDaySelector(
                    selectedRepeatDays = uiState.selectedRepeatDays,
                    onRepeatDayToggled = { onEvent(RoutineRegistrationEvent.RepeatDayToggled(it)) },
                )
            }
        }

        item {
            SectionBlock(
                title = "이동 수단",
                subtitle = "Mock 예상 이동 시간은 선택한 이동 수단에 따라 달라집니다.",
            ) {
                TransportModeSelector(
                    selectedTransportMode = uiState.selectedTransportMode,
                    onTransportModeSelected = {
                        onEvent(RoutineRegistrationEvent.TransportModeSelected(it))
                    },
                )
            }
        }

        item {
            SectionBlock(
                title = "보정 설정",
                subtitle = "예상 이동 시간에 더할 개인 보정과 기본 여유 시간입니다.",
            ) {
                MinuteInputRow(
                    personalBufferMinutes = uiState.personalBufferMinutes,
                    safetyMarginMinutes = uiState.safetyMarginMinutes,
                    onPersonalBufferChanged = {
                        onEvent(RoutineRegistrationEvent.PersonalBufferChanged(it))
                    },
                    onSafetyMarginChanged = {
                        onEvent(RoutineRegistrationEvent.SafetyMarginChanged(it))
                    },
                )
            }
        }

        item {
            ResultArea(uiState = uiState)
        }

        item {
            SaveActionArea(
                uiState = uiState,
                onEvent = onEvent,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "MapMate",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "루틴 등록",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "반복되는 등교와 출근 루틴의 권장 출발 시각을 계산합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SavedRoutineOverview(routines: List<Routine>) {
    SectionBlock(
        title = "저장된 루틴",
        subtitle = if (routines.isEmpty()) {
            "저장 버튼을 누르면 이곳에 루틴이 표시됩니다."
        } else {
            "총 ${routines.size}개의 루틴이 저장되어 있습니다."
        },
    ) {
        if (routines.isEmpty()) {
            Text(
                text = "아직 저장된 루틴이 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            routines.forEach { routine ->
                SavedRoutineRow(routine = routine)
            }
        }
    }
}

@Composable
private fun SavedRoutineRow(routine: Routine) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = routine.destination.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = routine.targetArrivalTime.format(timeFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = listOf(
                    routine.transportMode.toKoreanLabel(),
                    routine.repeatDays
                        .sortedBy { it.ordinal }
                        .joinToString(" ") { it.toKoreanShortLabel() },
                    "보정 ${routine.personalBufferMinutes}분",
                    "여유 ${routine.safetyMarginMinutes}분",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultArea(uiState: RoutineRegistrationUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.hasCalculationResult) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (uiState.hasCalculationResult) 3.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "계산 결과",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (uiState.hasCalculationResult) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            if (uiState.hasCalculationResult) {
                Text(
                    text = uiState.recommendedDepartureTimeText,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "권장 출발 시각",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                uiState.routeEstimate?.let {
                    ResultMetricRow(label = "예상 이동 시간", value = "${it.estimatedMinutes}분")
                    ResultMetricRow(label = "개인 보정 시간", value = "${uiState.personalBufferMinutes}분")
                    ResultMetricRow(label = "안전 여유 시간", value = "${uiState.safetyMarginMinutes}분")
                    Text(
                        text = it.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                Text(
                    text = "목적지와 시간을 입력한 뒤 권장 출발 시각을 계산하면 이곳에 결과가 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ResultMetricRow(
    label: String,
    value: String,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SaveActionArea(
    uiState: RoutineRegistrationUiState,
    onEvent: (RoutineRegistrationEvent) -> Unit,
) {
    SectionBlock(title = "저장") {
        MessageArea(
            errorMessage = uiState.errorMessage,
            successMessage = uiState.successMessage,
        )

        OutlinedButton(
            onClick = { onEvent(RoutineRegistrationEvent.CalculateClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isCalculating,
        ) {
            if (uiState.isCalculating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("권장 출발 시각 계산")
            }
        }

        Button(
            onClick = { onEvent(RoutineRegistrationEvent.SaveClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isSaveEnabled && !uiState.isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("루틴 저장")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineRegistrationScreenPreview() {
    MapMateTheme {
        RoutineRegistrationScreen(
            uiState = RoutineRegistrationUiState(
                routineName = "학교 가는 길",
                destinationQuery = "숭실대학교",
                selectedTransportMode = TransportMode.TRANSIT,
                routeEstimate = RouteEstimate(
                    estimatedMinutes = 42,
                    summary = "숭실대학교까지 대중교통 기준 42분 예상",
                    providerName = "MockRouteEstimateProvider",
                    reason = "Mock 대중교통 예상 시간입니다.",
                ),
                recommendedDepartureTimeText = "08:07",
            ),
            onEvent = {},
        )
    }
}
