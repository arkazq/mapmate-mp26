package com.mapmate.data.location

internal fun isUsableLocationFix(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    capturedElapsedRealtimeNanos: Long,
    nowElapsedRealtimeNanos: Long,
): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
    longitude.isFinite() && longitude in -180.0..180.0 &&
    accuracyMeters != null && accuracyMeters.isFinite() && accuracyMeters in 0f..3_000f &&
    capturedElapsedRealtimeNanos > 0 && nowElapsedRealtimeNanos >= capturedElapsedRealtimeNanos &&
    nowElapsedRealtimeNanos - capturedElapsedRealtimeNanos <= 5 * 60 * 1_000_000_000L
