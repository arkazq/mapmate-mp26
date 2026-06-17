package com.mapmate.presentation.common

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteBoardingStatus
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScheduleAwareRecommendationResolverTest {
    private val zoneId = ZoneId.of("Asia/Seoul")

    @Test
    fun resolve_appliesSafeDepartureAndPassesScheduleToRealtimeProvider() = runTest {
        val now = ZonedDateTime.of(2026, 6, 17, 9, 0, 0, 0, zoneId)
        val safeDepartureAt = ZonedDateTime.of(2026, 6, 17, 9, 10, 0, 0, zoneId)
            .toInstant()
            .toEpochMilli()
        val provider = RecordingRouteEstimateProvider(
            realtimeEstimate = routeEstimate(
                boardingAdvice = boardingAdvice(safeDepartureEpochMillis = safeDepartureAt),
            ),
        )
        val resolver = ScheduleAwareRecommendationResolver(routeEstimateProvider = provider)

        val result = resolver.resolve(
            routine = sampleRoutine(),
            now = now,
        )

        assertEquals("09:10", result.recommendation.recommendedDepartureTimeText)
        assertEquals(safeDepartureAt, result.recommendation.recommendedDepartureAtEpochMillis)
        assertEquals(true, result.recommendation.isImmediateDepartureRecommended.not())
        assertNotNull(provider.scheduledDepartureCalls.drop(1).single())
        assertNotNull(provider.targetArrivalCalls.drop(1).single())
    }

    private class RecordingRouteEstimateProvider(
        private val realtimeEstimate: RouteEstimate,
    ) : RouteEstimateProvider {
        val scheduledDepartureCalls = mutableListOf<Long?>()
        val targetArrivalCalls = mutableListOf<Long?>()

        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routineId: Long?,
            scheduledDepartureEpochMillis: Long?,
            targetArrivalEpochMillis: Long?,
        ): RouteEstimate {
            scheduledDepartureCalls += scheduledDepartureEpochMillis
            targetArrivalCalls += targetArrivalEpochMillis
            return if (scheduledDepartureEpochMillis == null) {
                RouteEstimate(
                    estimatedMinutes = 26,
                    summary = "test route",
                    providerName = "Test",
                    reason = "test",
                )
            } else {
                realtimeEstimate
            }
        }
    }

    private fun routeEstimate(
        boardingAdvice: RouteBoardingAdvice? = null,
    ): RouteEstimate {
        return RouteEstimate(
            estimatedMinutes = 26,
            summary = "test route",
            providerName = "Test",
            reason = "test",
            boardingAdvice = boardingAdvice,
            hasRealtimeAdjustment = boardingAdvice != null,
        )
    }

    private fun boardingAdvice(safeDepartureEpochMillis: Long): RouteBoardingAdvice {
        return RouteBoardingAdvice(
            selectedCandidateIndex = 1,
            candidateCount = 1,
            routeName = "753",
            stationName = "start",
            accessMinutes = 4,
            realtimeWaitMinutes = 17,
            slackMinutes = 3,
            status = RouteBoardingStatus.BOARDABLE,
            estimatedTotalMinutes = 26,
            safeDepartureEpochMillis = safeDepartureEpochMillis,
            earlyDepartureRequiredMinutes = 16,
        )
    }

    private fun sampleRoutine(): Routine {
        return Routine(
            id = 1L,
            name = "routine",
            origin = Destination(
                name = "origin",
                address = "origin address",
                latitude = 37.0,
                longitude = 127.0,
            ),
            destination = Destination(
                name = "destination",
                address = "destination address",
                latitude = 37.5,
                longitude = 127.5,
            ),
            targetArrivalTime = LocalTime.of(10, 0),
            repeatDays = setOf(RepeatDay.WEDNESDAY),
            transportMode = TransportMode.TRANSIT,
            personalBufferMinutes = 3,
            safetyMarginMinutes = 5,
        )
    }
}
