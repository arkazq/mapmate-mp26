@file:OptIn(ExperimentalLayoutApi::class)

package com.mapmate.presentation.routine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.TransportMode

@Composable
fun SectionBlock(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun DestinationCandidateList(
    candidates: List<Destination>,
    selectedDestination: Destination?,
    onDestinationSelected: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        candidates.forEach { destination ->
            val isSelected = destination == selectedDestination
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onDestinationSelected(destination) },
                shape = MaterialTheme.shapes.medium,
                color = containerColor,
                border = BorderStroke(1.dp, borderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SelectionDot(isSelected = isSelected)
                    Column {
                        Text(
                            text = destination.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = destination.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        RepeatDay.entries.forEach { repeatDay ->
            val isSelected = repeatDay in selectedRepeatDays
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onRepeatDayToggled(repeatDay) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = repeatDay.toKoreanShortLabel(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionDot(isSelected: Boolean) {
    val color = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier.size(18.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(2.dp, color),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    ) {
        if (isSelected) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(6.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.onPrimary,
                    content = {},
                )
            }
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
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransportMode.entries.forEach { transportMode ->
            val isSelected = transportMode == selectedTransportMode
            Surface(
                modifier = Modifier
                    .widthIn(min = 96.dp)
                    .heightIn(min = 56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onTransportModeSelected(transportMode) },
                shape = MaterialTheme.shapes.medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = transportMode.toKoreanLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        text = transportMode.toDescription(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = personalBufferMinutes,
            onValueChange = onPersonalBufferChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("개인 보정 시간") },
            suffix = { Text("분") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = safetyMarginMinutes,
            onValueChange = onSafetyMarginChanged,
            modifier = Modifier.fillMaxWidth(),
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        errorMessage?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "입력 확인 필요",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        successMessage?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "저장 준비 완료",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
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

private fun TransportMode.toDescription(): String {
    return when (this) {
        TransportMode.TRANSIT -> "버스/지하철"
        TransportMode.WALK -> "도보 이동"
        TransportMode.CAR -> "차량 이동"
    }
}
