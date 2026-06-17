package com.mapmate.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteBoardingAlternative
import com.mapmate.domain.model.RouteBoardingStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BoardingAdviceSummaryCard(
    advice: RouteBoardingAdvice,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = advice.status.containerColor().copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = MapMateIconType.Bus,
                    modifier = Modifier.size(28.dp),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    contentColor = advice.status.contentColor(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "추천: ${advice.routeName.busRouteLabel()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = advice.summaryDetailText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = advice.statusLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = advice.status.contentColor(),
                fontWeight = FontWeight.Bold,
            )
            advice.safeDepartureMessage()?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            advice.targetArrivalWarningText()?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun BoardingAdviceDetailCard(
    advice: RouteBoardingAdvice,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "탑승 판단",
        subtitle = "ODsay 후보 중 첫 버스를 실제로 탈 수 있는지 기준으로 비교했습니다.",
        leadingIcon = MapMateIconType.Bus,
        modifier = modifier,
    ) {
        BoardingAdviceSelectedRow(advice = advice)
        if (advice.alternatives.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = "대안 후보",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                advice.alternatives.forEach { alternative ->
                    BoardingAlternativeRow(alternative = alternative)
                }
            }
        }
    }
}

@Composable
private fun BoardingAdviceSelectedRow(
    advice: RouteBoardingAdvice,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = advice.routeName.busRouteLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = advice.stationName.orEmpty().ifBlank { "첫 탑승 정류장" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "후보 ${advice.selectedCandidateIndex}/${advice.candidateCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        BoardingMetricRow(
            accessMinutes = advice.accessMinutes,
            realtimeWaitMinutes = advice.realtimeWaitMinutes,
            slackMinutes = advice.slackMinutes,
            status = advice.status,
            estimatedTotalMinutes = advice.estimatedTotalMinutes,
        )
        advice.safeDepartureMessage()?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        advice.targetArrivalWarningText()?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BoardingAlternativeRow(
    alternative: RouteBoardingAlternative,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = alternative.routeName.busRouteLabel(),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = alternative.compactStatusText(),
            style = MaterialTheme.typography.bodySmall,
            color = alternative.status.contentColor(),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${alternative.estimatedTotalMinutes}분",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoardingMetricRow(
    accessMinutes: Int?,
    realtimeWaitMinutes: Int?,
    slackMinutes: Int?,
    status: RouteBoardingStatus,
    estimatedTotalMinutes: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = listOfNotNull(
                accessMinutes?.let { "정류장까지 ${it}분" },
                realtimeWaitMinutes?.let { "버스 ${it}분 후 도착" } ?: "실시간 도착정보 없음",
                slackMinutes.slackText(),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "${status.statusLabel()} · 예상 ${estimatedTotalMinutes}분",
            style = MaterialTheme.typography.labelLarge,
            color = status.contentColor(),
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun RouteBoardingAdvice.summaryDetailText(): String {
    return listOfNotNull(
        stationName?.takeIf(String::isNotBlank),
        accessMinutes?.let { "정류장까지 ${it}분" },
        realtimeWaitMinutes?.let { "버스 ${it}분 후 도착" },
        slackMinutes.slackText(),
    ).joinToString(" · ")
}

private fun RouteBoardingAdvice.statusLabel(): String = status.statusLabel()

private fun RouteBoardingAdvice.safeDepartureMessage(): String? {
    val safeDepartureText = safeDepartureEpochMillis.toTimeText() ?: return null
    val earlyMinutes = earlyDepartureRequiredMinutes?.takeIf { it > 0 } ?: return null
    return "${safeDepartureText}까지 출발해야 여유 있게 탑승 가능 · ${earlyMinutes}분 앞당김"
}

private fun RouteBoardingAdvice.targetArrivalWarningText(): String? {
    return if (mayMissTargetArrival) {
        "지금 출발해도 목표 시각보다 늦을 수 있어요."
    } else {
        null
    }
}

private fun RouteBoardingAlternative.compactStatusText(): String {
    return slackMinutes.slackText() ?: status.statusLabel()
}

private fun RouteBoardingStatus.statusLabel(): String {
    return when (this) {
        RouteBoardingStatus.BOARDABLE -> "탑승 여유 충분"
        RouteBoardingStatus.TIGHT -> "탑승 여유 적음"
        RouteBoardingStatus.MISS_RISK -> "놓칠 가능성 높음"
        RouteBoardingStatus.REALTIME_UNAVAILABLE -> "실시간 정보 없음"
        RouteBoardingStatus.NO_FIRST_BUS -> "첫 버스 후보 없음"
    }
}

@Composable
private fun RouteBoardingStatus.containerColor(): Color {
    return when (this) {
        RouteBoardingStatus.MISS_RISK -> MaterialTheme.colorScheme.errorContainer
        RouteBoardingStatus.TIGHT -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
}

@Composable
private fun RouteBoardingStatus.contentColor(): Color {
    return when (this) {
        RouteBoardingStatus.MISS_RISK -> MaterialTheme.colorScheme.error
        RouteBoardingStatus.TIGHT -> MaterialTheme.colorScheme.primary
        RouteBoardingStatus.BOARDABLE -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun Int?.slackText(): String? {
    return this?.let {
        if (it >= 0) {
            "여유 ${it}분"
        } else {
            "${-it}분 부족"
        }
    }
}

private fun Long?.toTimeText(): String? {
    val epochMillis = this ?: return null
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter)
}

private fun String?.busRouteLabel(): String {
    val routeName = this?.trim().orEmpty()
    if (routeName.isBlank()) return "첫 버스"
    if (routeName.contains("버스")) return routeName
    if (routeName.endsWith("번")) return "$routeName 버스"
    return "${routeName}번 버스"
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
