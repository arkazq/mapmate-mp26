package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulBusPositionApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.model.TransitOperationStatus
import com.mapmate.domain.provider.TransitOperationStatusProvider

class SeoulBusOperationStatusProvider(
    private val api: SeoulBusPositionApi,
    private val config: RemoteApiConfig,
) : TransitOperationStatusProvider {
    override suspend fun getOperationStatus(query: TransitArrivalQuery): TransitOperationStatus? {
        val busQuery = query as? TransitArrivalQuery.Bus ?: return null
        check(config.hasSeoulBusServiceKey) { "Seoul bus service key is missing." }

        val busRouteId = busQuery.busRouteId?.takeIf(String::isNotBlank) ?: return null
        val positions = SeoulBusPositionXmlParser.parse(
            api.getBusPositionsByRoute(
                serviceKey = config.seoulBusServiceKey,
                busRouteId = busRouteId,
            ).string(),
        )
        if (positions.isEmpty()) return null

        val arrivingCount = positions.count { it.isArriving }
        return TransitOperationStatus(
            summary = "노선 운행 차량 ${positions.size}대 확인",
            providerName = "Seoul Bus Position",
            reason = if (arrivingCount > 0) {
                "정류장 접근 차량 ${arrivingCount}대가 포함된 서울 버스 위치정보입니다."
            } else {
                "서울 버스 위치정보에서 운행 중 차량 ${positions.size}대를 확인했습니다."
            },
        )
    }
}
