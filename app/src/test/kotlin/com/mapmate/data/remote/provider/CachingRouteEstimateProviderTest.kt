package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RouteEstimateCacheRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachingRouteEstimateProviderTest {
    @Test
    fun getRouteEstimate_savesSuccessfulPrimaryEstimate() = runTest {
        val cacheRepository = InMemoryRouteEstimateCacheRepository()
        val provider = CachingRouteEstimateProvider(
            cacheNamespace = "Primary",
            primary = FixedRouteEstimateProvider(primaryEstimate),
            cacheRepository = cacheRepository,
            nowProvider = { 1_000L },
        )

        val result = provider.getRouteEstimate(testOrigin, testDestination, TransportMode.TRANSIT)

        assertEquals(primaryEstimate, result)
        assertFalse(result.isFallbackEstimate)
        assertEquals(
            primaryEstimate,
            cacheRepository.findFreshEstimate(
                cacheNamespace = "Primary",
                origin = testOrigin,
                destination = testDestination,
                transportMode = TransportMode.TRANSIT,
                nowEpochMillis = 1_000L,
            ),
        )
    }

    @Test
    fun getRouteEstimate_doesNotSaveRealtimeAdjustedEstimate() = runTest {
        val cacheRepository = InMemoryRouteEstimateCacheRepository()
        val provider = CachingRouteEstimateProvider(
            cacheNamespace = "Primary",
            primary = FixedRouteEstimateProvider(
                primaryEstimate.copy(hasRealtimeAdjustment = true),
            ),
            cacheRepository = cacheRepository,
            nowProvider = { 1_000L },
        )

        val result = provider.getRouteEstimate(
            origin = testOrigin,
            destination = testDestination,
            transportMode = TransportMode.TRANSIT,
            scheduledDepartureEpochMillis = 2_000L,
        )

        assertTrue(result.hasRealtimeAdjustment)
        assertEquals(
            null,
            cacheRepository.findFreshEstimate(
                cacheNamespace = "Primary",
                origin = testOrigin,
                destination = testDestination,
                transportMode = TransportMode.TRANSIT,
                nowEpochMillis = 1_000L,
            ),
        )
    }

    @Test
    fun getRouteEstimate_returnsFreshCacheWhenPrimaryFails() = runTest {
        val cacheRepository = InMemoryRouteEstimateCacheRepository()
        cacheRepository.saveEstimate(
            cacheNamespace = "Primary",
            origin = testOrigin,
            destination = testDestination,
            transportMode = TransportMode.TRANSIT,
            routeEstimate = primaryEstimate,
            capturedAtEpochMillis = 1_000L,
            expiresAtEpochMillis = 5_000L,
        )
        val provider = CachingRouteEstimateProvider(
            cacheNamespace = "Primary",
            primary = FailingRouteEstimateProvider,
            cacheRepository = cacheRepository,
            nowProvider = { 2_000L },
        )

        val result = provider.getRouteEstimate(testOrigin, testDestination, TransportMode.TRANSIT)

        assertEquals(primaryEstimate.estimatedMinutes, result.estimatedMinutes)
        assertTrue(result.isFallbackEstimate)
        assertTrue(result.statusMessage.orEmpty().contains("최근 성공"))
    }

    @Test
    fun getRouteEstimate_throwsWhenPrimaryFailsAndCacheIsMissing() = runTest {
        val provider = CachingRouteEstimateProvider(
            cacheNamespace = "Primary",
            primary = FailingRouteEstimateProvider,
            cacheRepository = InMemoryRouteEstimateCacheRepository(),
            nowProvider = { 2_000L },
        )

        val result = runCatching {
            provider.getRouteEstimate(testOrigin, testDestination, TransportMode.TRANSIT)
        }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    private class InMemoryRouteEstimateCacheRepository : RouteEstimateCacheRepository {
        private val estimates = mutableMapOf<String, Pair<RouteEstimate, Long>>()

        override suspend fun saveEstimate(
            cacheNamespace: String,
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routeEstimate: RouteEstimate,
            capturedAtEpochMillis: Long,
            expiresAtEpochMillis: Long,
        ) {
            estimates[key(cacheNamespace, origin, destination, transportMode)] = routeEstimate to expiresAtEpochMillis
        }

        override suspend fun findFreshEstimate(
            cacheNamespace: String,
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            nowEpochMillis: Long,
        ): RouteEstimate? {
            val (estimate, expiresAt) = estimates[key(cacheNamespace, origin, destination, transportMode)] ?: return null
            return estimate.takeIf { expiresAt > nowEpochMillis }
        }

        override suspend fun deleteExpired(nowEpochMillis: Long) {
            estimates.entries.removeIf { (_, value) -> value.second <= nowEpochMillis }
        }

        private fun key(
            cacheNamespace: String,
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
        ): String {
            return "$cacheNamespace|${transportMode.name}|${origin.name}|${destination.name}"
        }
    }

    private object FailingRouteEstimateProvider : RouteEstimateProvider {
        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routineId: Long?,
            scheduledDepartureEpochMillis: Long?,
            targetArrivalEpochMillis: Long?,
        ): RouteEstimate {
            error("Remote failed.")
        }
    }

    private class FixedRouteEstimateProvider(
        private val routeEstimate: RouteEstimate,
    ) : RouteEstimateProvider {
        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routineId: Long?,
            scheduledDepartureEpochMillis: Long?,
            targetArrivalEpochMillis: Long?,
        ): RouteEstimate {
            return routeEstimate
        }
    }

    private companion object {
        val testOrigin = Destination(
            name = "집",
            address = "서울",
            latitude = 37.4963,
            longitude = 126.9574,
        )

        val testDestination = Destination(
            name = "학교",
            address = "서울",
            latitude = 37.4979,
            longitude = 127.0276,
        )

        val primaryEstimate = RouteEstimate(
            estimatedMinutes = 33,
            summary = "실제 API 예상",
            providerName = "Primary",
            reason = "Primary route.",
        )
    }
}
