package com.mapmate.data.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureRecheckOffsetsTest {
    @Test
    fun departureRecheckOffsetsFor_addsTenMinuteRecheckForShortRoutes() {
        assertEquals(
            listOf(60L, 30L, 15L, 10L, 5L),
            departureRecheckOffsetsFor(routeDurationMinutes = 30),
        )
    }

    @Test
    fun departureRecheckOffsetsFor_addsTenMinuteRecheckForSixtyMinuteRoutes() {
        assertEquals(
            listOf(90L, 60L, 30L, 15L, 10L, 5L),
            departureRecheckOffsetsFor(routeDurationMinutes = 60),
        )
    }

    @Test
    fun departureRecheckOffsetsFor_addsTenMinuteRecheckForNinetyMinuteRoutes() {
        assertEquals(
            listOf(120L, 90L, 60L, 30L, 15L, 10L, 5L),
            departureRecheckOffsetsFor(routeDurationMinutes = 90),
        )
    }
}
