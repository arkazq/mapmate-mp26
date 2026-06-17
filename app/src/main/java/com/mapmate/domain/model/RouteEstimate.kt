package com.mapmate.domain.model

data class RouteEstimate(
    val estimatedMinutes: Int,
    val summary: String,
    val providerName: String,
    val reason: String,
    val isFallbackEstimate: Boolean = false,
    val statusMessage: String? = null,
    val segments: List<RouteSegment> = emptyList(),
    val hasRealtimeAdjustment: Boolean = false,
    val boardingAdvice: RouteBoardingAdvice? = null,
)

data class RouteBoardingAdvice(
    val selectedCandidateIndex: Int,
    val candidateCount: Int,
    val routeName: String?,
    val stationName: String?,
    val accessMinutes: Int?,
    val realtimeWaitMinutes: Int?,
    val slackMinutes: Int?,
    val status: RouteBoardingStatus,
    val estimatedTotalMinutes: Int,
    val safeDepartureEpochMillis: Long? = null,
    val earlyDepartureRequiredMinutes: Int? = null,
    val mayMissTargetArrival: Boolean = false,
    val alternatives: List<RouteBoardingAlternative> = emptyList(),
)

data class RouteBoardingAlternative(
    val candidateIndex: Int,
    val routeName: String?,
    val stationName: String?,
    val accessMinutes: Int?,
    val realtimeWaitMinutes: Int?,
    val slackMinutes: Int?,
    val status: RouteBoardingStatus,
    val estimatedTotalMinutes: Int,
    val safeDepartureEpochMillis: Long? = null,
    val earlyDepartureRequiredMinutes: Int? = null,
    val mayMissTargetArrival: Boolean = false,
)

enum class RouteBoardingStatus {
    BOARDABLE,
    TIGHT,
    MISS_RISK,
    REALTIME_UNAVAILABLE,
    NO_FIRST_BUS,
}
