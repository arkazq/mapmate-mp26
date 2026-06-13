package com.mapmate.presentation.prediction

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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.mapmate.presentation.common.DetailTopBar
import com.mapmate.presentation.common.IconBadge
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.NotificationCircle
import com.mapmate.presentation.common.RouteTimeline
import com.mapmate.presentation.common.RouteTimelineItem
import com.mapmate.presentation.common.RouteEstimateStatusMessage
import com.mapmate.presentation.common.RoutineRecommendationUiModel
import com.mapmate.presentation.common.SectionCard
import com.mapmate.presentation.common.toFallbackRecommendationUiModel
import com.mapmate.presentation.common.toKoreanDescription
import com.mapmate.presentation.common.transportModeIcon
import com.mapmate.ui.theme.MapMateTheme
import java.time.LocalTime

@Composable
fun PredictionDetailRoute(
    contentPadding: PaddingValues,
    routine: Routine,
    routeEstimateProvider: RouteEstimateProvider,
    onBackClick: () -> Unit,
    onStartTrackingClick: (Routine) -> Unit,
    onEditRoutineClick: (Routine) -> Unit,
) {
    val viewModel: PredictionDetailViewModel = viewModel(
        key = "prediction-${routine.id ?: routine.name}",
        factory = PredictionDetailViewModel.factory(
            routine = routine,
            routeEstimateProvider = routeEstimateProvider,
        ),
    )
    val uiState by viewModel.uiState.collectAsState()

    PredictionDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onStartTrackingClick = { onStartTrackingClick(routine) },
        onEditRoutineClick = { onEditRoutineClick(routine) },
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun PredictionDetailScreen(
    uiState: PredictionDetailUiState,
    onBackClick: () -> Unit,
    onStartTrackingClick: () -> Unit,
    onEditRoutineClick: () -> Unit,
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
                title = "상세 예측",
                onBackClick = onBackClick,
                trailingContent = { NotificationCircle() },
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
                    PredictionHeroCard(recommendation = recommendation)
                }

                item {
                    CompactCalculationBasisCard(recommendation = recommendation)
                }

                item {
                    PredictionActionButtons(
                        onStartTrackingClick = onStartTrackingClick,
                        onEditRoutineClick = onEditRoutineClick,
                    )
                }

                item {
                    SectionCard(
                        title = "경로 요약",
                        leadingIcon = MapMateIconType.Route,
                    ) {
                        RouteTimeline(
                            items = predictionTimelineItems(recommendation),
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CompactCalculationBasisCard(
    recommendation: RoutineRecommendationUiModel,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = MapMateIconType.Time, modifier = Modifier.size(30.dp))
                Text(
                    text = "계산 근거",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            RouteEstimateStatusMessage(message = recommendation.routeStatusMessage)
            RouteEstimateStatusMessage(message = recommendation.departureStatusMessage)
            CompactCalculationLine(text = "${recommendation.targetArrivalTimeText} 도착 목표")
            CompactCalculationLine(text = "-${recommendation.routeDurationMinutes}분 예상 이동 시간")
            CompactCalculationLine(text = "-${recommendation.personalBufferMinutes}분 개인 보정 시간")
            CompactCalculationLine(text = "-${recommendation.safetyMarginMinutes}분 안전 여유 시간")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            CompactCalculationLine(
                text = "=${recommendation.recommendedDepartureDisplayText} 권장 출발",
                emphasized = true,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FormulaToken(text = recommendation.targetArrivalTimeText)
                    FormulaToken(text = "-")
                    FormulaToken(text = "${recommendation.routeDurationMinutes}분")
                    FormulaToken(text = "-")
                    FormulaToken(text = "${recommendation.personalBufferMinutes + recommendation.safetyMarginMinutes}분")
                    FormulaToken(text = "=")
                    FormulaToken(text = recommendation.recommendedDepartureDisplayText, emphasized = true)
                }
            }
        }
    }
}

@Composable
private fun CompactCalculationLine(
    text: String,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.Normal,
    )
}

@Composable
private fun FormulaToken(
    text: String,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.Bold,
    )
}

@Composable
private fun PredictionActionButtons(
    onStartTrackingClick: () -> Unit,
    onEditRoutineClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onStartTrackingClick,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            MapMateIcon(
                icon = MapMateIconType.Play,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "이동 기록 시작",
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        OutlinedButton(
            onClick = onEditRoutineClick,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            MapMateIcon(
                icon = MapMateIconType.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "루틴 수정",
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun PredictionHeroCard(
    recommendation: RoutineRecommendationUiModel,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "권장 출발 시각",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = recommendation.recommendedDepartureDisplayText,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${recommendation.targetArrivalTimeText} 도착 목표 · ${recommendation.routine.name}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                )
            }
            IconBadge(
                icon = MapMateIconType.Time,
                modifier = Modifier.size(34.dp),
                containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private fun predictionTimelineItems(
    recommendation: RoutineRecommendationUiModel,
): List<RouteTimelineItem> {
    val routine = recommendation.routine
    val movementLabel = routine.transportMode.toKoreanDescription()

    return listOf(
        RouteTimelineItem(
            title = routine.origin.name,
            description = "출발",
            timeText = recommendation.recommendedDepartureDisplayText,
            icon = MapMateIconType.Location,
        ),
        RouteTimelineItem(
            title = movementLabel,
            description = "${recommendation.routeDurationMinutes}분 예상",
            icon = transportModeIcon(routine.transportMode),
        ),
        RouteTimelineItem(
            title = if (routine.transportMode == TransportMode.TRANSIT) "환승" else "이동 중",
            description = if (routine.transportMode == TransportMode.TRANSIT) {
                "환승/도보 포함"
            } else {
                "교통 변동 반영"
            },
            icon = MapMateIconType.Route,
        ),
        RouteTimelineItem(
            title = routine.destination.name,
            description = "도착",
            timeText = recommendation.targetArrivalTimeText,
            icon = MapMateIconType.Flag,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun PredictionDetailScreenPreview() {
    MapMateTheme {
        PredictionDetailScreen(
            uiState = PredictionDetailUiState(
                routine = sampleRoutine,
                recommendation = sampleRoutine.toFallbackRecommendationUiModel(),
                isLoading = false,
            ),
            onBackClick = {},
            onStartTrackingClick = {},
            onEditRoutineClick = {},
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
        name = "숭실대학교 정보과학관",
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
