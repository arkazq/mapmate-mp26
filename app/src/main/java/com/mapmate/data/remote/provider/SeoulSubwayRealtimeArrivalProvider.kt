package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulSubwayRealtimeApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.SeoulSubwayArrivalItem
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.provider.TransitArrivalProvider
import kotlin.math.ceil

class SeoulSubwayRealtimeArrivalProvider(
    private val api: SeoulSubwayRealtimeApi,
    private val config: RemoteApiConfig,
) : TransitArrivalProvider {
    override suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate? {
        val subwayQuery = query as? TransitArrivalQuery.Subway ?: return null
        check(config.hasSeoulOpenApiKey) { "Seoul Open API key is missing." }

        val stationName = subwayQuery.stationName
            ?.toSeoulSubwayStationName()
            ?.takeIf(String::isNotBlank)
            ?: return null

        val response = api.getRealtimeStationArrival(
            apiKey = config.seoulOpenApiKey,
            stationName = stationName,
        )
        val arrival = response.realtimeArrivalList
            .mapNotNull { it.toArrivalCandidate() }
            .minByOrNull { it.waitMinutes }
            ?: return null

        return TransitArrivalEstimate(
            waitMinutes = arrival.waitMinutes,
            summary = "${stationName} station first subway arrival in ${arrival.waitMinutes} min",
            providerName = "Seoul Subway Realtime",
            reason = listOfNotNull(
                arrival.item.trainLineName?.takeIf(String::isNotBlank),
                arrival.item.direction?.takeIf(String::isNotBlank),
                arrival.item.arrivalMessage?.takeIf(String::isNotBlank),
            ).joinToString(" ").ifBlank {
                "Seoul realtime subway arrival."
            },
        )
    }

    private fun SeoulSubwayArrivalItem.toArrivalCandidate(): ArrivalCandidate? {
        val waitMinutes = arrivalSeconds.toWaitMinutes()
            ?: arrivalMessage.toWaitMinutesFromMessage()
            ?: return null

        return ArrivalCandidate(
            waitMinutes = waitMinutes,
            item = this,
        )
    }

    private fun String?.toWaitMinutes(): Int? {
        val seconds = this?.toIntOrNull() ?: return null
        if (seconds < 0) return null
        return ceil(seconds / SECONDS_PER_MINUTE).toInt()
    }

    private fun String?.toWaitMinutesFromMessage(): Int? {
        val message = this.orEmpty()
        Regex("(\\d+)\\s*분").find(message)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            return it
        }
        return when {
            "도착" in message || "진입" in message -> 0
            "전역" in message -> 1
            else -> null
        }
    }

    private fun String.toSeoulSubwayStationName(): String {
        val trimmed = trim()
        return if (trimmed.length > 1 && trimmed.endsWith("역")) {
            trimmed.dropLast(1)
        } else {
            trimmed
        }
    }

    private data class ArrivalCandidate(
        val waitMinutes: Int,
        val item: SeoulSubwayArrivalItem,
    )

    private companion object {
        const val SECONDS_PER_MINUTE = 60.0
    }
}
