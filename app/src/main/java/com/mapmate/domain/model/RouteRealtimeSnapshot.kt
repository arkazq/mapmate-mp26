package com.mapmate.domain.model

data class RouteRealtimeSnapshot(
    val cacheKey: String,
    val baseRouteDurationMinutes: Int,
    val adjustedRouteDurationMinutes: Int,
    val realtimeDelayMinutes: Int,
    val providerName: String,
    val summary: String,
    val reason: String,
    val capturedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
) {
    fun isFresh(nowEpochMillis: Long): Boolean {
        return expiresAtEpochMillis >= nowEpochMillis
    }
}
