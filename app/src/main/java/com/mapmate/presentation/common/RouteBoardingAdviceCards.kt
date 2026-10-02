package com.mapmate.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteBoardingAlternative
import com.mapmate.domain.model.RouteBoardingStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoardingAdviceSummaryCard(
    advice: RouteBoardingAdvice,
    modifier: Modifier = Modifier,
) {
    if (advice.status == RouteBoardingStatus.NO_FIRST_BUS) {
        Text("실시간 탑승 정보 없음", modifier, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = MapMateIconType.Bus,
                    modifier = Modifier.size(28.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = advice.status.contentColor(),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = advice.routeName.busRouteLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = advice.stationName.orEmpty().ifBlank { "첫 탑승 정류장" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text("버스 도착", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(advice.realtimeWaitMinutes?.let { "${it}분 후" } ?: "정보 없음", fontSize = 24.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold, color = if (advice.realtimeWaitMinutes != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                advice.accessMinutes?.let {
                    Column {
                        Text("정류장까지", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("도보 ${it}분", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Column {
                    Text("탑승 여유", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(advice.slackMinutes.slackText() ?: "확인 전", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = advice.status.contentColor())
                }
            }
            Text(advice.statusLabel(), style = MaterialTheme.typography.labelMedium,
                color = advice.status.contentColor(), fontWeight = FontWeight.Bold)
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("첫 탑승", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        BoardingAdviceSummaryCard(advice)
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


@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoardingAlternativeRow(
    alternative: RouteBoardingAlternative,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = if (alternative.status == RouteBoardingStatus.NO_FIRST_BUS) "다른 이동 경로" else alternative.routeName.busRouteLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
}


private fun RouteBoardingAdvice.statusLabel(): String = status.statusLabel()

internal fun RouteBoardingAdvice.safeDepartureMessage(): String? {
    if (realtimeWaitMinutes == null || status in setOf(RouteBoardingStatus.REALTIME_UNAVAILABLE, RouteBoardingStatus.NO_FIRST_BUS)) return null
    if (status == RouteBoardingStatus.MISS_RISK) return "지금 출발해도 첫 버스를 놓칠 수 있어요."
    val safeDepartureText = safeDepartureEpochMillis.toTimeText() ?: return null
    val earlyMinutes = earlyDepartureRequiredMinutes?.takeIf { it > 0 } ?: return null
    return "${safeDepartureText} 출발 권장 · ${earlyMinutes}분 앞당김"
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
private fun RouteBoardingStatus.contentColor(): Color {
    return when (this) {
        RouteBoardingStatus.MISS_RISK -> MaterialTheme.colorScheme.error
        RouteBoardingStatus.TIGHT -> if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color(0xFFF2C46F) else Color(0xFF996100)
        RouteBoardingStatus.BOARDABLE -> MaterialTheme.colorScheme.tertiary
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
