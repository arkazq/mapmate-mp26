package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulBusArrivalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.provider.TransitArrivalProvider
import com.mapmate.domain.util.runCatchingCancellable
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
        val arrivals = runCatchingCancellable {
            val responseXml = api.getArrivalsByStationUid(
                serviceKey = config.seoulBusServiceKey,
                stationArsId = stationArsId,
            ).readSeoulBusXml()
            SeoulBusArrivalXmlParser.parse(responseXml)
        }.getOrNull() ?: return null

        val item = arrivals.firstRouteMatchOrNull(routeName)
            ?: return null
        val waitCandidateMinutes = item.waitCandidateMinutes()
        val waitMinutes = waitCandidateMinutes.firstOrNull() ?: return null

        return item.toEstimate(
            busQuery = busQuery,
            waitMinutes = waitMinutes,
            waitCandidateMinutes = waitCandidateMinutes,
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
        ).readSeoulBusXml()

        val item = SeoulBusArrivalXmlParser.parse(responseXml)
            .firstOrNull { it.matches(busQuery) }
            ?: return null
        val waitCandidateMinutes = item.waitCandidateMinutes()
        val waitMinutes = waitCandidateMinutes.firstOrNull() ?: return null

        return item.toEstimate(
            busQuery = busQuery,
            waitMinutes = waitMinutes,
            waitCandidateMinutes = waitCandidateMinutes,
            reasonSuffix = "busRouteId ${busRouteId} 기준",
        )
    }

    private fun SeoulBusArrivalItem.toEstimate(
        busQuery: TransitArrivalQuery.Bus,
        waitMinutes: Int,
        waitCandidateMinutes: List<Int>,
        reasonSuffix: String,
    ): TransitArrivalEstimate {
        return TransitArrivalEstimate(
            waitMinutes = waitMinutes,
            waitCandidateMinutes = waitCandidateMinutes,
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
        val requestedRoute = query.routeName.normalizedRouteName()
        if (requestedRoute.isNotBlank() && routeNames().isNotEmpty() &&
            routeNames().none { it.normalizedRouteName() == requestedRoute }
        ) return false
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
        }.minByOrNull { it.waitCandidateMinutes().firstOrNull() ?: Int.MAX_VALUE }
    }

    private fun SeoulBusArrivalItem.routeNames(): List<String> {
        return listOfNotNull(routeName, routeShortName)
    }

    private fun SeoulBusArrivalItem.waitCandidateMinutes(): List<Int> {
        return listOfNotNull(
            arrivalWaitMinutes(arrivalSeconds1, arrivalMessage1),
            arrivalWaitMinutes(arrivalSeconds2, arrivalMessage2),
        )
            .distinct()
            .sorted()
    }

    private fun arrivalWaitMinutes(seconds: Int?, message: String?): Int? {
        val normalized = message.orEmpty().replace("\\s+".toRegex(), "")
        if (listOf("운행종료", "운행중단", "출발대기", "운행대기", "정보없", "정보가없", "예정없").any { it in normalized }) return null
        if (seconds != null && seconds > 0) return ceil(seconds / SECONDS_PER_MINUTE).toInt()
        return message.toWaitMinutesFromMessage()
    }

    private fun String?.toWaitMinutesFromMessage(): Int? {
        val message = this.orEmpty()
        Regex("(\\d+)\\s*분").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it
        }
        val normalized = message.replace("\\s+".toRegex(), "")
        return when {
            normalized.startsWith("곧도착") || normalized == "도착" -> 0
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
