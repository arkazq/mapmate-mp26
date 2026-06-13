package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RouteEstimateCacheRepository

class CachingRouteEstimateProvider(
    private val cacheNamespace: String,
    private val primary: RouteEstimateProvider,
    private val cacheRepository: RouteEstimateCacheRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
    ): RouteEstimate {
        val now = nowProvider()
        cacheRepository.deleteExpired(now)

        return runCatching {
            primary.getRouteEstimate(
                origin = origin,
                destination = destination,
                transportMode = transportMode,
            )
        }.onSuccess { estimate ->
            if (!estimate.isFallbackEstimate) {
                cacheRepository.saveEstimate(
                    cacheNamespace = cacheNamespace,
                    origin = origin,
                    destination = destination,
                    transportMode = transportMode,
                    routeEstimate = estimate,
                    capturedAtEpochMillis = now,
                    expiresAtEpochMillis = now + CACHE_TTL_MILLIS,
                )
            }
        }.getOrElse { error ->
            cacheRepository.findFreshEstimate(
                cacheNamespace = cacheNamespace,
                origin = origin,
                destination = destination,
                transportMode = transportMode,
                nowEpochMillis = now,
            )?.copy(
                isFallbackEstimate = true,
                statusMessage = "실제 경로 API를 사용할 수 없어 최근 성공한 경로 예상 시간을 사용했습니다.",
                reason = "최근 성공한 ${cacheNamespace} 경로 예상값을 재사용했습니다. 원래 실패 사유: ${error.toStatusReason()}",
            ) ?: throw error
        }
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

    private companion object {
        const val CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L
    }
}
