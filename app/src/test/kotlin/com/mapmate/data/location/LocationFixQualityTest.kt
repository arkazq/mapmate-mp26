package com.mapmate.data.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFixQualityTest {
    private val now = 1_000 * 1_000_000_000L
    private fun accepted(ageNanos: Long, accuracy: Float? = 15f, latitude: Double = 37.5) =
        isUsableLocationFix(latitude, 127.0, accuracy, now - ageNanos, now)

    @Test fun fiveMinuteBoundaryIsIncludedButOlderFixIsRejected() {
        assertTrue(accepted(5 * 60 * 1_000_000_000L))
        assertFalse(accepted(5 * 60 * 1_000_000_000L + 1))
    }

    @Test fun approximatePermissionFixIsAllowedButUnknownAndExtremeAccuracyAreRejected() {
        assertTrue(accepted(0, 3_000f))
        assertFalse(accepted(0, 3_001f))
        assertFalse(accepted(0, null))
        assertFalse(accepted(0, Float.NaN))
    }

    @Test fun futureAndInvalidCoordinateFixesAreRejected() {
        assertFalse(accepted(-1))
        assertFalse(accepted(0, latitude = Double.NaN))
        assertFalse(accepted(0, latitude = 91.0))
    }
}
