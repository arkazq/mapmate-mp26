package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackRouteEstimateProviderTest {
    @Test
    fun getRouteEstimate_returnsFirstSuccessfulPrimary() = runTest {
        val primaryEstimate = RouteEstimate(
            estimatedMinutes = 38,
            summary = "실제 API 예상",
            providerName = "Primary",
            reason = "Primary provider result.",
        )
        val provider = FallbackRouteEstimateProvider(
            primaryProviders = listOf(
                FailingRouteEstimateProvider,
                FixedRouteEstimateProvider(primaryEstimate),
            ),
            fallback = FixedRouteEstimateProvider(
                primaryEstimate.copy(providerName = "Fallback"),
            ),
        )

        val result = provider.getRouteEstimate(
            origin = testOrigin,
            destination = testDestination,
            transportMode = TransportMode.TRANSIT,
        )

        assertEquals(primaryEstimate, result)
        assertEquals(false, result.isFallbackEstimate)
        assertNull(result.statusMessage)
    }

    @Test
    fun getRouteEstimate_returnsFallbackWhenAllPrimaryProvidersFail() = runTest {
        val fallbackEstimate = RouteEstimate(
            estimatedMinutes = 42,
            summary = "Mock 예상",
            providerName = "Mock",
            reason = "Fallback provider result.",
        )
        val provider = FallbackRouteEstimateProvider(
            primaryProviders = listOf(FailingRouteEstimateProvider),
            fallback = FixedRouteEstimateProvider(fallbackEstimate),
        )

        val result = provider.getRouteEstimate(
            origin = testOrigin,
            destination = testDestination,
            transportMode = TransportMode.TRANSIT,
        )

        assertEquals(fallbackEstimate.estimatedMinutes, result.estimatedMinutes)
        assertEquals(fallbackEstimate.providerName, result.providerName)
        assertTrue(result.isFallbackEstimate)
        assertTrue(result.statusMessage.orEmpty().contains("기본 예상 시간"))
        assertTrue(result.statusMessage.orEmpty().contains("Failing"))
    }

    private object FailingRouteEstimateProvider : RouteEstimateProvider {
        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
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
        ): RouteEstimate {
            return routeEstimate
        }
    }

    private companion object {
        val testOrigin = Destination(
            name = "숭실대학교",
            address = "서울특별시 동작구 상도로 369",
            latitude = 37.4963,
            longitude = 126.9574,
        )

        val testDestination = Destination(
            name = "강남역",
            address = "서울특별시 강남구 강남대로 지하396",
            latitude = 37.4979,
            longitude = 127.0276,
        )
    }
}
