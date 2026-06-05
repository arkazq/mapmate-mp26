package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.OdsayApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider

class OdsayRouteEstimateProvider(
    private val api: OdsayApi,
    private val config: RemoteApiConfig,
) : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        destination: Destination,
        transportMode: TransportMode,
    ): RouteEstimate {
        check(transportMode == TransportMode.TRANSIT) {
            "ODsay route estimate supports only public transit."
        }
        check(config.hasOdsayKey) { "ODsay API key is missing." }

        val origin = requireNotNull(config.origin?.takeIf { it.isValid() }) {
            "Origin coordinate is missing."
        }
        val destinationLatitude = requireNotNull(destination.latitude) {
            "Destination latitude is missing."
        }
        val destinationLongitude = requireNotNull(destination.longitude) {
            "Destination longitude is missing."
        }

        val response = api.searchPublicTransitPath(
            startLongitude = origin.longitude,
            startLatitude = origin.latitude,
            endLongitude = destinationLongitude,
            endLatitude = destinationLatitude,
            apiKey = config.odsayApiKey,
        )
        val pathInfo = response.result?.path
            ?.firstOrNull()
            ?.info
        val totalTime = pathInfo?.totalTime
        check(totalTime != null && totalTime > 0) {
            response.error?.msg ?: "ODsay route estimate was empty."
        }

        return RouteEstimate(
            estimatedMinutes = totalTime,
            summary = "${destination.name}까지 대중교통 기준 ${totalTime}분 예상",
            providerName = "ODsay",
            reason = buildOdsayReason(pathInfo),
        )
    }

    private fun buildOdsayReason(pathInfo: com.mapmate.data.remote.dto.OdsayPathInfo): String {
        val transitCount = listOfNotNull(
            pathInfo.busTransitCount?.let { "버스 ${it}회" },
            pathInfo.subwayTransitCount?.let { "지하철 ${it}회" },
        ).joinToString(", ")
        val stationSummary = listOfNotNull(
            pathInfo.firstStartStation?.takeIf(String::isNotBlank),
            pathInfo.lastEndStation?.takeIf(String::isNotBlank),
        ).joinToString(" → ")

        return listOf(
            "ODsay 대중교통 경로 기준입니다.",
            transitCount.takeIf(String::isNotBlank),
            stationSummary.takeIf(String::isNotBlank),
        ).filterNotNull().joinToString(" ")
    }
}
