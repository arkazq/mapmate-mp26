package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.OdsayApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.OdsayPath
import com.mapmate.data.remote.dto.OdsayPathInfo
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteRealtimeSnapshot
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.TransitArrivalProvider
import com.mapmate.domain.repository.RouteRealtimeSnapshotRepository
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class OdsayRouteEstimateProvider(
    private val api: OdsayApi,
    private val config: RemoteApiConfig,
    private val transitArrivalProvider: TransitArrivalProvider? = null,
    private val routeRealtimeSnapshotRepository: RouteRealtimeSnapshotRepository? = null,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val snapshotTtlMillis: Long = DEFAULT_SNAPSHOT_TTL_MILLIS,
) : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
    ): RouteEstimate {
        check(transportMode == TransportMode.TRANSIT) {
            "ODsay route estimate supports only public transit."
        }
        check(config.hasOdsayKey) { "ODsay API key is missing." }

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

        val response = api.searchPublicTransitPath(
            startLongitude = originLongitude,
            startLatitude = originLatitude,
            endLongitude = destinationLongitude,
            endLatitude = destinationLatitude,
            apiKey = config.odsayApiKey,
        )
        val path = response.result?.path?.firstOrNull()
        val pathInfo = path?.info
            ?: error(response.error?.msg ?: "ODsay route estimate was empty.")
        val totalTime = pathInfo.totalTime
        check(totalTime != null && totalTime > 0) {
            response.error?.msg ?: "ODsay route estimate was empty."
        }

        val transitArrivalQuery = path.firstTransitArrivalQuery()
        val snapshotCacheKey = transitArrivalQuery?.toSnapshotCacheKey(
            origin = origin,
            destination = destination,
            totalTime = totalTime,
        )
        val now = nowEpochMillis()
        cleanupExpiredSnapshots(now)

        val realtimeArrival = transitArrivalQuery?.let { query ->
            runCatching {
                transitArrivalProvider?.getArrivalEstimate(query)
            }.getOrNull()
        }

        if (realtimeArrival != null) {
            val realtimeDelayMinutes = realtimeArrival.extraDelayMinutes()
            val estimatedMinutes = totalTime + realtimeDelayMinutes
            snapshotCacheKey?.let { cacheKey ->
                saveRealtimeSnapshot(
                    cacheKey = cacheKey,
                    baseRouteDurationMinutes = totalTime,
                    estimatedMinutes = estimatedMinutes,
                    realtimeDelayMinutes = realtimeDelayMinutes,
                    realtimeArrival = realtimeArrival,
                    capturedAtEpochMillis = now,
                )
            }

            return RouteEstimate(
                estimatedMinutes = estimatedMinutes,
                summary = "${origin.name} to ${destination.name} transit estimate ${estimatedMinutes} min",
                providerName = "ODsay + ${realtimeArrival.providerName}",
                reason = buildOdsayReason(
                    pathInfo = pathInfo,
                    realtimeArrival = realtimeArrival,
                    realtimeDelayMinutes = realtimeDelayMinutes,
                ),
            )
        }

        val cachedSnapshot = snapshotCacheKey?.let { cacheKey ->
            findFreshSnapshot(
                cacheKey = cacheKey,
                nowEpochMillis = now,
            )
        }
        val estimatedMinutes = cachedSnapshot?.adjustedRouteDurationMinutes ?: totalTime

        return RouteEstimate(
            estimatedMinutes = estimatedMinutes,
            summary = "${origin.name} to ${destination.name} transit estimate ${estimatedMinutes} min",
            providerName = if (cachedSnapshot != null) {
                "ODsay + ${cachedSnapshot.providerName} snapshot"
            } else {
                "ODsay"
            },
            reason = if (cachedSnapshot != null) {
                buildCachedSnapshotReason(
                    pathInfo = pathInfo,
                    snapshot = cachedSnapshot,
                )
            } else {
                buildOdsayReason(
                    pathInfo = pathInfo,
                    realtimeArrival = null,
                    realtimeDelayMinutes = 0,
                )
            },
            statusMessage = cachedSnapshot?.let {
                "실시간 도착정보를 새로 확인하지 못해 최근 성공 보정값을 사용했습니다."
            },
        )
    }

    private fun buildOdsayReason(
        pathInfo: OdsayPathInfo,
        realtimeArrival: TransitArrivalEstimate?,
        realtimeDelayMinutes: Int,
    ): String {
        val transitCount = listOfNotNull(
            pathInfo.busTransitCount?.let { "bus ${it}" },
            pathInfo.subwayTransitCount?.let { "subway ${it}" },
        ).joinToString(", ")
        val stationSummary = listOfNotNull(
            pathInfo.firstStartStation?.takeIf(String::isNotBlank),
            pathInfo.lastEndStation?.takeIf(String::isNotBlank),
        ).joinToString(" to ")
        val realtimeSummary = realtimeArrival?.let {
            "Realtime first arrival ${it.waitMinutes} min; added ${realtimeDelayMinutes} min delay."
        }

        return listOf(
            "ODsay transit route estimate.",
            transitCount.takeIf(String::isNotBlank),
            stationSummary.takeIf(String::isNotBlank),
            realtimeSummary,
        ).filterNotNull().joinToString(" ")
    }

    private fun buildCachedSnapshotReason(
        pathInfo: OdsayPathInfo,
        snapshot: RouteRealtimeSnapshot,
    ): String {
        return listOf(
            buildOdsayReason(
                pathInfo = pathInfo,
                realtimeArrival = null,
                realtimeDelayMinutes = 0,
            ),
            "Cached realtime snapshot added ${snapshot.realtimeDelayMinutes} min delay.",
            snapshot.summary.takeIf(String::isNotBlank),
        ).filterNotNull().joinToString(" ")
    }

    private suspend fun saveRealtimeSnapshot(
        cacheKey: String,
        baseRouteDurationMinutes: Int,
        estimatedMinutes: Int,
        realtimeDelayMinutes: Int,
        realtimeArrival: TransitArrivalEstimate,
        capturedAtEpochMillis: Long,
    ) {
        val snapshot = RouteRealtimeSnapshot(
            cacheKey = cacheKey,
            baseRouteDurationMinutes = baseRouteDurationMinutes,
            adjustedRouteDurationMinutes = estimatedMinutes,
            realtimeDelayMinutes = realtimeDelayMinutes,
            providerName = realtimeArrival.providerName,
            summary = realtimeArrival.summary,
            reason = realtimeArrival.reason,
            capturedAtEpochMillis = capturedAtEpochMillis,
            expiresAtEpochMillis = capturedAtEpochMillis + snapshotTtlMillis,
        )
        runCatching {
            routeRealtimeSnapshotRepository?.saveSnapshot(snapshot)
        }
    }

    private suspend fun findFreshSnapshot(
        cacheKey: String,
        nowEpochMillis: Long,
    ): RouteRealtimeSnapshot? {
        return runCatching {
            routeRealtimeSnapshotRepository?.findFreshSnapshot(
                cacheKey = cacheKey,
                nowEpochMillis = nowEpochMillis,
            )
        }.getOrNull()
    }

    private suspend fun cleanupExpiredSnapshots(nowEpochMillis: Long) {
        runCatching {
            routeRealtimeSnapshotRepository?.deleteExpiredSnapshots(nowEpochMillis)
        }
    }

    private fun OdsayPath?.firstTransitArrivalQuery(): TransitArrivalQuery? {
        val transitSubPath = this?.subPath?.firstOrNull {
            it.trafficType == TRAFFIC_TYPE_SUBWAY || it.trafficType == TRAFFIC_TYPE_BUS
        } ?: return null

        val firstLane = transitSubPath.lane.firstOrNull()
        return when (transitSubPath.trafficType) {
            TRAFFIC_TYPE_BUS -> TransitArrivalQuery.Bus(
                stationName = transitSubPath.startName,
                stationId = transitSubPath.startId.asString(),
                stationArsId = transitSubPath.startArsId.asString(),
                busRouteId = firstLane?.routeId.asString()
                    ?: firstLane?.busId.asString(),
                routeName = firstLane?.busNo
                    ?: firstLane?.routeNm
                    ?: firstLane?.name,
            )
            TRAFFIC_TYPE_SUBWAY -> TransitArrivalQuery.Subway(
                stationName = transitSubPath.startName,
                lineName = firstLane?.name,
                direction = transitSubPath.endName,
            )
            else -> null
        }
    }

    private fun TransitArrivalQuery.toSnapshotCacheKey(
        origin: Destination,
        destination: Destination,
        totalTime: Int,
    ): String {
        return listOf(
            "transit",
            origin.latitude.cacheCoordinate(),
            origin.longitude.cacheCoordinate(),
            destination.latitude.cacheCoordinate(),
            destination.longitude.cacheCoordinate(),
            totalTime.toString(),
            toCachePart(),
        ).joinToString("|")
    }

    private fun TransitArrivalQuery.toCachePart(): String {
        return when (this) {
            is TransitArrivalQuery.Bus -> listOf(
                "bus",
                stationName.cacheValue(),
                stationId.cacheValue(),
                stationArsId.cacheValue(),
                busRouteId.cacheValue(),
                routeName.cacheValue(),
            )
            is TransitArrivalQuery.Subway -> listOf(
                "subway",
                stationName.cacheValue(),
                lineName.cacheValue(),
                direction.cacheValue(),
            )
        }.joinToString(":")
    }

    private fun Double?.cacheCoordinate(): String {
        return this?.let { String.format(Locale.US, "%.5f", it) } ?: "_"
    }

    private fun String?.cacheValue(): String {
        return this
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf(String::isNotBlank)
            ?: "_"
    }

    private fun TransitArrivalEstimate.extraDelayMinutes(): Int {
        return (waitMinutes - PLANNED_WAIT_BASELINE_MINUTES).coerceAtLeast(0)
    }

    private fun JsonElement?.asString(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private companion object {
        const val TRAFFIC_TYPE_SUBWAY = 1
        const val TRAFFIC_TYPE_BUS = 2
        const val PLANNED_WAIT_BASELINE_MINUTES = 5
        const val DEFAULT_SNAPSHOT_TTL_MILLIS = 20 * 60 * 1000L
    }
}
