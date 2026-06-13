package com.mapmate.presentation.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.presentation.common.EmptyStateCard
import com.mapmate.presentation.common.IconBadge
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.MetricRow
import com.mapmate.presentation.common.NotificationCircle
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.presentation.common.SectionCard
import com.mapmate.presentation.common.toKoreanLabel
import com.mapmate.presentation.common.transportModeIcon
import com.mapmate.ui.theme.MapMateTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecordsRoute(
    contentPadding: PaddingValues,
    commuteRecordRepository: CommuteRecordRepository,
    onRegisterRoutineClick: () -> Unit,
) {
    val viewModel: RecordsViewModel = viewModel(
        factory = RecordsViewModel.factory(commuteRecordRepository),
    )
    val uiState by viewModel.uiState.collectAsState()

    RecordsScreen(
        uiState = uiState,
        contentPadding = contentPadding,
        onRegisterRoutineClick = onRegisterRoutineClick,
    )
}

@Composable
fun RecordsScreen(
    uiState: RecordsUiState,
    contentPadding: PaddingValues,
    onRegisterRoutineClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            horizontal = MapMateSpacing.ScreenHorizontal,
            vertical = MapMateSpacing.ScreenTop,
        ),
        verticalArrangement = Arrangement.spacedBy(MapMateSpacing.Section),
    ) {
        item {
            ScreenHeader(
                title = "이동 기록",
                subtitle = "완료한 이동 기록을 확인하고 추천 정확도를 점검합니다.",
                trailingContent = { NotificationCircle() },
            )
        }

        when {
            uiState.isLoading -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            uiState.records.isEmpty() -> {
                item {
                    EmptyStateCard(
                        title = "아직 저장된 이동 기록이 없습니다",
                        message = "상세 예측에서 이동 기록을 시작하면 기록이 여기에 표시됩니다.",
                        actionLabel = "루틴 등록하기",
                        onActionClick = onRegisterRoutineClick,
                    )
                }
            }

            else -> {
                item {
                    RecordsStatsCard(stats = uiState.stats)
                }

                items(
                    items = uiState.records,
                    key = { it.id ?: it.arrivedAtEpochMillis },
                ) { record ->
                    CommuteRecordCard(record = record)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RecordsStatsCard(
    stats: RecordsStats,
) {
    SectionCard(
        title = "기록 분석",
        subtitle = "최근 저장 기록을 기준으로 추천 정확도를 요약합니다.",
        leadingIcon = MapMateIconType.Records,
    ) {
        MetricRow(
            icon = MapMateIconType.Check,
            label = "전체 기록",
            value = "${stats.totalRecords}회",
        )
        MetricRow(
            icon = MapMateIconType.Time,
            label = "평균 도착 오차",
            value = stats.averageArrivalDeltaMinutes.toSignedDeltaText(),
            valueColor = stats.averageArrivalDeltaMinutes.toDeltaColor(),
        )
        MetricRow(
            icon = MapMateIconType.Flag,
            label = "정시/빠른 도착률",
            value = "${stats.onTimeRatePercent}%",
        )
        MetricRow(
            icon = MapMateIconType.Notifications,
            label = "늦은 도착",
            value = "${stats.lateRecords}회",
            valueColor = if (stats.lateRecords > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
        MetricRow(
            icon = stats.mostUsedTransportMode?.let(::transportModeIcon) ?: MapMateIconType.Route,
            label = "자주 쓴 이동수단",
            value = stats.mostUsedTransportMode?.toKoreanLabel() ?: "기록 없음",
        )
        MetricRow(
            icon = MapMateIconType.Route,
            label = "최근 5회 평균",
            value = stats.recentAverageDeltaMinutes.toSignedDeltaText(),
            valueColor = stats.recentAverageDeltaMinutes.toDeltaColor(),
        )
    }
}

@Composable
private fun CommuteRecordCard(
    record: CommuteRecord,
) {
    SectionCard(
        title = record.routineName,
        subtitle = record.arrivedAtEpochMillis.toDateTimeText(),
        leadingIcon = MapMateIconType.Records,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = transportModeIcon(record.transportMode),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${record.originName} → ${record.destinationName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${record.transportMode.toKoreanLabel()} · ${record.routeDurationMinutes}분 예상",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MetricRow(
            icon = MapMateIconType.Time,
            label = "추천 출발",
            value = record.recommendedDepartureTime.toDisplayText(),
        )
        MetricRow(
            icon = MapMateIconType.Flag,
            label = "목표 도착",
            value = record.targetArrivalTime.toDisplayText(),
        )
        MetricRow(
            icon = MapMateIconType.Check,
            label = "도착 오차",
            value = record.arrivalDeltaMinutes.toDeltaText(),
            valueColor = if (record.arrivalDeltaMinutes > 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

private fun Long.toDateTimeText(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
}

private fun LocalTime.toDisplayText(): String {
    return format(DateTimeFormatter.ofPattern("HH:mm"))
}

private fun Int.toDeltaText(): String {
    return when {
        this > 0 -> "+${this}분 늦음"
        this < 0 -> "${kotlin.math.abs(this)}분 빠름"
        else -> "정시"
    }
}

@Composable
private fun Int.toDeltaColor() = if (this > 0) {
    MaterialTheme.colorScheme.error
} else {
    MaterialTheme.colorScheme.primary
}

private fun Int.toSignedDeltaText(): String {
    return when {
        this > 0 -> "+${this}분"
        this < 0 -> "${this}분"
        else -> "0분"
    }
}

@Preview(showBackground = true)
@Composable
private fun RecordsScreenPreview() {
    MapMateTheme {
        RecordsScreen(
            uiState = RecordsUiState(
                records = listOf(sampleRecord),
                isLoading = false,
                stats = RecordsStats.from(listOf(sampleRecord)),
            ),
            contentPadding = PaddingValues(),
            onRegisterRoutineClick = {},
        )
    }
}

private val sampleRecord = CommuteRecord(
    routineId = 1L,
    routineName = "going school",
    originName = "서울역",
    destinationName = "숭실대학교",
    transportMode = TransportMode.TRANSIT,
    targetArrivalTime = LocalTime.of(9, 0),
    recommendedDepartureTime = LocalTime.of(8, 7),
    routeDurationMinutes = 42,
    routeSummary = "대중교통 기준 42분 예상",
    startedAtEpochMillis = 1_800_000L,
    arrivedAtEpochMillis = 2_000_000L,
    arrivalDeltaMinutes = 2,
)
