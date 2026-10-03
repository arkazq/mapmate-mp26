package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.util.runCatchingCancellable

class FallbackRouteEstimateProvider(
    private val primaryProviders: List<RouteEstimateProvider>,
    private val fallback: RouteEstimateProvider,
) : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        routineId: Long?,
        scheduledDepartureEpochMillis: Long?,
        targetArrivalEpochMillis: Long?,
    ): RouteEstimate {
        val failures = mutableListOf<String>()

        primaryProviders.forEach { provider ->
            val result = runCatchingCancellable {
                provider.getRouteEstimate(
                    origin = origin,
                    destination = destination,
                    transportMode = transportMode,
                    routineId = routineId,
                    scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
                    targetArrivalEpochMillis = targetArrivalEpochMillis,
                )
            }.onFailure { error ->
                failures += error.toStatusReason()
            }.getOrNull()

            if (result != null) return result
        }

        val fallbackEstimate = fallback.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = transportMode,
            routineId = routineId,
            scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
            targetArrivalEpochMillis = targetArrivalEpochMillis,
        )

        return fallbackEstimate.copy(
            isFallbackEstimate = true,
            statusMessage = fallbackEstimate.statusMessage ?: buildFallbackStatusMessage(failures),
        )
    }

    private fun buildFallbackStatusMessage(failures: List<String>): String {
        val detail = failures.distinct().take(2)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")

        return listOfNotNull(
            "경로 정보를 확인하지 못해 기본 예상 시간을 사용했습니다.",
            detail,
        ).joinToString(" ")
    }

    private fun Throwable.toStatusReason(): String {
        val message = message.orEmpty()

        return when {
            message.contains("key", ignoreCase = true) -> "경로 서비스 설정을 확인해 주세요."
            message.contains("supports only", ignoreCase = true) -> "선택한 이동수단의 경로를 제공하지 못했습니다."
            message.contains("latitude", ignoreCase = true) ||
                message.contains("longitude", ignoreCase = true) -> "출발지와 목적지를 다시 선택해 주세요."
            message.contains("empty", ignoreCase = true) ||
                message.contains("duration", ignoreCase = true) -> "요청한 구간의 경로를 찾지 못했습니다."
            else -> "네트워크 연결을 확인한 뒤 다시 조회해 주세요."
        }
    }
}
