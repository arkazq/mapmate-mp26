package com.mapmate.data.mock

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider

class MockRouteEstimateProvider : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        routineId: Long?,
        scheduledDepartureEpochMillis: Long?,
    ): RouteEstimate {
        val estimatedMinutes = when (transportMode) {
            TransportMode.TRANSIT -> 42
            TransportMode.WALK -> 25
            TransportMode.CAR -> 30
        }

        return RouteEstimate(
            estimatedMinutes = estimatedMinutes,
            summary = "${origin.name}에서 ${destination.name}까지 ${transportMode.toKoreanLabel()} 기준 ${estimatedMinutes}분 예상",
            providerName = "MockRouteEstimateProvider",
            reason = "Mock ${transportMode.toKoreanLabel()} 예상 시간입니다. 실제 ODsay 또는 Google Routes API 연동 전 임시 데이터입니다.",
        )
    }

    private fun TransportMode.toKoreanLabel(): String {
        return when (this) {
            TransportMode.TRANSIT -> "대중교통"
            TransportMode.WALK -> "도보"
            TransportMode.CAR -> "자동차"
        }
    }
}
