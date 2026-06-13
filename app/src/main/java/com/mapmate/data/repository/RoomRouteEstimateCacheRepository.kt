package com.mapmate.data.repository

import com.mapmate.data.local.RouteEstimateCacheDao
import com.mapmate.data.local.RouteEstimateCacheEntity
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.RouteEstimateCacheRepository
import java.util.Locale

class RoomRouteEstimateCacheRepository(
    private val dao: RouteEstimateCacheDao,
) : RouteEstimateCacheRepository {
    override suspend fun saveEstimate(
        cacheNamespace: String,
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        routeEstimate: RouteEstimate,
        capturedAtEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ) {
        dao.upsert(
            RouteEstimateCacheEntity(
                cacheKey = cacheKey(cacheNamespace, origin, destination, transportMode),
                cacheNamespace = cacheNamespace,
                transportMode = transportMode.name,
                originName = origin.name,
                originAddress = origin.address,
                originLatitude = origin.latitude,
                originLongitude = origin.longitude,
                destinationName = destination.name,
                destinationAddress = destination.address,
                destinationLatitude = destination.latitude,
                destinationLongitude = destination.longitude,
                estimatedMinutes = routeEstimate.estimatedMinutes,
                summary = routeEstimate.summary,
                providerName = routeEstimate.providerName,
                reason = routeEstimate.reason,
                capturedAtEpochMillis = capturedAtEpochMillis,
                expiresAtEpochMillis = expiresAtEpochMillis,
            ),
        )
    }

    override suspend fun findFreshEstimate(
        cacheNamespace: String,
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        nowEpochMillis: Long,
    ): RouteEstimate? {
        return dao.findFresh(
            cacheKey = cacheKey(cacheNamespace, origin, destination, transportMode),
            nowEpochMillis = nowEpochMillis,
        )?.toRouteEstimate()
    }

    override suspend fun deleteExpired(nowEpochMillis: Long) {
        dao.deleteExpired(nowEpochMillis)
    }

    private fun RouteEstimateCacheEntity.toRouteEstimate(): RouteEstimate {
        return RouteEstimate(
            estimatedMinutes = estimatedMinutes,
            summary = summary,
            providerName = providerName,
            reason = reason,
        )
    }

    private fun cacheKey(
        cacheNamespace: String,
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
    ): String {
        return listOf(
            cacheNamespace,
            transportMode.name,
            origin.cacheIdentity(),
            destination.cacheIdentity(),
        ).joinToString("|")
    }

    private fun Destination.cacheIdentity(): String {
        val coordinateIdentity = latitude?.let { lat ->
            longitude?.let { lon ->
                "${lat.roundForCache()},${lon.roundForCache()}"
            }
        }

        return coordinateIdentity ?: "${name.trim()}@${address.trim()}".lowercase(Locale.ROOT)
    }

    private fun Double.roundForCache(): String {
        return String.format(Locale.US, "%.5f", this)
    }
}
