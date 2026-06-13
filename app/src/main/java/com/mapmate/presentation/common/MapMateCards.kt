@file:OptIn(ExperimentalLayoutApi::class)

package com.mapmate.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.ui.theme.MapMateTheme
import java.time.LocalTime

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String = "MapMate",
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        trailingContent?.let {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                it()
            }
        }
    }
}

@Composable
fun NotificationCircle(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(38.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MapMateIcon(
                icon = MapMateIconType.Notifications,
                contentDescription = "알림",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun DetailTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircleButton(
                icon = MapMateIconType.Back,
                contentDescription = "뒤로",
                onClick = onBackClick,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingText: String? = null,
    leadingIcon: MapMateIconType? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = MapMateElevation.Card),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MapMateSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                val resolvedIcon = leadingIcon ?: leadingText?.toBadgeIcon()
                when {
                    resolvedIcon != null -> IconBadge(icon = resolvedIcon)
                    leadingText != null -> NumberBadge(text = leadingText)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun IconBadge(
    icon: MapMateIconType,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.size(32.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MapMateIcon(
                icon = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
fun IconBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val icon = text.toBadgeIcon()
    if (icon != null) {
        IconBadge(
            icon = icon,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
        )
    } else {
        NumberBadge(
            text = text,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
        )
    }
}

@Composable
private fun NumberBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.size(30.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun IconCircleButton(
    icon: MapMateIconType,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier
            .size(36.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            MapMateIcon(
                icon = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = MapMateElevation.Card),
    ) {
        Column(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(icon = MapMateIconType.Add)
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onActionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                MapMateIcon(
                    icon = MapMateIconType.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    text = actionLabel,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
fun DepartureHeroCard(
    departureTimeText: String,
    targetArrivalText: String,
    title: String,
    reason: String,
    primaryActionLabel: String,
    secondaryActionLabel: String,
    onPrimaryActionClick: () -> Unit,
    onSecondaryActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = MapMateElevation.Hero),
    ) {
        Column(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            icon = MapMateIconType.Time,
                            modifier = Modifier.size(30.dp),
                            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = departureTimeText,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = targetArrivalText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
                    )
                }
                IconBadge(
                    icon = MapMateIconType.Time,
                    modifier = Modifier.size(36.dp),
                    containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.13f),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrimaryActionClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)),
                ) {
                    MapMateIcon(
                        icon = MapMateIconType.Records,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = primaryActionLabel,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = onSecondaryActionClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    MapMateIcon(
                        icon = MapMateIconType.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = secondaryActionLabel,
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun CalculationBreakdownCard(
    routeDurationMinutes: Int,
    personalBufferMinutes: Int,
    safetyMarginMinutes: Int,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "계산 기준",
        subtitle = "도착 목표에서 이동 시간과 여유 시간을 뺍니다.",
        modifier = modifier,
        leadingIcon = MapMateIconType.Time,
    ) {
        MetricRow(icon = MapMateIconType.Bus, label = "예상 이동 시간", value = "${routeDurationMinutes}분")
        MetricRow(icon = MapMateIconType.Person, label = "개인 보정", value = "${personalBufferMinutes}분")
        MetricRow(icon = MapMateIconType.Shield, label = "안전 여유", value = "${safetyMarginMinutes}분")
    }
}

@Composable
fun CalculationFormulaCard(
    targetArrivalTimeText: String,
    routeDurationMinutes: Int,
    personalBufferMinutes: Int,
    safetyMarginMinutes: Int,
    recommendedDepartureTimeText: String,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "계산 근거",
        modifier = modifier,
        leadingIcon = MapMateIconType.Time,
    ) {
        MetricRow(icon = MapMateIconType.Time, label = "$targetArrivalTimeText 도착 목표", value = "")
        MetricRow(icon = MapMateIconType.Bus, label = "-${routeDurationMinutes}분 예상 이동 시간", value = "")
        MetricRow(icon = MapMateIconType.Person, label = "-${personalBufferMinutes}분 개인 보정 시간", value = "")
        MetricRow(icon = MapMateIconType.Shield, label = "-${safetyMarginMinutes}분 안전 여유 시간", value = "")
        MetricRow(icon = MapMateIconType.Check, label = "=${recommendedDepartureTimeText} 권장 출발 시각", value = "")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        ) {
            FlowRow(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FormulaToken(text = targetArrivalTimeText)
                FormulaOperator(text = "-")
                FormulaToken(text = "${routeDurationMinutes}분")
                FormulaOperator(text = "-")
                FormulaToken(text = "${personalBufferMinutes}분")
                FormulaOperator(text = "-")
                FormulaToken(text = "${safetyMarginMinutes}분")
                FormulaOperator(text = "=")
                FormulaToken(
                    text = recommendedDepartureTimeText,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    borderColor = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FormulaToken(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FormulaOperator(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

data class RouteTimelineItem(
    val title: String,
    val description: String,
    val timeText: String? = null,
    val marker: String = "•",
    val icon: MapMateIconType? = null,
)

@Composable
fun RouteTimeline(
    items: List<RouteTimelineItem>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconBadge(
                        icon = item.icon ?: item.marker.toBadgeIcon() ?: MapMateIconType.Route,
                        modifier = Modifier.size(30.dp),
                        containerColor = if (index == 0 || index == items.lastIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (index == 0 || index == items.lastIndex) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    if (index != items.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (index == items.lastIndex) 0.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item.timeText?.let {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineCard(
    recommendation: RoutineRecommendationUiModel,
    modifier: Modifier = Modifier,
    isDeleting: Boolean = false,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    val routine = recommendation.routine

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = MapMateElevation.Card),
    ) {
        Column(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                RoutineAvatar(routine = routine)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = routine.destination.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onEditClick) {
                        MapMateIcon(
                            icon = MapMateIconType.Edit,
                            contentDescription = "수정",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDeleteClick, enabled = !isDeleting) {
                        MapMateIcon(
                            icon = MapMateIconType.Delete,
                            contentDescription = "삭제",
                            modifier = Modifier.size(18.dp),
                            tint = if (isDeleting) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onDetailClick) {
                        MapMateIcon(
                            icon = MapMateIconType.More,
                            contentDescription = "상세",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            RoutineFactRow(icon = MapMateIconType.Time, label = "도착 목표", value = "${recommendation.targetArrivalTimeText} 도착")
            RoutineFactRow(
                icon = MapMateIconType.Records,
                label = "반복",
                value = routine.repeatDays.sortedBy { it.ordinal }.joinToString(", ") { it.toKoreanShortLabel() },
            )
            RoutineFactRow(icon = transportModeIcon(routine.transportMode), label = "교통수단", value = routine.transportMode.toKoreanLabel())

            RouteEstimateStatusMessage(message = recommendation.routeStatusMessage)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDetailClick),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MapMateIcon(
                                icon = MapMateIconType.Time,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "최신 추천 출발",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = recommendation.recommendedDepartureDisplayText,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    MapMateIcon(
                        icon = MapMateIconType.Route,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoutineAvatar(
    routine: Routine,
    modifier: Modifier = Modifier,
) {
    val icon = when {
        routine.name.contains("출근") || routine.name.contains("회사") -> MapMateIconType.Work
        routine.name.contains("학교") || routine.name.contains("등교") || routine.name.contains("school", ignoreCase = true) -> MapMateIconType.School
        else -> transportModeIcon(routine.transportMode)
    }
    IconBadge(
        icon = icon,
        modifier = modifier.size(40.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RoutineFactRow(
    icon: MapMateIconType,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MapMateIcon(
            icon = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            modifier = Modifier.width(68.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun SettingSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingText: String? = null,
    leadingIcon: MapMateIconType? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SectionCard(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        leadingText = leadingText,
        leadingIcon = leadingIcon,
        content = content,
    )
}

@Composable
fun MetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: MapMateIconType? = null,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            IconBadge(
                icon = it,
            modifier = Modifier.size(28.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
        )
        if (value.isNotBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = valueColor,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
fun RouteEstimateStatusMessage(
    message: String?,
    modifier: Modifier = Modifier,
    inverse: Boolean = false,
) {
    val visibleMessage = message?.takeIf(String::isNotBlank) ?: return
    val containerColor = if (inverse) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f)
    }
    val contentColor = if (inverse) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    val borderColor = if (inverse) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = visibleMessage,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun CompactRoutineCard(
    routine: Routine,
    recommendationText: String,
    targetArrivalTimeText: String,
    statusMessage: String? = null,
    modifier: Modifier = Modifier,
    onManageClick: () -> Unit,
    onDetailClick: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = MapMateElevation.Card),
    ) {
        Column(
            modifier = Modifier.padding(MapMateSpacing.CardInner),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "오늘의 루틴",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                OutlinedButton(
                    onClick = onManageClick,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("루틴 관리")
                }
            }
            RouteEstimateStatusMessage(message = statusMessage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDetailClick),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoutineAvatar(routine = routine)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = routine.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$targetArrivalTimeText 도착 목표 · ${routine.destination.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "추천",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = recommendationText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                MapMateIcon(
                    icon = MapMateIconType.Route,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun String.toBadgeIcon(): MapMateIconType? {
    return when (this) {
        "계", "시" -> MapMateIconType.Time
        "길", "출", "중" -> MapMateIconType.Route
        "도" -> MapMateIconType.Flag
        "왜", "완" -> MapMateIconType.Check
        "단", "다", "데" -> MapMateIconType.Records
        "보" -> MapMateIconType.Person
        "이" -> MapMateIconType.Bus
        "알" -> MapMateIconType.Notifications
        else -> null
    }
}

@Preview(showBackground = true)
@Composable
private fun DepartureHeroCardPreview() {
    MapMateTheme {
        DepartureHeroCard(
            departureTimeText = "오늘은 08:07에 출발하세요",
            targetArrivalText = "09:00 도착 목표 · going school",
            title = "권장 출발 시각",
            reason = "기본 예상 이동 시간과 보정 6분 반영",
            primaryActionLabel = "상세 예측 보기",
            secondaryActionLabel = "루틴 수정",
            onPrimaryActionClick = {},
            onSecondaryActionClick = {},
            modifier = Modifier.padding(16.dp),
        ) {
            MetricRow(icon = MapMateIconType.Bus, label = "예상 이동 시간", value = "42분")
            MetricRow(icon = MapMateIconType.Person, label = "개인 보정", value = "6분")
            MetricRow(icon = MapMateIconType.Shield, label = "안전 여유", value = "5분")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoutineCardPreview() {
    MapMateTheme {
        RoutineCard(
            recommendation = sampleRoutine.toFallbackRecommendationUiModel(),
            modifier = Modifier.padding(16.dp),
            onEditClick = {},
            onDeleteClick = {},
            onDetailClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionCardPreview() {
    MapMateTheme {
        SectionCard(
            title = "기본 보정",
            subtitle = "새 루틴에 기본으로 반영합니다.",
            leadingIcon = MapMateIconType.Person,
            modifier = Modifier.padding(16.dp),
        ) {
            MetricRow(icon = MapMateIconType.Person, label = "개인 보정", value = "6분")
            MetricRow(icon = MapMateIconType.Shield, label = "안전 여유", value = "5분")
        }
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
