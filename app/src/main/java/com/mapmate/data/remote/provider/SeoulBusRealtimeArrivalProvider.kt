package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulBusArrivalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.provider.TransitArrivalProvider
import kotlin.math.ceil

class SeoulBusRealtimeArrivalProvider(
    private val api: SeoulBusArrivalApi,
    private val config: RemoteApiConfig,
) : TransitArrivalProvider {
    override suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate? {
        val busQuery = query as? TransitArrivalQuery.Bus ?: return null
        check(config.hasSeoulBusServiceKey) { "Seoul bus service key is missing." }

        return getArrivalByStationUid(busQuery)
            ?: getArrivalByRouteAll(busQuery)
    }

    private suspend fun getArrivalByStationUid(
        busQuery: TransitArrivalQuery.Bus,
    ): TransitArrivalEstimate? {
        val stationArsId = busQuery.stationArsId?.takeIf(String::isNotBlank) ?: return null
        val routeName = busQuery.routeName?.takeIf(String::isNotBlank) ?: return null
        val responseXml = runCatching {
            api.getArrivalsByStationUid(
                serviceKey = config.seoulBusServiceKey,
                stationArsId = stationArsId,
            ).string()
        }.getOrNull() ?: return null

        val item = SeoulBusArrivalXmlParser.parse(responseXml)
            .firstRouteMatchOrNull(routeName)
            ?: return null
        val waitMinutes = item.firstWaitMinutes() ?: return null

        return item.toEstimate(
            busQuery = busQuery,
            waitMinutes = waitMinutes,
            reasonSuffix = "ARS ${stationArsId} 정류장 기준",
        )
    }

    private suspend fun getArrivalByRouteAll(
        busQuery: TransitArrivalQuery.Bus,
    ): TransitArrivalEstimate? {
        val busRouteId = busQuery.busRouteId?.takeIf(String::isNotBlank) ?: return null
        val responseXml = api.getArrivalsByRouteAll(
            serviceKey = config.seoulBusServiceKey,
            busRouteId = busRouteId,
        ).string()

        val item = SeoulBusArrivalXmlParser.parse(responseXml)
            .firstOrNull { it.matches(busQuery) }
            ?: return null
        val waitMinutes = item.firstWaitMinutes() ?: return null

        return item.toEstimate(
            busQuery = busQuery,
            waitMinutes = waitMinutes,
            reasonSuffix = "busRouteId ${busRouteId} 기준",
        )
    }

    private fun SeoulBusArrivalItem.toEstimate(
        busQuery: TransitArrivalQuery.Bus,
        waitMinutes: Int,
        reasonSuffix: String,
    ): TransitArrivalEstimate {
        return TransitArrivalEstimate(
            waitMinutes = waitMinutes,
            summary = "${stationName ?: busQuery.stationName.orEmpty()} ${displayRouteName(busQuery.routeName)} 버스 ${waitMinutes}분 후 도착 예정",
            providerName = "Seoul Bus Realtime",
            reason = listOfNotNull(
                displayRouteName(busQuery.routeName).takeIf(String::isNotBlank),
                arrivalMessage1?.takeIf(String::isNotBlank),
                reasonSuffix,
            ).joinToString(" ").ifBlank {
                "서울버스 실시간 도착정보 기준입니다."
            },
        )
    }

    private fun SeoulBusArrivalItem.matches(query: TransitArrivalQuery.Bus): Boolean {
        return listOfNotNull(
            stationId != null && stationId == query.stationId,
            stationArsId != null && stationArsId == query.stationArsId,
            stationName != null && stationName.normalizedStationName() == query.stationName.normalizedStationName(),
        ).any { it }
    }

    private fun List<SeoulBusArrivalItem>.firstRouteMatchOrNull(
        routeName: String,
    ): SeoulBusArrivalItem? {
        val normalizedRouteName = routeName.normalizedRouteName()
        if (normalizedRouteName.isBlank()) return null
        return filter { item ->
            item.routeNames().any { it.normalizedRouteName() == normalizedRouteName }
        }.minByOrNull { it.firstWaitMinutes() ?: Int.MAX_VALUE }
    }

    private fun SeoulBusArrivalItem.routeNames(): List<String> {
        return listOfNotNull(routeName, routeShortName)
    }

    private fun SeoulBusArrivalItem.firstWaitMinutes(): Int? {
        val waitSeconds = listOfNotNull(arrivalSeconds1, arrivalSeconds2)
            .filter { it >= 0 }
            .minOrNull()
        if (waitSeconds != null) {
            return ceil(waitSeconds / SECONDS_PER_MINUTE).toInt()
        }

        return listOfNotNull(arrivalMessage1, arrivalMessage2)
            .mapNotNull { it.toWaitMinutesFromMessage() }
            .minOrNull()
    }

    private fun String?.toWaitMinutesFromMessage(): Int? {
        val message = this.orEmpty()
        Regex("(\\d+)\\s*분").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it
        }
        return when {
            "곧" in message || "도착" in message -> 0
            else -> null
        }
    }

    private fun SeoulBusArrivalItem.displayRouteName(fallback: String?): String {
        return routeName?.takeIf(String::isNotBlank)
            ?: routeShortName?.takeIf(String::isNotBlank)
            ?: fallback.orEmpty()
    }

    private fun String?.normalizedStationName(): String {
        return this.orEmpty()
            .replace("\\s+".toRegex(), "")
            .trim()
    }

    private fun String?.normalizedRouteName(): String {
        return this.orEmpty()
            .replace("\\([^)]*\\)".toRegex(), "")
            .replace("\\[[^]]*]".toRegex(), "")
            .replace("\\s+".toRegex(), "")
            .replace("번", "")
            .trim()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
