package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider

class FallbackRouteEstimateProvider(
    private val primaryProviders: List<RouteEstimateProvider>,
    private val fallback: RouteEstimateProvider,
) : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
    ): RouteEstimate {
        primaryProviders.forEach { provider ->
            val result = runCatching {
                provider.getRouteEstimate(
                    origin = origin,
                    destination = destination,
                    transportMode = transportMode,
                )
            }.getOrNull()

            if (result != null) return result
        }

        return fallback.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = transportMode,
        )
    }
}
