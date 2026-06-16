package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.TagoBusArrivalApi
import com.mapmate.data.remote.api.TagoBusStationApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.TagoBusArrivalItem
import com.mapmate.data.remote.dto.TagoBusStationItem
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.provider.TransitArrivalProvider
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TagoBusArrivalProvider(
    private val stationApi: TagoBusStationApi,
    private val arrivalApi: TagoBusArrivalApi,
    private val config: RemoteApiConfig,
) : TransitArrivalProvider {
    override suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate? {
        val busQuery = query as? TransitArrivalQuery.Bus ?: return null
        check(config.hasTagoServiceKey) { "TAGO service key is missing." }

        val stationLatitude = busQuery.stationLatitude ?: return null
        val stationLongitude = busQuery.stationLongitude ?: return null
        val station = stationApi.getNearbyStations(
            serviceKey = config.tagoServiceKey,
            latitude = stationLatitude,
            longitude = stationLongitude,
        ).response?.body?.items?.item
            .orEmpty()
            .filter { it.citycode != null && it.nodeid != null }
            .sortedByDescending { it.matchScore(busQuery, stationLatitude, stationLongitude) }
            .take(MAX_STATION_CANDIDATES)
            .firstNotNullOfOrNull { candidate ->
                val arrivals = arrivalApi.getArrivalsByStation(
                    serviceKey = config.tagoServiceKey,
                    cityCode = candidate.citycode.orEmpty(),
                    nodeId = candidate.nodeid.orEmpty(),
                ).response?.body?.items?.item.orEmpty()

                arrivals.bestMatch(busQuery)?.let { arrival ->
                    candidate to arrival
                }
            } ?: return null

        val arrival = station.second
        val waitSeconds = arrival.arrtime?.takeIf { it >= 0 } ?: return null
        val waitMinutes = ceil(waitSeconds / SECONDS_PER_MINUTE).toInt()
        val stationName = arrival.nodenm ?: station.first.nodenm ?: busQuery.stationName.orEmpty()
        val routeName = arrival.routeno ?: busQuery.routeName.orEmpty()

        return TransitArrivalEstimate(
            waitMinutes = waitMinutes,
            summary = "${stationName} ${routeName}번 버스 ${waitMinutes}분 후 도착 예정",
            providerName = "TAGO Bus Arrival",
            reason = listOfNotNull(
                routeName.takeIf(String::isNotBlank)?.let { "TAGO 노선 $it" },
                arrival.arrprevstationcnt?.let { "남은 정류장 ${it}개" },
                arrival.routetp?.takeIf(String::isNotBlank),
            ).joinToString(" ").ifBlank {
                "TAGO 전국 버스 도착정보 기준입니다."
            },
        )
    }

    private fun List<TagoBusArrivalItem>.bestMatch(
        query: TransitArrivalQuery.Bus,
    ): TagoBusArrivalItem? {
        val routeName = query.routeName.normalizedRouteName()
        return if (routeName.isBlank()) {
            minByOrNull { it.arrtime ?: Int.MAX_VALUE }
        } else {
            filter { it.routeno.normalizedRouteName() == routeName }
                .minByOrNull { it.arrtime ?: Int.MAX_VALUE }
        }
    }

    private fun TagoBusStationItem.matchScore(
        query: TransitArrivalQuery.Bus,
        stationLatitude: Double,
        stationLongitude: Double,
    ): Double {
        val distanceMeters = distanceMeters(
            latitude1 = stationLatitude,
            longitude1 = stationLongitude,
            latitude2 = gpslati,
            longitude2 = gpslong,
        ) ?: MAX_DISTANCE_METERS
        val distanceScore = (MAX_DISTANCE_METERS - distanceMeters).coerceAtLeast(0.0)
        val stationNameScore = if (
            nodenm.normalizedStationName().isNotBlank() &&
            nodenm.normalizedStationName() == query.stationName.normalizedStationName()
        ) {
            STATION_NAME_MATCH_BONUS
        } else {
            0.0
        }

        return distanceScore + stationNameScore
    }

    private fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double?,
        longitude2: Double?,
    ): Double? {
        if (latitude2 == null || longitude2 == null) return null
        val dLat = Math.toRadians(latitude2 - latitude1)
        val dLon = Math.toRadians(longitude2 - longitude1)
        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)
        val a = sin(dLat / 2).pow(2.0) + sin(dLon / 2).pow(2.0) * cos(lat1) * cos(lat2)
        val c = 2 * asin(sqrt(a))
        return EARTH_RADIUS_METERS * c
    }

    private fun String?.normalizedStationName(): String {
        return orEmpty()
            .replace("\\s+".toRegex(), "")
            .trim()
    }

    private fun String?.normalizedRouteName(): String {
        return orEmpty()
            .replace("\\([^)]*\\)".toRegex(), "")
            .replace("\\[[^]]*]".toRegex(), "")
            .replace("\\s+".toRegex(), "")
            .replace("번", "")
            .trim()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MAX_DISTANCE_METERS = 500.0
        const val STATION_NAME_MATCH_BONUS = 1_000.0
        const val MAX_STATION_CANDIDATES = 3
    }
}
