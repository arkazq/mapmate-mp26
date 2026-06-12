package com.mapmate.presentation.routine

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.CurrentLocationProvider
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.presentation.common.DayOfWeekSelector
import com.mapmate.presentation.common.DetailTopBar
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.MetricRow
import com.mapmate.presentation.common.SectionCard
import com.mapmate.presentation.common.TransportModeSelector as CommonTransportModeSelector
import com.mapmate.ui.theme.MapMateTheme

@Composable
fun RoutineRegistrationRoute(
    contentPadding: PaddingValues,
    routineRepository: RoutineRepository,
    settingsRepository: SettingsRepository,
    placeSearchProvider: PlaceSearchProvider,
    routeEstimateProvider: RouteEstimateProvider,
    currentLocationProvider: CurrentLocationProvider,
    editingRoutine: Routine? = null,
    onBackClick: () -> Unit,
    onSaveCompleted: () -> Unit,
) {
    val viewModel: RoutineRegistrationViewModel = viewModel(
        factory = RoutineRegistrationViewModel.factory(
            routineRepository = routineRepository,
            settingsRepository = settingsRepository,
            placeSearchProvider = placeSearchProvider,
            routeEstimateProvider = routeEstimateProvider,
            currentLocationProvider = currentLocationProvider,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(editingRoutine?.id) {
        if (editingRoutine == null) {
            viewModel.startNewRoutine()
        } else {
            viewModel.loadRoutineForEditing(editingRoutine)
        }
    }

    LaunchedEffect(uiState.isSaveCompleted) {
        if (uiState.isSaveCompleted) {
            onSaveCompleted()
        }
    }

    RoutineRegistrationScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBackClick = onBackClick,
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun RoutineRegistrationScreen(
    uiState: RoutineRegistrationUiState,
    onEvent: (RoutineRegistrationEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val isGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (isGranted) {
            onEvent(RoutineRegistrationEvent.CurrentLocationClicked)
        } else {
            onEvent(RoutineRegistrationEvent.CurrentLocationPermissionDenied)
        }
    }
    val onCurrentLocationClick = {
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocationPermission || hasCoarseLocationPermission) {
            onEvent(RoutineRegistrationEvent.CurrentLocationClicked)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
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
            DetailTopBar(
                title = if (uiState.isEditing) "루틴 수정" else "루틴 등록",
                onBackClick = onBackClick,
            )
        }

        item {
            RegistrationStepRow(activeStepIndex = currentStepIndex)
        }

        if (currentStepIndex == 0) item {
            SectionCard(
                title = "기본 정보",
                leadingText = "1",
            ) {
                OutlinedTextField(
                    value = uiState.routineName,
                    onValueChange = { onEvent(RoutineRegistrationEvent.RoutineNameChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("루틴 이름") },
                    placeholder = { Text("등교 루틴") },
                    supportingText = { Text("${uiState.routineName.length}/30") },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                )
            }
        }

        if (currentStepIndex == 1) item {
            SectionCard(
                title = "장소",
                leadingText = "2",
            ) {
                OutlinedTextField(
                    value = uiState.originQuery,
                    onValueChange = { onEvent(RoutineRegistrationEvent.OriginQueryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("출발지") },
                    placeholder = { Text("우리집") },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                )

                OutlinedButton(
                    onClick = onCurrentLocationClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isGettingCurrentLocation,
                ) {
                    if (uiState.isGettingCurrentLocation) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("현재 위치 사용")
                    }
                }

                uiState.selectedOrigin?.let { selectedOrigin ->
                    SelectedDestinationSummary(
                        label = "선택된 출발지",
                        destination = selectedOrigin,
                    )
                }

                DestinationCandidateList(
                    candidates = uiState.originCandidates,
                    selectedDestination = uiState.selectedOrigin,
                    onDestinationSelected = { onEvent(RoutineRegistrationEvent.OriginSelected(it)) },
                )

                OutlinedTextField(
                    value = uiState.destinationQuery,
                    onValueChange = { onEvent(RoutineRegistrationEvent.DestinationQueryChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("목적지") },
                    placeholder = { Text("숭실대학교") },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                )

                DestinationCandidateList(
                    candidates = uiState.destinationCandidates,
                    selectedDestination = uiState.selectedDestination,
                    onDestinationSelected = { onEvent(RoutineRegistrationEvent.DestinationSelected(it)) },
                )
            }
        }

        if (currentStepIndex == 2) item {
            SectionCard(
                title = "도착 목표",
                subtitle = "HH:mm 형식으로 입력합니다.",
                leadingText = "3",
            ) {
                OutlinedTextField(
                    value = uiState.targetArrivalTimeText,
                    onValueChange = { onEvent(RoutineRegistrationEvent.ArrivalTimeChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("도착 목표 시각") },
                    placeholder = { Text("09:00") },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                )
            }
        }

        if (currentStepIndex == 3) item {
            SectionCard(
                title = "반복 요일",
                leadingText = "4",
            ) {
                DayOfWeekSelector(
                    selectedRepeatDays = uiState.selectedRepeatDays,
                    onRepeatDayToggled = { onEvent(RoutineRegistrationEvent.RepeatDayToggled(it)) },
                )
            }
        }

        if (currentStepIndex == 4) item {
            SectionCard(
                title = "이동 수단",
                leadingText = "5",
            ) {
                CommonTransportModeSelector(
                    selectedTransportMode = uiState.selectedTransportMode,
                    onTransportModeSelected = {
                        onEvent(RoutineRegistrationEvent.TransportModeSelected(it))
                    },
                )
            }
        }

        if (currentStepIndex == 5) item {
            SectionCard(
                title = "보정 설정",
                subtitle = "예상 이동 시간에 더할 개인 보정과 기본 여유 시간입니다.",
                leadingText = "6",
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

        if (currentStepIndex == 5) item {
            ResultArea(uiState = uiState)
        }

        item {
            if (currentStepIndex == 5) {
                SaveActionArea(
                    uiState = uiState,
                    onEvent = onEvent,
                    onPreviousClick = { currentStepIndex = (currentStepIndex - 1).coerceAtLeast(0) },
                )
            } else {
                StepNavigationArea(
                    currentStepIndex = currentStepIndex,
                    onPreviousClick = { currentStepIndex = (currentStepIndex - 1).coerceAtLeast(0) },
                    onNextClick = { currentStepIndex = (currentStepIndex + 1).coerceAtMost(5) },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SelectedDestinationSummary(
    label: String,
    destination: Destination,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = destination.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = destination.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RegistrationStepRow(activeStepIndex: Int) {
    val steps = listOf(
        "1" to "기본 정보",
        "2" to "장소",
        "3" to "도착 목표",
        "4" to "반복",
        "5" to "이동수단",
        "6" to "보정",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            steps.forEachIndexed { index, (number, _) ->
                val isActive = index == activeStepIndex
                Surface(
                    modifier = Modifier.size(26.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (index != steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            steps.forEach { (_, label) ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = if (steps.indexOfFirst { it.second == label } == activeStepIndex) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultArea(uiState: RoutineRegistrationUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (uiState.hasCalculationResult) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (uiState.hasCalculationResult) 4.dp else 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "계산 결과",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (uiState.hasCalculationResult) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            if (uiState.hasCalculationResult) {
                Text(
                    text = "${uiState.recommendedDepartureTimeText} 출발",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                uiState.routeEstimate?.let {
                    MetricRow(
                        label = "예상 이동 시간",
                        value = "${it.estimatedMinutes}분",
                        labelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                        valueColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    MetricRow(
                        label = "개인 보정",
                        value = "${uiState.personalBufferMinutes}분",
                        labelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                        valueColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    MetricRow(
                        label = "안전 여유",
                        value = "${uiState.safetyMarginMinutes}분",
                        labelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                        valueColor = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = it.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    )
                }
            } else {
                Text(
                    text = "출발지, 목적지, 도착 목표를 입력한 뒤 권장 출발 시각을 계산하면 이곳에 결과가 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SaveActionArea(
    uiState: RoutineRegistrationUiState,
    onEvent: (RoutineRegistrationEvent) -> Unit,
    onPreviousClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MessageArea(
            errorMessage = uiState.errorMessage,
            successMessage = uiState.successMessage,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { onEvent(RoutineRegistrationEvent.CalculateClicked) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !uiState.isCalculating,
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (uiState.isCalculating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    MapMateIcon(
                        icon = MapMateIconType.Time,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("권장 출발 시각 계산")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPreviousClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("이전")
                }
                Button(
                    onClick = { onEvent(RoutineRegistrationEvent.SaveClicked) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    enabled = uiState.isSaveEnabled && !uiState.isSaving,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (uiState.isEditing) "수정 완료" else "완료")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepNavigationArea(
    currentStepIndex: Int,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onPreviousClick,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            enabled = currentStepIndex > 0,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("이전")
        }
        Button(
            onClick = onNextClick,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("다음")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineRegistrationScreenPreview() {
    MapMateTheme {
        RoutineRegistrationScreen(
            uiState = RoutineRegistrationUiState(
                routineName = "등교 루틴",
                originQuery = "우리집",
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
            onBackClick = {},
        )
    }
}
