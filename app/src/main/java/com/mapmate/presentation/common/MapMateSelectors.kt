@file:OptIn(ExperimentalLayoutApi::class)

package com.mapmate.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.TransportMode

@Composable
fun DayOfWeekSelector(
    selectedRepeatDays: Set<RepeatDay>,
    onRepeatDayToggled: (RepeatDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RepeatDay.entries.forEach { repeatDay ->
            val isSelected = repeatDay in selectedRepeatDays
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .clickable { onRepeatDayToggled(repeatDay) },
                shape = MaterialTheme.shapes.extraLarge,
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
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
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
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = LocalDensity.current.fontScale >= 1.5f || maxWidth < 300.dp
        if (stacked) {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportMode.entries.forEach { mode ->
                    TransportModeOption(mode, mode == selectedTransportMode, true,
                        { onTransportModeSelected(mode) }, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportMode.entries.forEach { mode ->
                    TransportModeOption(mode, mode == selectedTransportMode, false,
                        { onTransportModeSelected(mode) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TransportModeOption(mode: TransportMode, selected: Boolean, stacked: Boolean,
    onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp).clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        if (stacked) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapMateIcon(transportModeIcon(mode), null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    TransportModeLabels(mode)
                }
                SelectionDot(selected)
            }
        } else {
            Box {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    MapMateIcon(transportModeIcon(mode), null, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
                    TransportModeLabels(mode)
                }
                SelectionDot(selected, Modifier.align(Alignment.TopEnd).padding(7.dp))
            }
        }
    }
}

@Composable
private fun TransportModeLabels(mode: TransportMode) {
    Text(mode.toKoreanLabel(), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    Text(mode.toKoreanDescription(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SelectionDot(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(18.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 2.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        if (isSelected) {
            Box(contentAlignment = Alignment.Center) {
                MapMateIcon(
                    icon = MapMateIconType.Check,
                    contentDescription = null,
                    modifier = Modifier.size(11.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
