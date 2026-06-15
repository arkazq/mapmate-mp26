package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.SegmentTimeAdjustment
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.SegmentTimeAdjustmentRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentAdjustedRouteEstimateProviderTest {
    @Test
    fun getRouteEstimate_appliesConfidentSegmentAdjustments() = runTest {
        val provider = SegmentAdjustedRouteEstimateProvider(
            delegate = FixedRouteEstimateProvider(
                RouteEstimate(
                    estimatedMinutes = 30,
                    summary = "route",
                    providerName = "test",
                    reason = "base",
                    segments = listOf(busSegment()),
                ),
            ),
            segmentTimeAdjustmentRepository = FixedSegmentTimeAdjustmentRepository(
                listOf(
                    SegmentTimeAdjustment(
                        routineId = 1L,
                        segmentType = RouteSegmentType.BUS_RIDE,
                        routeName = "753",
                        startName = "start",
                        endName = "end",
                        averageDelayMinutes = 4,
                        sampleCount = 3,
                        confidence = 1.0,
                        updatedAtEpochMillis = 1000L,
                    ),
                ),
            ),
        )

        val estimate = provider.getRouteEstimate(
            origin = destination,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            routineId = 1L,
        )

        assertEquals(34, estimate.estimatedMinutes)
    }

    @Test
    fun getRouteEstimate_keepsBaseEstimateWhenConfidenceIsTooLow() = runTest {
        val provider = SegmentAdjustedRouteEstimateProvider(
            delegate = FixedRouteEstimateProvider(
                RouteEstimate(
                    estimatedMinutes = 30,
                    summary = "route",
                    providerName = "test",
                    reason = "base",
                    segments = listOf(busSegment()),
                ),
            ),
            segmentTimeAdjustmentRepository = FixedSegmentTimeAdjustmentRepository(
                listOf(
                    SegmentTimeAdjustment(
                        routineId = 1L,
                        segmentType = RouteSegmentType.BUS_RIDE,
                        routeName = "753",
                        startName = "start",
                        endName = "end",
                        averageDelayMinutes = 4,
                        sampleCount = 1,
                        confidence = 0.2,
                        updatedAtEpochMillis = 1000L,
                    ),
                ),
            ),
        )

        val estimate = provider.getRouteEstimate(
            origin = destination,
            destination = destination,
            transportMode = TransportMode.TRANSIT,
            routineId = 1L,
        )

        assertEquals(30, estimate.estimatedMinutes)
    }

    private class FixedRouteEstimateProvider(
        private val estimate: RouteEstimate,
    ) : RouteEstimateProvider {
        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routineId: Long?,
            scheduledDepartureEpochMillis: Long?,
        ): RouteEstimate = estimate
    }

    private class FixedSegmentTimeAdjustmentRepository(
        private val adjustments: List<SegmentTimeAdjustment>,
    ) : SegmentTimeAdjustmentRepository {
        override suspend fun replaceAdjustmentsForRoutine(
            routineId: Long,
            adjustments: List<SegmentTimeAdjustment>,
        ) = Unit

        override suspend fun getAdjustmentsForRoutine(routineId: Long): List<SegmentTimeAdjustment> {
            return adjustments.filter { it.routineId == routineId }
        }
    }

    private fun busSegment(): RouteSegment {
        return RouteSegment(
            routineId = 1L,
            segmentIndex = 0,
            segmentType = RouteSegmentType.BUS_RIDE,
            trafficType = 2,
            routeName = "753",
            startName = "Start",
            endName = "End",
            plannedDurationMinutes = 10,
        )
    }

    private val destination = Destination(
        name = "place",
        address = "address",
        latitude = 37.0,
        longitude = 127.0,
    )
}
