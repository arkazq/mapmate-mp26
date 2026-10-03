package com.mapmate.domain.calculator

fun isWithinRealtimeDepartureWindow(
    scheduledDepartureEpochMillis: Long?,
    nowEpochMillis: Long,
): Boolean {
    val departureAt = scheduledDepartureEpochMillis ?: return false
    return departureAt >= nowEpochMillis && departureAt - nowEpochMillis <= 30 * 60_000L
}
