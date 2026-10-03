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
import kotlinx.coroutines.CancellationException

class FallbackRouteEstimateProviderTest {
    @Test fun failureMessageUsesUserFacingTextInsteadOfImplementationClassNames() = runTest {
        val provider = FallbackRouteEstimateProvider(listOf(FailingRouteEstimateProvider),
            FixedRouteEstimateProvider(RouteEstimate(42, "기본 예상", "Mock", "")))
        val result = provider.getRouteEstimate(testOrigin, testDestination, TransportMode.TRANSIT)
        assertEquals("경로 정보를 확인하지 못해 기본 예상 시간을 사용했습니다. 네트워크 연결을 확인한 뒤 다시 조회해 주세요.", result.statusMessage)
    }

    @Test(expected = CancellationException::class)
    fun cancellationDoesNotTryAnotherRouteProvider() = runTest {
        val provider = FallbackRouteEstimateProvider(
            primaryProviders = listOf(object : RouteEstimateProvider {
                override suspend fun getRouteEstimate(
                    origin: Destination, destination: Destination, transportMode: TransportMode,
                    routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?,
                ): RouteEstimate {
                    throw CancellationException("Screen was closed.")
                }
            }),
            fallback = FailingRouteEstimateProvider,
        )
        provider.getRouteEstimate(testOrigin, testDestination, TransportMode.TRANSIT)
    }

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
        assertTrue(result.statusMessage.orEmpty().contains("네트워크 연결"))
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
