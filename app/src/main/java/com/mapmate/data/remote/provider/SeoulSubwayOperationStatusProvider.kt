package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulSubwayTrainPositionApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.SeoulSubwayTrainPositionItem
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.model.TransitOperationStatus
import com.mapmate.domain.provider.TransitOperationStatusProvider

class SeoulSubwayOperationStatusProvider(
    private val api: SeoulSubwayTrainPositionApi,
    private val config: RemoteApiConfig,
) : TransitOperationStatusProvider {
    override suspend fun getOperationStatus(query: TransitArrivalQuery): TransitOperationStatus? {
        val subwayQuery = query as? TransitArrivalQuery.Subway ?: return null
        check(config.hasSeoulOpenApiKey) { "Seoul open API key is missing." }

        val lineName = subwayQuery.lineName?.toSeoulLineName()?.takeIf(String::isNotBlank) ?: return null
        val trains = api.getTrainPositions(
            apiKey = config.seoulOpenApiKey,
            lineName = lineName,
        ).realtimePositionList
        if (trains.isEmpty()) return null

        val nearbyTrains = trains.filter { item ->
            item.statnNm.normalizedStationName() == subwayQuery.stationName.normalizedStationName()
        }
        val directionTrains = subwayQuery.direction
            ?.normalizedStationName()
            ?.takeIf(String::isNotBlank)
            ?.let { direction ->
                trains.filter { item -> item.updnLine.normalizedStationName().contains(direction) }
            }
            .orEmpty()

        return TransitOperationStatus(
            summary = "${lineName} 열차 위치 ${trains.size}건 확인",
            providerName = "Seoul Subway Position",
            reason = buildList {
                add("서울 지하철 실시간 열차 위치정보 기준입니다.")
                if (nearbyTrains.isNotEmpty()) add("현재 역 주변 열차 ${nearbyTrains.size}건")
                if (directionTrains.isNotEmpty()) add("방면 후보 ${directionTrains.size}건")
                mostCommonStatus(trains)?.let { add("주요 상태 $it") }
            }.joinToString(" "),
        )
    }

    private fun mostCommonStatus(items: List<SeoulSubwayTrainPositionItem>): String? {
        return items
            .mapNotNull { it.trainSttus?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { (_, count) -> count }
            ?.key
    }

    private fun String.toSeoulLineName(): String {
        return trim()
            .replace("수도권", "")
            .replace("서울", "")
            .replace(" ", "")
    }

    private fun String?.normalizedStationName(): String {
        return orEmpty()
            .replace("\\s+".toRegex(), "")
            .trim()
    }
}
