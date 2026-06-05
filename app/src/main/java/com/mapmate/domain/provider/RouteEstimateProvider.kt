package com.mapmate.domain.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode

interface RouteEstimateProvider {
    suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
    ): RouteEstimate
}
