package com.mapmate.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun defaults_matchRoutineRegistrationDefaults() {
        val settings = AppSettings(
            personalBufferMinutes = AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES,
            safetyMarginMinutes = AppSettings.DEFAULT_SAFETY_MARGIN_MINUTES,
            notificationsEnabled = AppSettings.DEFAULT_NOTIFICATIONS_ENABLED,
            defaultTransportMode = TransportMode.TRANSIT,
        )

        assertEquals(6, settings.personalBufferMinutes)
        assertEquals(5, settings.safetyMarginMinutes)
        assertFalse(settings.notificationsEnabled)
        assertEquals(TransportMode.TRANSIT, settings.defaultTransportMode)
    }

    @Test
    fun isValidBufferMinutes_acceptsOnlyZeroToSixty() {
        assertTrue(AppSettings.isValidBufferMinutes(0))
        assertTrue(AppSettings.isValidBufferMinutes(60))

        assertFalse(AppSettings.isValidBufferMinutes(-1))
        assertFalse(AppSettings.isValidBufferMinutes(61))
    }

    @Test
    fun bufferMinutesOrDefault_fallsBackForMissingOrInvalidValues() {
        assertEquals(12, AppSettings.bufferMinutesOrDefault(12, 6))
        assertEquals(6, AppSettings.bufferMinutesOrDefault(null, 6))
        assertEquals(6, AppSettings.bufferMinutesOrDefault(90, 6))
    }

    @Test
    fun transportModeOrDefault_fallsBackForUnknownNames() {
        assertEquals(TransportMode.WALK, AppSettings.transportModeOrDefault("WALK"))
        assertEquals(TransportMode.TRANSIT, AppSettings.transportModeOrDefault("BUS"))
        assertEquals(TransportMode.TRANSIT, AppSettings.transportModeOrDefault(null))
    }
}
