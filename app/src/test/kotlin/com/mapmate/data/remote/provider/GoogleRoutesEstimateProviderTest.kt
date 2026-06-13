package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.GoogleRoutesApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.GoogleLocalizedText
import com.mapmate.data.remote.dto.GoogleLocalizedValues
import com.mapmate.data.remote.dto.GoogleRoute
import com.mapmate.data.remote.dto.GoogleRoutesRequest
import com.mapmate.data.remote.dto.GoogleRoutesResponse
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.TransportMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleRoutesEstimateProviderTest {
    @Test
    fun getRouteEstimate_usesTrafficAwareRoutingForCars() = runTest {
        val api = CapturingGoogleRoutesApi()
        val provider = GoogleRoutesEstimateProvider(api, testConfig)

        val estimate = provider.getRouteEstimate(
            origin = testOrigin,
            destination = testDestination,
            transportMode = TransportMode.CAR,
        )

        assertEquals("DRIVE", api.lastRequest?.travelMode)
        assertEquals("TRAFFIC_AWARE", api.lastRequest?.routingPreference)
        assertEquals(31, estimate.estimatedMinutes)
        assertTrue(estimate.reason.contains("traffic-aware"))
    }

    @Test
    fun getRouteEstimate_doesNotSendRoutingPreferenceForWalking() = runTest {
        val api = CapturingGoogleRoutesApi()
        val provider = GoogleRoutesEstimateProvider(api, testConfig)

        provider.getRouteEstimate(
            origin = testOrigin,
            destination = testDestination,
            transportMode = TransportMode.WALK,
        )

        assertEquals("WALK", api.lastRequest?.travelMode)
        assertNull(api.lastRequest?.routingPreference)
    }

    private class CapturingGoogleRoutesApi : GoogleRoutesApi {
        var lastRequest: GoogleRoutesRequest? = null

        override suspend fun computeRoutes(
            apiKey: String,
            fieldMask: String,
            request: GoogleRoutesRequest,
        ): GoogleRoutesResponse {
            lastRequest = request
            return GoogleRoutesResponse(
                routes = listOf(
                    GoogleRoute(
                        duration = "1830s",
                        description = "테스트 경로",
                        localizedValues = GoogleLocalizedValues(
                            duration = GoogleLocalizedText("31분"),
                        ),
                    ),
                ),
            )
        }
    }

    private companion object {
        val testConfig = RemoteApiConfig(
            kakaoRestApiKey = "",
            odsayApiKey = "",
            googleRoutesApiKey = "google-key",
            seoulOpenApiKey = "",
            seoulBusServiceKey = "",
            tagoServiceKey = "",
        )

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
    }
}
