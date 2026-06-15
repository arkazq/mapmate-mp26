package com.mapmate.presentation.common

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineRecommendationUiModelTest {
    @Test
    fun toRecommendationUiModel_marksImmediateDepartureWhenCalculatedTimePassed() {
        val recommendation = sampleRoutine.toRecommendationUiModel(
            routeEstimate = routeEstimate,
            now = LocalTime.of(8, 8, 30),
        )

        assertEquals("08:07", recommendation.calculatedDepartureTimeText)
        assertEquals("08:08", recommendation.recommendedDepartureTimeText)
        assertEquals("지금 출발", recommendation.recommendedDepartureDisplayText)
        assertEquals(true, recommendation.isImmediateDepartureRecommended)
        assertNotNull(recommendation.departureStatusMessage)
    }

    @Test
    fun toRecommendationUiModel_keepsTimeDisplayWhenDepartureTimeIsFuture() {
        val recommendation = sampleRoutine.toRecommendationUiModel(
            routeEstimate = routeEstimate,
            now = LocalTime.of(7, 30),
            recommendedDepartureAtEpochMillis = 60 * 60 * 1000L,
        )

        assertEquals("08:07", recommendation.calculatedDepartureTimeText)
        assertEquals("08:07", recommendation.recommendedDepartureTimeText)
        assertEquals("08:07", recommendation.recommendedDepartureDisplayText)
        assertEquals(false, recommendation.isImmediateDepartureRecommended)
        assertNull(recommendation.departureStatusMessage)
        assertEquals(23, recommendation.minutesUntilDeparture(37 * 60 * 1000L))
        assertEquals("출발까지 23분 남았어요", recommendation.departureCountdownText(37 * 60 * 1000L))
    }

    @Test
    fun departureCountdownText_formatsLongRemainingTimeAsHoursAndMinutes() {
        val recommendation = sampleRoutine.toRecommendationUiModel(
            routeEstimate = routeEstimate,
            now = LocalTime.of(7, 30),
            recommendedDepartureAtEpochMillis = 1328 * 60 * 1000L,
        )

        assertEquals("출발까지 22시간 8분 남았어요", recommendation.departureCountdownText(0L))
    }

    @Test
    fun departureProgress_usesSameRemainingMinutesAsCountdownText() {
        val recommendation = sampleRoutine.toRecommendationUiModel(
            routeEstimate = routeEstimate,
            now = LocalTime.of(7, 30),
            recommendedDepartureAtEpochMillis = 60 * 60 * 1000L,
        )

        assertEquals(23, recommendation.minutesUntilDeparture(37 * 60 * 1000L))
        assertEquals(0.616f, recommendation.departureProgress(37 * 60 * 1000L), 0.001f)
    }

    private companion object {
        val routeEstimate = RouteEstimate(
            estimatedMinutes = 42,
            summary = "42 min",
            providerName = "Test",
            reason = "test",
        )

        val sampleRoutine = Routine(
            name = "등교",
            origin = Destination(
                name = "집",
                address = "집 주소",
                latitude = 37.0,
                longitude = 127.0,
            ),
            destination = Destination(
                name = "학교",
                address = "학교 주소",
                latitude = 37.5,
                longitude = 127.5,
            ),
            targetArrivalTime = LocalTime.of(9, 0),
            repeatDays = setOf(RepeatDay.MONDAY),
            transportMode = TransportMode.TRANSIT,
            personalBufferMinutes = 6,
            safetyMarginMinutes = 5,
        )
    }
}
