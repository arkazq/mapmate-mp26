package com.mapmate.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppSettingsTest {
    @Test
    public void defaults_matchRoutineRegistrationDefaults() {
        AppSettings settings = new AppSettings(
                AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES,
                AppSettings.DEFAULT_SAFETY_MARGIN_MINUTES,
                AppSettings.DEFAULT_NOTIFICATIONS_ENABLED,
                TransportMode.TRANSIT
        );

        assertEquals(6, settings.getPersonalBufferMinutes());
        assertEquals(5, settings.getSafetyMarginMinutes());
        assertFalse(settings.getNotificationsEnabled());
        assertEquals(TransportMode.TRANSIT, settings.getDefaultTransportMode());
    }

    @Test
    public void isValidBufferMinutes_acceptsOnlyZeroToSixty() {
        assertTrue(AppSettings.Companion.isValidBufferMinutes(0));
        assertTrue(AppSettings.Companion.isValidBufferMinutes(60));

        assertFalse(AppSettings.Companion.isValidBufferMinutes(-1));
        assertFalse(AppSettings.Companion.isValidBufferMinutes(61));
    }

    @Test
    public void bufferMinutesOrDefault_fallsBackForMissingOrInvalidValues() {
        assertEquals(12, AppSettings.Companion.bufferMinutesOrDefault(12, 6));
        assertEquals(6, AppSettings.Companion.bufferMinutesOrDefault(null, 6));
        assertEquals(6, AppSettings.Companion.bufferMinutesOrDefault(90, 6));
    }

    @Test
    public void transportModeOrDefault_fallsBackForUnknownNames() {
        assertEquals(TransportMode.WALK, AppSettings.Companion.transportModeOrDefault("WALK"));
        assertEquals(TransportMode.TRANSIT, AppSettings.Companion.transportModeOrDefault("BUS"));
        assertEquals(TransportMode.TRANSIT, AppSettings.Companion.transportModeOrDefault(null));
    }
}
