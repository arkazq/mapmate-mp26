package com.mapmate.data.local

import com.mapmate.domain.model.RouteRealtimeSnapshot

fun RouteRealtimeSnapshot.toEntity(): RouteRealtimeSnapshotEntity {
    return RouteRealtimeSnapshotEntity(
        cacheKey = cacheKey,
        baseRouteDurationMinutes = baseRouteDurationMinutes,
        adjustedRouteDurationMinutes = adjustedRouteDurationMinutes,
        realtimeDelayMinutes = realtimeDelayMinutes,
        providerName = providerName,
        summary = summary,
        reason = reason,
        capturedAtEpochMillis = capturedAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}

fun RouteRealtimeSnapshotEntity.toDomain(): RouteRealtimeSnapshot {
    return RouteRealtimeSnapshot(
        cacheKey = cacheKey,
        baseRouteDurationMinutes = baseRouteDurationMinutes,
        adjustedRouteDurationMinutes = adjustedRouteDurationMinutes,
        realtimeDelayMinutes = realtimeDelayMinutes,
        providerName = providerName,
        summary = summary,
        reason = reason,
        capturedAtEpochMillis = capturedAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
    )
}
