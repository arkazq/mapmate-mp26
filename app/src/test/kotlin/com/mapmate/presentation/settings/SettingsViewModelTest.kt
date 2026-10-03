package com.mapmate.presentation.settings

import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestSettingsRepository
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun deniedSystemPermissionDoesNotEraseStoredUserNotificationPreference() = runTest {
        val repository = TestSettingsRepository()
        repository.updateNotificationsEnabled(true)
        val model = SettingsViewModel(repository)
        runCurrent()
        model.onEvent(SettingsEvent.NotificationPermissionDenied)
        runCurrent()
        assertEquals(true, repository.settings.value.notificationsEnabled)
        assertEquals(true, model.uiState.value.notificationsEnabled)
        assertNotNull(model.uiState.value.errorMessage)
    }

    @Test fun failedNotificationSaveRestoresPersistedToggle() = runTest {
        val original = TestSettingsRepository()
        val repository = object : SettingsRepository by original {
            override suspend fun updateNotificationsEnabled(enabled: Boolean) = error("disk full")
        }
        val model = SettingsViewModel(repository)
        runCurrent()
        model.onEvent(SettingsEvent.NotificationsEnabledChanged(true))
        runCurrent()
        assertEquals(original.settings.value.notificationsEnabled, model.uiState.value.notificationsEnabled)
        assertNotNull(model.uiState.value.errorMessage)
    }

    @Test fun settingsReadFailureShowsErrorInsteadOfThrowingOnMainThread() = runTest {
        val repository = object : SettingsRepository by TestSettingsRepository() {
            override val settings = flow<com.mapmate.domain.model.AppSettings> { error("unreadable settings") }
        }
        val model = SettingsViewModel(repository)
        runCurrent()
        assertNotNull(model.uiState.value.errorMessage)
        assertEquals(false, model.uiState.value.isSettingsAvailable)
    }

    @Test fun numericDraftIsNotSavedPerKeystrokeAndUnrelatedToggleDoesNotOverwriteIt() = runTest {
        val repository = TestSettingsRepository()
        val original = repository.settings.value
        val model = SettingsViewModel(repository)
        runCurrent()
        model.onEvent(SettingsEvent.PersonalBufferChanged("12"))
        model.onEvent(SettingsEvent.SafetyMarginChanged("8"))
        runCurrent()
        assertEquals(original, repository.settings.value)
        model.onEvent(SettingsEvent.NotificationsEnabledChanged(true))
        runCurrent()
        assertEquals("12", model.uiState.value.personalBufferMinutes)
        assertEquals("8", model.uiState.value.safetyMarginMinutes)
        model.onEvent(SettingsEvent.SaveBufferDefaultsClicked)
        runCurrent()
        assertEquals(12, repository.settings.value.personalBufferMinutes)
        assertEquals(8, repository.settings.value.safetyMarginMinutes)
        assertEquals(false, model.uiState.value.hasUnsavedBufferDefaults)
    }

    @Test fun invalidDefaultOrFailedSaveLeavesStoredPairUnchangedAndRetainsDraft() = runTest {
        val original = TestSettingsRepository()
        val repository = object : SettingsRepository by original {
            override suspend fun updateBufferDefaults(personalBufferMinutes: Int, safetyMarginMinutes: Int) = error("Disk full")
        }
        val initial = original.settings.value
        val model = SettingsViewModel(repository)
        runCurrent()
        model.onEvent(SettingsEvent.PersonalBufferChanged("99"))
        model.onEvent(SettingsEvent.SaveBufferDefaultsClicked)
        runCurrent()
        assertNotNull(model.uiState.value.errorMessage)
        assertEquals(initial, original.settings.value)
        model.onEvent(SettingsEvent.PersonalBufferChanged("10"))
        model.onEvent(SettingsEvent.SaveBufferDefaultsClicked)
        runCurrent()
        assertEquals("10", model.uiState.value.personalBufferMinutes)
        assertEquals(true, model.uiState.value.hasUnsavedBufferDefaults)
        assertNotNull(model.uiState.value.errorMessage)
        assertEquals(initial, original.settings.value)
    }
}
