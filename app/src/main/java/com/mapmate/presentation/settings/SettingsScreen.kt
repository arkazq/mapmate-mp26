package com.mapmate.presentation.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.model.AlarmAccessState
import com.mapmate.domain.provider.AlarmAccessProvider
import com.mapmate.domain.provider.AlarmSettingsDestination
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.presentation.common.TransportModeSelector
import com.mapmate.ui.theme.MapMateTheme

@Composable
fun SettingsRoute(
    contentPadding: PaddingValues,
    settingsRepository: SettingsRepository,
    alarmAccessProvider: AlarmAccessProvider,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(settingsRepository),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var alarmAccess by remember { mutableStateOf(alarmAccessProvider.read()) }
    var systemSettingsError by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        alarmAccess = alarmAccessProvider.read()
        viewModel.onEvent(
            if (granted) {
                SettingsEvent.NotificationsEnabledChanged(true)
            } else {
                SettingsEvent.NotificationPermissionDenied
            },
        )
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        alarmAccess = alarmAccessProvider.read()
    }

    fun openSystemSettings(destination: AlarmSettingsDestination) {
        systemSettingsError = if (alarmAccessProvider.openSettings(destination)) null
            else "이 기기에서 시스템 설정을 열지 못했습니다."
    }

    fun handleSettingsEvent(event: SettingsEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            event is SettingsEvent.NotificationsEnabledChanged &&
            event.enabled &&
            !alarmAccess.runtimeNotificationGranted
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        viewModel.onEvent(event)
    }

    SettingsScreen(
        uiState = uiState,
        onEvent = { handleSettingsEvent(it) },
        modifier = Modifier.padding(contentPadding),
        onRetry = viewModel::retry,
        alarmAccess = alarmAccess,
        systemSettingsError = systemSettingsError,
        onOpenNotificationSettings = { openSystemSettings(AlarmSettingsDestination.NOTIFICATIONS) },
        onOpenExactAlarmSettings = { openSystemSettings(AlarmSettingsDestination.EXACT_ALARM) },
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    alarmAccess: AlarmAccessState = AlarmAccessState(),
    systemSettingsError: String? = null,
    onOpenNotificationSettings: () -> Unit = {},
    onOpenExactAlarmSettings: () -> Unit = {},
) {
    if (!uiState.isSettingsAvailable) {
        Column(modifier.fillMaxSize().padding(MapMateSpacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("설정", style = MaterialTheme.typography.headlineSmall)
            if (uiState.isLoading) CircularProgressIndicator()
            uiState.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!uiState.isLoading) TextButton(onClick = onRetry) { Text("다시 시도") }
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = MapMateSpacing.ScreenHorizontal,
            vertical = MapMateSpacing.ScreenTop,
        ),
        verticalArrangement = Arrangement.spacedBy(MapMateSpacing.Section),
    ) {
        item {
            ScreenHeader(
                title = "설정",
                subtitle = "",
            )
        }

        uiState.errorMessage?.let { message ->
            item {
                SettingsMessage(message = message)
            }
        }

        item {
            SettingsSection(title = "기본 보정") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.personalBufferMinutes,
                        onValueChange = { onEvent(SettingsEvent.PersonalBufferChanged(it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("개인 보정") },
                        suffix = { Text("분") },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        enabled = !uiState.isSavingBufferDefaults,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = uiState.safetyMarginMinutes,
                        onValueChange = { onEvent(SettingsEvent.SafetyMarginChanged(it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("안전 여유") },
                        suffix = { Text("분") },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
                        enabled = !uiState.isSavingBufferDefaults,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Button(
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { onEvent(SettingsEvent.SaveBufferDefaultsClicked) },
                    enabled = uiState.hasUnsavedBufferDefaults && !uiState.isSavingBufferDefaults,
                ) { Text(if (uiState.isSavingBufferDefaults) "저장 중" else "기본 보정 저장") }
            }
        }

        item {
            SettingsSection(title = "기본 이동수단") {
                TransportModeSelector(
                    selectedTransportMode = uiState.defaultTransportMode,
                    onTransportModeSelected = {
                        onEvent(SettingsEvent.DefaultTransportModeSelected(it))
                    },
                )
            }
        }

        item {
            SettingsSection(title = "알림") {
                SettingsSwitchRow(
                    title = "출발 알림",
                    description = if (alarmAccess.canPostDeparture) "Android 알림 허용됨" else "Android에서 출발 알림이 차단되어 있습니다.",
                    checked = uiState.notificationsEnabled,
                    enabled = true,
                    onCheckedChange = {
                        onEvent(SettingsEvent.NotificationsEnabledChanged(it))
                    },
                )
                SettingsSwitchRow(
                    title = "출발 전 상태 알림",
                    description = if (alarmAccess.canPostStatus) "출발 30분 전부터 표시" else "Android에서 상태 알림이 차단되어 있습니다.",
                    checked = uiState.notificationsEnabled &&
                        uiState.predepartureStatusNotificationEnabled,
                    enabled = uiState.notificationsEnabled,
                    onCheckedChange = {
                        onEvent(SettingsEvent.PredepartureStatusNotificationEnabledChanged(it))
                    },
                )
                TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("시스템 알림 설정")
                }
                Text(
                    if (alarmAccess.exactAlarmAllowed) "정확한 출발 알림 허용됨" else "정확한 알람 권한이 없어 출발 알림이 늦어질 수 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!alarmAccess.exactAlarmAllowed) {
                    TextButton(onClick = onOpenExactAlarmSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("정확한 알람 허용")
                    }
                }
                systemSettingsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
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
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MapMateTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                personalBufferMinutes = "6",
                safetyMarginMinutes = "5",
                notificationsEnabled = true,
                predepartureStatusNotificationEnabled = true,
                defaultTransportMode = TransportMode.TRANSIT,
            ),
            onEvent = {},
        )
    }
}
