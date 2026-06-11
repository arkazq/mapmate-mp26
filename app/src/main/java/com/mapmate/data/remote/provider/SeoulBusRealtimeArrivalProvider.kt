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

        val busRouteId = busQuery.busRouteId?.takeIf(String::isNotBlank) ?: return null
        val responseXml = api.getArrivalsByRouteAll(
            serviceKey = config.seoulBusServiceKey,
            busRouteId = busRouteId,
        ).string()

        val item = SeoulBusArrivalXmlParser.parse(responseXml)
            .firstOrNull { it.matches(busQuery) }
            ?: return null
        val waitMinutes = item.firstWaitMinutes() ?: return null

        return TransitArrivalEstimate(
            waitMinutes = waitMinutes,
            summary = "${item.stationName ?: busQuery.stationName.orEmpty()} first bus arrival in ${waitMinutes} min",
            providerName = "Seoul Bus Realtime",
            reason = listOfNotNull(
                item.routeName?.takeIf(String::isNotBlank),
                item.arrivalMessage1?.takeIf(String::isNotBlank),
            ).joinToString(" ").ifBlank {
                "Seoul realtime bus arrival."
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

    private fun String?.normalizedStationName(): String {
        return this.orEmpty()
            .replace("\\s+".toRegex(), "")
            .trim()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
