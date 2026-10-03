package com.mapmate.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.Routine

@Composable
fun RouteEndpoints(routine: Routine, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Endpoint("출발", routine.origin.name, MaterialTheme.colorScheme.tertiary)
        Endpoint("도착", routine.destination.name, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Endpoint(label: String, name: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

internal fun RouteSegment.isWalking(): Boolean = segmentType in setOf(
    RouteSegmentType.WALK_TO_TRANSIT, RouteSegmentType.TRANSFER_WALK, RouteSegmentType.WALK_TO_DESTINATION,
)

internal fun RouteSegment.isTransit(): Boolean = segmentType == RouteSegmentType.BUS_RIDE || segmentType == RouteSegmentType.SUBWAY_RIDE

internal fun RouteSegment.journeyLabel(): String = when (segmentType) {
    RouteSegmentType.BUS_RIDE -> routeName?.takeIf(String::isNotBlank)?.let {
        when { it.contains("버스") -> it; it.endsWith("번") -> "$it 버스"; else -> "${it}번 버스" }
    } ?: "버스"
    RouteSegmentType.SUBWAY_RIDE -> routeName?.takeIf(String::isNotBlank) ?: "지하철"
    RouteSegmentType.WALK_TO_TRANSIT -> "정류장·역까지 도보"
    RouteSegmentType.TRANSFER_WALK -> "환승 이동"
    RouteSegmentType.WALK_TO_DESTINATION -> "목적지까지 도보"
    RouteSegmentType.WAIT_FOR_BUS -> "버스 대기"
    RouteSegmentType.WAIT_FOR_SUBWAY -> "지하철 대기"
    RouteSegmentType.UNKNOWN -> "이동 구간"
}

@Composable
internal fun RouteSegment.journeyColor(): Color {
    val dark = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    return when (segmentType) {
        RouteSegmentType.BUS_RIDE -> if (dark) Color(0xFF7EB4FF) else Color(0xFF1767D2)
        RouteSegmentType.SUBWAY_RIDE -> if (dark) Color(0xFF72D3AC) else Color(0xFF07845B)
        RouteSegmentType.WAIT_FOR_BUS, RouteSegmentType.WAIT_FOR_SUBWAY -> if (dark) Color(0xFFF2C46F) else Color(0xFF996100)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyOverview(recommendation: RoutineRecommendationUiModel, modifier: Modifier = Modifier) {
    val segments = recommendation.routeSegments.sortedBy { it.segmentIndex }
    val transit = segments.filter { it.isTransit() }
    val walkMinutes = segments.filter { it.isWalking() }.sumOf { it.plannedDurationMinutes.coerceAtLeast(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${recommendation.routeDurationMinutes}분", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(if (recommendation.isFallbackEstimate) "기본 예상" else "예상 이동", Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (transit.isNotEmpty()) Text("환승 ${(transit.size - 1).coerceAtLeast(0)}회", Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (segments.any { it.isWalking() }) Text("도보 ${walkMinutes}분", Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (segments.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                .semantics { contentDescription = segments.joinToString(", ") { "${it.journeyLabel()} ${it.plannedDurationMinutes}분" } },
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                segments.forEach { segment ->
                    Box(Modifier.weight(segment.plannedDurationMinutes.coerceAtLeast(1).toFloat())
                        .fillMaxHeight().background(segment.journeyColor()))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                segments.forEach { segment ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        MapMateIcon(if (segment.isWalking()) MapMateIconType.Walk
                            else if (segment.segmentType == RouteSegmentType.SUBWAY_RIDE) MapMateIconType.Train
                            else if (segment.isTransit()) MapMateIconType.Bus else MapMateIconType.Time,
                            null, Modifier.size(16.dp), segment.journeyColor())
                        Text(if (segment.isTransit()) segment.journeyLabel() else "${segment.plannedDurationMinutes}분",
                            style = MaterialTheme.typography.labelMedium, color = segment.journeyColor())
                    }
                }
            }
        }
    }
}

@Composable
fun JourneyItinerary(recommendation: RoutineRecommendationUiModel, modifier: Modifier = Modifier) {
    val segments = recommendation.routeSegments.sortedBy { it.segmentIndex }
    if (segments.isEmpty()) {
        RouteTimeline(recommendation.routeTimelineItems(), modifier)
        return
    }
    Column(modifier.fillMaxWidth()) {
        ItineraryStop(recommendation.routine.origin.name, "${recommendation.departureWithDateText()} 출발 권장", false)
        segments.forEach { segment ->
            val color = segment.journeyColor()
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(color.copy(alpha = 0.5f)))
                    Box(Modifier.padding(top = 18.dp).size(12.dp).background(color, CircleShape))
                }
                Column(Modifier.weight(1f).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(segment.journeyLabel(), style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
                    Text(listOfNotNull(segment.startName, segment.endName).filter(String::isNotBlank).joinToString(" → "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("예상 ${segment.plannedDurationMinutes}분", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ItineraryStop(recommendation.routine.destination.name, "${recommendation.arrivalWithDateText()} 도착 목표", true)
    }
}

@Composable
private fun ItineraryStop(name: String, time: String, arrival: Boolean) {
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        MapMateIcon(if (arrival) MapMateIconType.Flag else MapMateIconType.Location, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
