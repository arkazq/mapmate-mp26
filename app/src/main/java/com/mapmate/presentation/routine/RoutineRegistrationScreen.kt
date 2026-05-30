package com.mapmate.presentation.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.ui.theme.MapMateTheme

@Composable
fun RoutineRegistrationRoute(
    contentPadding: PaddingValues,
    viewModel: RoutineRegistrationViewModel = viewModel(),
) {
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
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header()

        SectionBlock(title = "기본 정보") {
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
                label = { Text("목적지") },
                placeholder = { Text("예: 숭실대학교") },
                singleLine = true,
            )

            DestinationCandidateList(
                candidates = uiState.destinationCandidates,
                selectedDestination = uiState.selectedDestination,
                onDestinationSelected = { onEvent(RoutineRegistrationEvent.DestinationSelected(it)) },
            )
        }

        SectionBlock(title = "도착 목표") {
            OutlinedTextField(
                value = uiState.targetArrivalTimeText,
                onValueChange = { onEvent(RoutineRegistrationEvent.ArrivalTimeChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("목표 도착 시각") },
                placeholder = { Text("09:00") },
                supportingText = { Text("HH:mm 형식으로 입력") },
                singleLine = true,
            )
        }

        SectionBlock(title = "반복 요일") {
            RepeatDaySelector(
                selectedRepeatDays = uiState.selectedRepeatDays,
                onRepeatDayToggled = { onEvent(RoutineRegistrationEvent.RepeatDayToggled(it)) },
            )
        }

        SectionBlock(title = "이동 수단") {
            TransportModeSelector(
                selectedTransportMode = uiState.selectedTransportMode,
                onTransportModeSelected = {
                    onEvent(RoutineRegistrationEvent.TransportModeSelected(it))
                },
            )
        }

        SectionBlock(title = "시간 보정") {
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

        ResultArea(
            routeEstimate = uiState.routeEstimate,
            recommendedDepartureTimeText = uiState.recommendedDepartureTimeText,
        )

        MessageArea(
            errorMessage = uiState.errorMessage,
            successMessage = uiState.successMessage,
        )

        Button(
            onClick = { onEvent(RoutineRegistrationEvent.SaveClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.isSaveEnabled,
        ) {
            Text("루틴 저장")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun Header() {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    }
}

@Composable
private fun ResultArea(
    routeEstimate: RouteEstimate?,
    recommendedDepartureTimeText: String,
) {
    if (routeEstimate == null && recommendedDepartureTimeText.isBlank()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "계산 결과",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (recommendedDepartureTimeText.isNotBlank()) {
                Text(
                    text = "권장 출발 시각: $recommendedDepartureTimeText",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            routeEstimate?.let {
                Text("예상 이동 시간: ${it.estimatedMinutes}분")
                Text("요약: ${it.summary}")
                Text("근거: ${it.reason}")
                Text(
                    text = "Provider: ${it.providerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                recommendedDepartureTimeText = "08:07",
            ),
            onEvent = {},
        )
    }
}
