package com.mapmate.presentation.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.presentation.common.MapMateIcon
import com.mapmate.presentation.common.MapMateIconType
import com.mapmate.presentation.common.MapMateSpacing
import com.mapmate.presentation.common.NotificationCircle
import com.mapmate.presentation.common.ScreenHeader
import com.mapmate.presentation.common.SettingSectionCard
import com.mapmate.presentation.common.TransportModeSelector
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
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onEvent(
            if (granted) {
                SettingsEvent.NotificationsEnabledChanged(true)
            } else {
                SettingsEvent.NotificationPermissionDenied
            },
        )
    }

    LaunchedEffect(uiState.notificationsEnabled) {
        if (uiState.notificationsEnabled && !context.canPostNotifications()) {
            viewModel.onEvent(SettingsEvent.NotificationPermissionDenied)
        }
    }

    fun handleSettingsEvent(event: SettingsEvent) {
        if (event is SettingsEvent.NotificationsEnabledChanged &&
            event.enabled &&
            !context.canPostNotifications()
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
    )
}

private fun Context.canPostNotifications(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                subtitle = "루틴 설정과 알림, 데이터 관리를 관리하세요.",
                trailingContent = { NotificationCircle() },
            )
        }

        uiState.errorMessage?.let { message ->
            item {
                SettingsMessage(message = message)
            }
        }

        item {
            SettingSectionCard(
                title = "기본 보정",
                leadingIcon = MapMateIconType.Person,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = uiState.personalBufferMinutes,
                        onValueChange = { onEvent(SettingsEvent.PersonalBufferChanged(it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("개인 보정") },
                        suffix = { Text("분") },
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true,
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }

        item {
            SettingSectionCard(
                title = "기본 이동수단",
                leadingIcon = MapMateIconType.Bus,
            ) {
                TransportModeSelector(
                    selectedTransportMode = uiState.defaultTransportMode,
                    onTransportModeSelected = {
                        onEvent(SettingsEvent.DefaultTransportModeSelected(it))
                    },
                )
            }
        }

        item {
            SettingSectionCard(
                title = "알림",
                leadingIcon = MapMateIconType.Notifications,
            ) {
                SettingsSwitchRow(
                    title = "출발 알림",
                    description = "설정한 시간에 출발 알림을 받습니다.",
                    checked = uiState.notificationsEnabled,
                    enabled = true,
                    onCheckedChange = {
                        onEvent(SettingsEvent.NotificationsEnabledChanged(it))
                    },
                )
                // TODO: 실시간 도착정보 provider가 추가되면 별도 변경 알림 설정으로 승격한다.
                SettingsSwitchRow(
                    title = "추천 시간 재계산 알림",
                    description = "교통 상황 변화 시 새로운 추천 시간을 알려드립니다.",
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
        }

        item {
            SettingSectionCard(
                title = "데이터 및 기록",
                leadingIcon = MapMateIconType.Records,
            ) {
                DisabledSettingsItem(
                    title = "기록 보기",
                    description = "이동 기록과 루틴 통계를 확인합니다.",
                )
                DisabledSettingsItem(
                    title = "루틴 초기화",
                    description = "모든 루틴 설정을 초기 상태로 되돌립니다.",
                )
                DisabledSettingsItem(
                    title = "앱 정보",
                    description = "버전 정보 및 서비스 정책을 확인합니다.",
                )
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
private fun DisabledSettingsItem(
    title: String,
    description: String,
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
            MapMateIcon(
                icon = MapMateIconType.Route,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MapMateTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                personalBufferMinutes = "6",
                safetyMarginMinutes = "5",
                defaultTransportMode = TransportMode.TRANSIT,
            ),
            onEvent = {},
        )
    }
}
