package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.OdsayApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.OdsayPath
import com.mapmate.data.remote.dto.OdsayPathInfo
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.TransitArrivalProvider
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class OdsayRouteEstimateProvider(
    private val api: OdsayApi,
    private val config: RemoteApiConfig,
    private val transitArrivalProvider: TransitArrivalProvider? = null,
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

        val realtimeArrival = path.firstTransitArrivalQuery()?.let { query ->
            runCatching {
                transitArrivalProvider?.getArrivalEstimate(query)
            }.getOrNull()
        }
        val realtimeDelayMinutes = realtimeArrival?.extraDelayMinutes().orZero()
        val estimatedMinutes = totalTime + realtimeDelayMinutes

        return RouteEstimate(
            estimatedMinutes = estimatedMinutes,
            summary = "${origin.name} to ${destination.name} transit estimate ${estimatedMinutes} min",
            providerName = if (realtimeArrival != null) {
                "ODsay + ${realtimeArrival.providerName}"
            } else {
                "ODsay"
            },
            reason = buildOdsayReason(
                pathInfo = pathInfo,
                realtimeArrival = realtimeArrival,
                realtimeDelayMinutes = realtimeDelayMinutes,
            ),
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

    private fun TransitArrivalEstimate.extraDelayMinutes(): Int {
        return (waitMinutes - PLANNED_WAIT_BASELINE_MINUTES).coerceAtLeast(0)
    }

    private fun Int?.orZero(): Int = this ?: 0

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
    }
}
