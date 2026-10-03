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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import kotlinx.coroutines.CancellationException
import org.junit.Test

class SegmentAdjustedRouteEstimateProviderTest {
    @Test fun adjustmentReadFailureKeepsSuccessfulRouteAndCancellationStillPropagates() = runTest {
        val base = RouteEstimate(30, "route", "test", "base", segments = listOf(busSegment()))
        val failed = object : SegmentTimeAdjustmentRepository {
            override suspend fun replaceAdjustmentsForRoutine(routineId: Long, adjustments: List<SegmentTimeAdjustment>) = Unit
            override suspend fun getAdjustmentsForRoutine(routineId: Long): List<SegmentTimeAdjustment> = error("storage unavailable")
        }
        val provider = SegmentAdjustedRouteEstimateProvider(FixedRouteEstimateProvider(base), failed)
        assertSame(base, provider.getRouteEstimate(destination, destination, TransportMode.TRANSIT, 1))
        val cancelled = object : SegmentTimeAdjustmentRepository {
            override suspend fun replaceAdjustmentsForRoutine(routineId: Long, adjustments: List<SegmentTimeAdjustment>) = Unit
            override suspend fun getAdjustmentsForRoutine(routineId: Long): List<SegmentTimeAdjustment> = throw CancellationException("cancelled")
        }
        val outcome = runCatching {
            SegmentAdjustedRouteEstimateProvider(FixedRouteEstimateProvider(base), cancelled)
                .getRouteEstimate(destination, destination, TransportMode.TRANSIT, 1)
        }
        assertTrue(outcome.exceptionOrNull() is CancellationException)
    }

    @Test fun malformedConfidenceAndImpossibleNegativeSegmentDurationAreHandledConservatively() = runTest {
        val base = RouteEstimate(30, "route", "test", "base", segments = listOf(busSegment()))
        fun adjustment(confidence: Double, delay: Int) = SegmentTimeAdjustment(routineId = 1,
            segmentType = RouteSegmentType.BUS_RIDE, routeName = "753", startName = "start", endName = "end",
            averageDelayMinutes = delay, averageActualDurationMinutes = 1, minActualDurationMinutes = 1,
            maxActualDurationMinutes = 1, sampleCount = 3, confidence = confidence, updatedAtEpochMillis = 1000)
        for (confidence in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 1.1)) {
            val provider = SegmentAdjustedRouteEstimateProvider(FixedRouteEstimateProvider(base),
                FixedSegmentTimeAdjustmentRepository(listOf(adjustment(confidence, 4))))
            assertSame(base, provider.getRouteEstimate(destination, destination, TransportMode.TRANSIT, 1))
        }
        val bounded = SegmentAdjustedRouteEstimateProvider(FixedRouteEstimateProvider(base),
            FixedSegmentTimeAdjustmentRepository(listOf(adjustment(1.0, -40))))
        assertEquals(20, bounded.getRouteEstimate(destination, destination, TransportMode.TRANSIT, 1).estimatedMinutes)
    }

    @Test fun queriedRoutineSuppliesIdentityForSegmentsWithoutRoutineId() = runTest {
        val base = RouteEstimate(30, "route", "test", "base", segments = listOf(busSegment().copy(routineId = null)))
        val adjustment = SegmentTimeAdjustment(routineId = 1, segmentType = RouteSegmentType.BUS_RIDE,
            routeName = "753", startName = "start", endName = "end", averageDelayMinutes = 4,
            averageActualDurationMinutes = 14, minActualDurationMinutes = 13, maxActualDurationMinutes = 15,
            sampleCount = 3, confidence = 1.0, updatedAtEpochMillis = 1000)
        val provider = SegmentAdjustedRouteEstimateProvider(FixedRouteEstimateProvider(base),
            FixedSegmentTimeAdjustmentRepository(listOf(adjustment)))
        assertEquals(34, provider.getRouteEstimate(destination, destination, TransportMode.TRANSIT, 1).estimatedMinutes)
    }

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
                        averageActualDurationMinutes = 14,
                        minActualDurationMinutes = 13,
                        maxActualDurationMinutes = 15,
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
                        averageActualDurationMinutes = 14,
                        minActualDurationMinutes = 13,
                        maxActualDurationMinutes = 15,
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

    @Test
    fun getRouteEstimate_doesNotApplyAdjustmentFromDifferentRoutine() = runTest {
        val provider = SegmentAdjustedRouteEstimateProvider(
            delegate = FixedRouteEstimateProvider(
                RouteEstimate(
                    estimatedMinutes = 30,
                    summary = "route",
                    providerName = "test",
                    reason = "base",
                    segments = listOf(busSegment(routineId = 1L)),
                ),
            ),
            segmentTimeAdjustmentRepository = UnfilteredSegmentTimeAdjustmentRepository(
                listOf(
                    SegmentTimeAdjustment(
                        routineId = 2L,
                        segmentType = RouteSegmentType.BUS_RIDE,
                        routeName = "753",
                        startName = "start",
                        endName = "end",
                        averageDelayMinutes = 20,
                        averageActualDurationMinutes = 30,
                        minActualDurationMinutes = 30,
                        maxActualDurationMinutes = 30,
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
            targetArrivalEpochMillis: Long?,
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

    private class UnfilteredSegmentTimeAdjustmentRepository(
        private val adjustments: List<SegmentTimeAdjustment>,
    ) : SegmentTimeAdjustmentRepository {
        override suspend fun replaceAdjustmentsForRoutine(
            routineId: Long,
            adjustments: List<SegmentTimeAdjustment>,
        ) = Unit

        override suspend fun getAdjustmentsForRoutine(routineId: Long): List<SegmentTimeAdjustment> {
            return adjustments
        }
    }

    private fun busSegment(
        routineId: Long = 1L,
    ): RouteSegment {
        return RouteSegment(
            routineId = routineId,
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
