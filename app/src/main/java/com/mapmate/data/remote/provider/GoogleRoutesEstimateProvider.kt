package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.GoogleRoutesApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.GoogleLatLng
import com.mapmate.data.remote.dto.GoogleLocation
import com.mapmate.data.remote.dto.GoogleRoutesRequest
import com.mapmate.data.remote.dto.GoogleWaypoint
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import kotlin.math.ceil

class GoogleRoutesEstimateProvider(
    private val api: GoogleRoutesApi,
    private val config: RemoteApiConfig,
) : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
    ): RouteEstimate {
        check(config.hasGoogleRoutesKey) { "Google Routes API key is missing." }

        val originLatitude = requireNotNull(origin.latitude) {
            "Origin latitude is missing."
        }
        val originLongitude = requireNotNull(origin.longitude) {
            "Origin longitude is missing."
        }
        val destinationLatitude = requireNotNull(destination.latitude) {
            "Destination latitude is missing."
        }
        val destinationLongitude = requireNotNull(destination.longitude) {
            "Destination longitude is missing."
        }

        val response = api.computeRoutes(
            apiKey = config.googleRoutesApiKey,
            fieldMask = "routes.duration,routes.description,routes.localizedValues.duration",
            request = GoogleRoutesRequest(
                origin = waypoint(originLatitude, originLongitude),
                destination = waypoint(destinationLatitude, destinationLongitude),
                travelMode = transportMode.toGoogleTravelMode(),
                routingPreference = transportMode.toGoogleRoutingPreference(),
            ),
        )
        val route = response.routes.firstOrNull()
        val estimatedMinutes = route?.duration?.toEstimatedMinutes()
        check(estimatedMinutes != null && estimatedMinutes > 0) {
            "Google Routes response did not include a route duration."
        }

        val transportLabel = transportMode.toKoreanLabel()
        return RouteEstimate(
            estimatedMinutes = estimatedMinutes,
            summary = "${origin.name}에서 ${destination.name}까지 ${transportLabel} 기준 ${estimatedMinutes}분 예상",
            providerName = "Google Routes",
            reason = listOfNotNull(
                transportMode.trafficAwareReason(),
                route.localizedValues?.duration?.text?.let { "Google Routes ${it} 경로 기준입니다." },
                route.description?.takeIf(String::isNotBlank),
            ).joinToString(" ").ifBlank {
                "Google Routes ${transportLabel} 경로 기준입니다."
            },
        )
    }

    private fun waypoint(
        latitude: Double,
        longitude: Double,
    ): GoogleWaypoint {
        return GoogleWaypoint(
            location = GoogleLocation(
                latLng = GoogleLatLng(
                    latitude = latitude,
                    longitude = longitude,
                ),
            ),
        )
    }

    private fun String.toEstimatedMinutes(): Int? {
        val seconds = removeSuffix("s").toDoubleOrNull() ?: return null
        return ceil(seconds / SECONDS_PER_MINUTE).toInt()
    }

    private fun TransportMode.toGoogleTravelMode(): String {
        return when (this) {
            TransportMode.TRANSIT -> "TRANSIT"
            TransportMode.WALK -> "WALK"
            TransportMode.CAR -> "DRIVE"
        }
    }

    private fun TransportMode.toGoogleRoutingPreference(): String? {
        return when (this) {
            TransportMode.CAR -> "TRAFFIC_AWARE"
            TransportMode.TRANSIT, TransportMode.WALK -> null
        }
    }

    private fun TransportMode.trafficAwareReason(): String? {
        return when (this) {
            TransportMode.CAR -> "현재 교통 상황을 반영하는 traffic-aware 자동차 경로 기준입니다."
            TransportMode.TRANSIT, TransportMode.WALK -> null
        }
    }

    private fun TransportMode.toKoreanLabel(): String {
        return when (this) {
            TransportMode.TRANSIT -> "대중교통"
            TransportMode.WALK -> "도보"
            TransportMode.CAR -> "자동차"
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
