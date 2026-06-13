package com.mapmate.domain.repository

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode

interface RouteEstimateCacheRepository {
    suspend fun saveEstimate(
        cacheNamespace: String,
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        routeEstimate: RouteEstimate,
        capturedAtEpochMillis: Long,
        expiresAtEpochMillis: Long,
    )

    suspend fun findFreshEstimate(
        cacheNamespace: String,
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        nowEpochMillis: Long,
    ): RouteEstimate?

    suspend fun deleteExpired(nowEpochMillis: Long)
}
