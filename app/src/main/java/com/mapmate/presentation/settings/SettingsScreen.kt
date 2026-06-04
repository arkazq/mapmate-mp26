@file:OptIn(ExperimentalLayoutApi::class)

package com.mapmate.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.ui.theme.MapMateTheme

@Composable
fun SettingsRoute(
    contentPadding: PaddingValues,
    settingsRepository: SettingsRepository,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(settingsRepository),
    )
    val uiState by viewModel.uiState.collectAsState()

    SettingsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = Modifier.padding(contentPadding),
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SettingsHeader()
        }

        item {
            uiState.errorMessage?.let {
                SettingsMessage(message = it)
            }
        }

        item {
            SettingsSection(title = "기본 보정") {
                OutlinedTextField(
                    value = uiState.personalBufferMinutes,
                    onValueChange = { onEvent(SettingsEvent.PersonalBufferChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("개인 보정 시간") },
                    suffix = { Text("분") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = uiState.safetyMarginMinutes,
                    onValueChange = { onEvent(SettingsEvent.SafetyMarginChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("안전 여유") },
                    suffix = { Text("분") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }

        item {
            SettingsSection(title = "기본 이동수단") {
                SettingsTransportSelector(
                    selectedTransportMode = uiState.defaultTransportMode,
                    onTransportModeSelected = {
                        onEvent(SettingsEvent.DefaultTransportModeSelected(it))
                    },
                )
            }
        }

        item {
            SettingsSection(title = "알림") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "출발 알림",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = {
                            onEvent(SettingsEvent.NotificationsEnabledChanged(it))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "MapMate",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "설정",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingsMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsTransportSelector(
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
                    .heightIn(min = 48.dp)
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
                Text(
                    text = transportMode.toSettingsLabel(),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun TransportMode.toSettingsLabel(): String {
    return when (this) {
        TransportMode.TRANSIT -> "대중교통"
        TransportMode.WALK -> "도보"
        TransportMode.CAR -> "자동차"
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MapMateTheme {
        SettingsScreen(
            uiState = SettingsUiState(),
            onEvent = {},
        )
    }
}
