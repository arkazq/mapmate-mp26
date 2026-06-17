package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider

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
        val failures = mutableListOf<RouteProviderFailure>()

        primaryProviders.forEach { provider ->
            val result = runCatching {
                provider.getRouteEstimate(
                    origin = origin,
                    destination = destination,
                    transportMode = transportMode,
                    routineId = routineId,
                    scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
                    targetArrivalEpochMillis = targetArrivalEpochMillis,
                )
            }.onFailure { error ->
                failures += RouteProviderFailure(
                    providerName = provider.providerLabel(),
                    reason = error.toStatusReason(),
                )
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

    private fun buildFallbackStatusMessage(failures: List<RouteProviderFailure>): String {
        val detail = failures
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { "${it.providerName}: ${it.reason}" }

        return listOfNotNull(
            "실제 경로 API를 사용할 수 없어 기본 예상 시간을 사용했습니다.",
            detail?.let { "실패 사유: $it" },
        ).joinToString(" ")
    }

    private fun RouteEstimateProvider.providerLabel(): String {
        return javaClass.simpleName
            ?.removeSuffix("RouteEstimateProvider")
            ?.removeSuffix("EstimateProvider")
            ?.takeIf(String::isNotBlank)
            ?: "Route API"
    }

    private fun Throwable.toStatusReason(): String {
        val message = message.orEmpty()

        return when {
            message.contains("key", ignoreCase = true) -> "API key 없음"
            message.contains("supports only", ignoreCase = true) -> "이동수단 미지원"
            message.contains("latitude", ignoreCase = true) ||
                message.contains("longitude", ignoreCase = true) -> "좌표 없음"
            message.contains("empty", ignoreCase = true) ||
                message.contains("duration", ignoreCase = true) -> "경로 없음"
            else -> "요청 실패"
        }
    }

    private data class RouteProviderFailure(
        val providerName: String,
        val reason: String,
    )
}
