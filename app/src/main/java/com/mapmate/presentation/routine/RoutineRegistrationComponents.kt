@file:OptIn(ExperimentalLayoutApi::class)

package com.mapmate.presentation.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.TransportMode

@Composable
fun SectionBlock(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
fun DestinationCandidateList(
    candidates: List<Destination>,
    selectedDestination: Destination?,
    onDestinationSelected: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        candidates.forEach { destination ->
            AssistChip(
                onClick = { onDestinationSelected(destination) },
                label = {
                    Column {
                        Text(text = destination.name)
                        Text(
                            text = destination.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                modifier = Modifier,
                leadingIcon = if (destination == selectedDestination) {
                    {
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            content = {},
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
fun RepeatDaySelector(
    selectedRepeatDays: Set<RepeatDay>,
    onRepeatDayToggled: (RepeatDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RepeatDay.entries.forEach { repeatDay ->
            FilterChip(
                selected = repeatDay in selectedRepeatDays,
                onClick = { onRepeatDayToggled(repeatDay) },
                label = { Text(text = repeatDay.toKoreanShortLabel()) },
            )
        }
    }
}

@Composable
fun TransportModeSelector(
    selectedTransportMode: TransportMode,
    onTransportModeSelected: (TransportMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransportMode.entries.forEach { transportMode ->
            FilterChip(
                selected = transportMode == selectedTransportMode,
                onClick = { onTransportModeSelected(transportMode) },
                label = { Text(text = transportMode.toKoreanLabel()) },
            )
        }
    }
}

@Composable
fun MinuteInputRow(
    personalBufferMinutes: String,
    safetyMarginMinutes: String,
    onPersonalBufferChanged: (String) -> Unit,
    onSafetyMarginChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = personalBufferMinutes,
            onValueChange = onPersonalBufferChanged,
            modifier = Modifier.weight(1f),
            label = { Text("개인 버퍼") },
            suffix = { Text("분") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = safetyMarginMinutes,
            onValueChange = onSafetyMarginChanged,
            modifier = Modifier.weight(1f),
            label = { Text("안전 여유") },
            suffix = { Text("분") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
fun MessageArea(
    errorMessage: String?,
    successMessage: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        errorMessage?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        successMessage?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = it,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

fun RepeatDay.toKoreanShortLabel(): String {
    return when (this) {
        RepeatDay.MONDAY -> "월"
        RepeatDay.TUESDAY -> "화"
        RepeatDay.WEDNESDAY -> "수"
        RepeatDay.THURSDAY -> "목"
        RepeatDay.FRIDAY -> "금"
        RepeatDay.SATURDAY -> "토"
        RepeatDay.SUNDAY -> "일"
    }
}

fun TransportMode.toKoreanLabel(): String {
    return when (this) {
        TransportMode.TRANSIT -> "대중교통"
        TransportMode.WALK -> "도보"
        TransportMode.CAR -> "자동차"
    }
}
