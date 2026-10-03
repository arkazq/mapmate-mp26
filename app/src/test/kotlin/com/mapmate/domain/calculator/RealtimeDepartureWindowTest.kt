package com.mapmate.domain.calculator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeDepartureWindowTest {
    private val now = 1_000_000L

    @Test
    fun includesNowAndExactlyThirtyMinutes() {
        assertTrue(isWithinRealtimeDepartureWindow(now, now))
        assertTrue(isWithinRealtimeDepartureWindow(now + 30 * 60_000L, now))
    }

    @Test
    fun excludesEvenOneMillisecondBeyondWindowOrBeforeNow() {
        assertFalse(isWithinRealtimeDepartureWindow(now + 30 * 60_000L + 1, now))
        assertFalse(isWithinRealtimeDepartureWindow(now - 1, now))
    }

    @Test
    fun excludesMissingSchedule() {
        assertFalse(isWithinRealtimeDepartureWindow(null, now))
    }
}
