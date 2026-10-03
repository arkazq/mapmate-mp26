package com.mapmate.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmate.domain.model.AlarmAccessState
import com.mapmate.domain.provider.AlarmAccessProvider
import com.mapmate.domain.provider.AlarmSettingsDestination
import com.mapmate.presentation.settings.SettingsRoute
import com.mapmate.ui.theme.MapMateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSystemAccessUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun systemSettingsFailureIsVisibleAndSuccessfulRetryClearsItWithoutErasingPreference() {
        val data = NavigationTestData()
        data.settings.value = data.settings.value.copy(notificationsEnabled = true)
        val destinations = mutableListOf<AlarmSettingsDestination>()
        val access = object : AlarmAccessProvider {
            override fun read() = AlarmAccessState(runtimeNotificationGranted = false, exactAlarmAllowed = false)
            override fun openSettings(destination: AlarmSettingsDestination): Boolean {
                destinations += destination
                return destination == AlarmSettingsDestination.EXACT_ALARM
            }
        }
        compose.setContent { MapMateTheme { SettingsRoute(PaddingValues(), data.settingsRepository, access) } }
        compose.onNodeWithText("Android에서 출발 알림이 차단되어 있습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("시스템 알림 설정").performScrollTo().performClick()
        compose.onNodeWithText("이 기기에서 시스템 설정을 열지 못했습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("정확한 알람 허용").performScrollTo().performClick()
        compose.onNodeWithText("이 기기에서 시스템 설정을 열지 못했습니다.").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(AlarmSettingsDestination.NOTIFICATIONS, AlarmSettingsDestination.EXACT_ALARM), destinations)
            assertTrue(data.settings.value.notificationsEnabled)
        }
    }
}
