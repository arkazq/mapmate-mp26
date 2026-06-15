package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.OdsayApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.OdsayPath
import com.mapmate.data.remote.dto.OdsayPathInfo
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteRealtimeSnapshot
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.model.TransitOperationStatus
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.TransitArrivalProvider
import com.mapmate.domain.provider.TransitOperationStatusProvider
import com.mapmate.domain.repository.RouteRealtimeSnapshotRepository
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class OdsayRouteEstimateProvider(
    private val api: OdsayApi,
    private val config: RemoteApiConfig,
    private val transitArrivalProvider: TransitArrivalProvider? = null,
    private val transitOperationStatusProvider: TransitOperationStatusProvider? = null,
    private val routeRealtimeSnapshotRepository: RouteRealtimeSnapshotRepository? = null,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val snapshotTtlMillis: Long = DEFAULT_SNAPSHOT_TTL_MILLIS,
) : RouteEstimateProvider {
    private val routeCandidateEvaluator = RouteCandidateEvaluator()

    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        routineId: Long?,
        scheduledDepartureEpochMillis: Long?,
    ): RouteEstimate {
        check(transportMode == TransportMode.TRANSIT) {
            "ODsay route estimate supports only public transit."
        }
        check(config.hasOdsayKey) { "ODsay API key is missing." }

        val originLatitude = requireNotNull(origin.latitude) {
            "Origin latitude is missing."
        }
        val originLongitude = requireNotNull(origin.longitude) {
            "Origin longitude is missing."
        }
        val destinationLatitude = requireNotNull(destination.latitude) {
            "Destination latitude is missing."
        }
        val destinationLongitude = requireNotNull(destination.longitude) {
            "Destination longitude is missing."
        }

        val response = api.searchPublicTransitPath(
            startLongitude = originLongitude,
            startLatitude = originLatitude,
            endLongitude = destinationLongitude,
            endLatitude = destinationLatitude,
            apiKey = config.odsayApiKey,
        )
        val paths = response.result?.path.orEmpty()
        check(paths.isNotEmpty()) {
            response.error?.msg ?: "ODsay route estimate was empty."
        }

        val now = nowEpochMillis()
        cleanupExpiredSnapshots(now)
        val applyRealtimeArrival = shouldApplyRealtimeArrival(
            nowEpochMillis = now,
            scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
        )
        val skippedRealtimeStatus = skippedRealtimeStatus(
            nowEpochMillis = now,
            scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
        )
        val candidates = paths
            .take(MAX_CANDIDATE_PATH_COUNT)
            .mapIndexedNotNull { index, path ->
                estimateCandidatePath(
                    pathIndex = index,
                    path = path,
                    origin = origin,
                    destination = destination,
                    routineId = routineId,
                    applyRealtimeArrival = applyRealtimeArrival,
                    skippedRealtimeStatus = skippedRealtimeStatus,
                    nowEpochMillis = now,
                )
            }
        val selection = routeCandidateEvaluator.select(
            candidates.map { candidate ->
                candidate.toEvaluationInput(
                    scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
                    nowEpochMillis = now,
                )
            },
        )
        val selected = selection?.selected?.input?.pathIndex?.let { selectedPathIndex ->
            candidates.firstOrNull { it.pathIndex == selectedPathIndex }
        }
            ?: error(response.error?.msg ?: "ODsay route estimate was empty.")
        val operationStatus = selected.firstTransitQuery?.let { query ->
            runCatching {
                transitOperationStatusProvider?.getOperationStatus(query)
            }.getOrNull()
        }

        return RouteEstimate(
            estimatedMinutes = selected.adjustedTotalMinutes,
            summary = "${origin.name} to ${destination.name} transit estimate ${selected.adjustedTotalMinutes} min",
            providerName = selected.providerName,
            reason = if (selected.cachedSnapshot != null) {
                buildCachedSnapshotReason(
                    pathInfo = selected.pathInfo,
                    snapshot = selected.cachedSnapshot,
                ).withCandidateReason(selected, candidates.size, applyRealtimeArrival, selection)
            } else {
                buildOdsayReason(
                    pathInfo = selected.pathInfo,
                    realtimeArrival = selected.realtimeArrival,
                    realtimeDelayMinutes = selected.realtimeAdjustmentMinutes,
                    operationStatus = operationStatus,
                ).withCandidateReason(selected, candidates.size, applyRealtimeArrival, selection)
            },
            statusMessage = selected.cachedSnapshot?.let {
                "실시간 도착정보를 새로 확인하지 못해 최근 성공 보정값을 사용했습니다."
            },
            segments = selected.routeSegments,
            hasRealtimeAdjustment = selected.hasRealtimeAdjustment,
        )
    }

    private suspend fun estimateCandidatePath(
        pathIndex: Int,
        path: OdsayPath,
        origin: Destination,
        destination: Destination,
        routineId: Long?,
        applyRealtimeArrival: Boolean,
        skippedRealtimeStatus: RealtimeStatus,
        nowEpochMillis: Long,
    ): RouteCandidateEstimate? {
        val pathInfo = path.info ?: return null
        val totalTime = pathInfo.totalTime?.takeIf { it > 0 } ?: return null
        val routeSegments = runCatching {
            path.toRouteSegments(routineId = routineId)
        }.getOrDefault(emptyList())
        val firstTransitInfo = path.firstTransitArrivalInfo()
        val firstTransitQuery = firstTransitInfo?.query
        val baseCandidate = RouteCandidateEstimate(
            pathIndex = pathIndex,
            pathInfo = pathInfo,
            originalTotalMinutes = totalTime,
            adjustedTotalMinutes = totalTime,
            realtimeAdjustmentMinutes = 0,
            transferCount = pathInfo.transferCount(),
            walkingMinutes = pathInfo.walkingMinutes(path),
            firstTransitQuery = firstTransitQuery,
            realtimeArrival = null,
            cachedSnapshot = null,
            realtimeStatus = if (applyRealtimeArrival) {
                RealtimeStatus.NOT_BUS_FIRST_LEG
            } else {
                skippedRealtimeStatus
            },
            routeSegments = routeSegments,
            firstBusAccessMinutes = firstTransitInfo?.takeIf {
                it.query is TransitArrivalQuery.Bus
            }?.accessMinutes,
        )
        if (!applyRealtimeArrival) return baseCandidate

        val busQuery = firstTransitQuery as? TransitArrivalQuery.Bus ?: return baseCandidate
        val snapshotCacheKey = busQuery.toSnapshotCacheKey(
            origin = origin,
            destination = destination,
            totalTime = totalTime,
        )
        val realtimeResult = runCatching {
            transitArrivalProvider?.getArrivalEstimate(busQuery)
        }
        val realtimeArrival = realtimeResult.getOrNull()
        if (realtimeArrival != null) {
            val realtimeDelayMinutes = realtimeArrival.extraDelayMinutes()
            val adjustedTotalMinutes = totalTime + realtimeDelayMinutes
            saveRealtimeSnapshot(
                cacheKey = snapshotCacheKey,
                baseRouteDurationMinutes = totalTime,
                estimatedMinutes = adjustedTotalMinutes,
                realtimeDelayMinutes = realtimeDelayMinutes,
                realtimeArrival = realtimeArrival,
                capturedAtEpochMillis = nowEpochMillis,
            )
            return baseCandidate.copy(
                adjustedTotalMinutes = adjustedTotalMinutes,
                realtimeAdjustmentMinutes = realtimeDelayMinutes,
                realtimeArrival = realtimeArrival,
                realtimeStatus = RealtimeStatus.APPLIED,
            )
        }

        val cachedSnapshot = findFreshSnapshot(
            cacheKey = snapshotCacheKey,
            nowEpochMillis = nowEpochMillis,
        )
        if (cachedSnapshot != null) {
            return baseCandidate.copy(
                adjustedTotalMinutes = cachedSnapshot.adjustedRouteDurationMinutes,
                realtimeAdjustmentMinutes = cachedSnapshot.realtimeDelayMinutes,
                cachedSnapshot = cachedSnapshot,
                realtimeStatus = RealtimeStatus.CACHED_SNAPSHOT,
            )
        }

        return baseCandidate.copy(
            realtimeStatus = if (realtimeResult.isFailure) {
                RealtimeStatus.FAILED
            } else {
                RealtimeStatus.UNAVAILABLE
            },
        )
    }

    private fun shouldApplyRealtimeArrival(
        nowEpochMillis: Long,
        scheduledDepartureEpochMillis: Long?,
    ): Boolean {
        val departureAt = scheduledDepartureEpochMillis ?: return false
        val minutesUntilDeparture = (departureAt - nowEpochMillis) / MILLIS_PER_MINUTE
        return minutesUntilDeparture in 0..REALTIME_ARRIVAL_LOOKAHEAD_MINUTES
    }

    private fun skippedRealtimeStatus(
        nowEpochMillis: Long,
        scheduledDepartureEpochMillis: Long?,
    ): RealtimeStatus {
        val departureAt = scheduledDepartureEpochMillis
            ?: return RealtimeStatus.SKIPPED_NO_SCHEDULED_DEPARTURE
        return if (departureAt < nowEpochMillis) {
            RealtimeStatus.SKIPPED_DEPARTURE_PASSED
        } else {
            RealtimeStatus.SKIPPED_TOO_EARLY
        }
    }

    private fun OdsayPathInfo.transferCount(): Int {
        val transitCount = (busTransitCount ?: 0) + (subwayTransitCount ?: 0)
        return (transitCount - 1).coerceAtLeast(0)
    }

    private fun OdsayPathInfo.walkingMinutes(path: OdsayPath): Int {
        return totalWalk ?: path.subPath
            .filter { it.trafficType == TRAFFIC_TYPE_WALK }
            .sumOf { it.sectionTime ?: 0 }
    }

    private val RouteCandidateEstimate.providerName: String
        get() {
            realtimeArrival?.let { return "ODsay + ${it.providerName}" }
            cachedSnapshot?.let { return "ODsay + ${it.providerName} snapshot" }
            return "ODsay"
        }

    private fun String.withCandidateReason(
        selected: RouteCandidateEstimate,
        candidateCount: Int,
        applyRealtimeArrival: Boolean,
        selection: RouteCandidateSelection?,
    ): String {
        val candidateReason = "Selected ODsay candidate ${selected.pathIndex + 1}/${candidateCount}; " +
            "adjusted=${selected.adjustedTotalMinutes} min; " +
            "realtime=${selected.realtimeStatus}; " +
            "applyRealtime=$applyRealtimeArrival."
        return listOfNotNull(this, candidateReason, selection?.reason).joinToString(" ")
    }

    private fun buildOdsayReason(
        pathInfo: OdsayPathInfo,
        realtimeArrival: TransitArrivalEstimate?,
        realtimeDelayMinutes: Int,
        operationStatus: TransitOperationStatus? = null,
    ): String {
        val transitCount = listOfNotNull(
            pathInfo.busTransitCount?.let { "bus ${it}" },
            pathInfo.subwayTransitCount?.let { "subway ${it}" },
        ).joinToString(", ")
        val stationSummary = listOfNotNull(
            pathInfo.firstStartStation?.takeIf(String::isNotBlank),
            pathInfo.lastEndStation?.takeIf(String::isNotBlank),
        ).joinToString(" to ")
        val realtimeSummary = realtimeArrival?.let {
            "Realtime first bus arrival ${it.waitMinutes} min; added ${realtimeDelayMinutes} min delay."
        }
        val operationSummary = operationStatus?.let {
            "${it.providerName}: ${it.reason}"
        }

        return listOf(
            "ODsay transit route estimate.",
            transitCount.takeIf(String::isNotBlank),
            stationSummary.takeIf(String::isNotBlank),
            realtimeSummary,
            operationSummary,
        ).filterNotNull().joinToString(" ")
    }

    private fun buildCachedSnapshotReason(
        pathInfo: OdsayPathInfo,
        snapshot: RouteRealtimeSnapshot,
    ): String {
        return listOf(
            buildOdsayReason(
                pathInfo = pathInfo,
                realtimeArrival = null,
                realtimeDelayMinutes = 0,
            ),
            "Cached realtime snapshot added ${snapshot.realtimeDelayMinutes} min delay.",
            snapshot.summary.takeIf(String::isNotBlank),
        ).filterNotNull().joinToString(" ")
    }

    private suspend fun saveRealtimeSnapshot(
        cacheKey: String,
        baseRouteDurationMinutes: Int,
        estimatedMinutes: Int,
        realtimeDelayMinutes: Int,
        realtimeArrival: TransitArrivalEstimate,
        capturedAtEpochMillis: Long,
    ) {
        val snapshot = RouteRealtimeSnapshot(
            cacheKey = cacheKey,
            baseRouteDurationMinutes = baseRouteDurationMinutes,
            adjustedRouteDurationMinutes = estimatedMinutes,
            realtimeDelayMinutes = realtimeDelayMinutes,
            providerName = realtimeArrival.providerName,
            summary = realtimeArrival.summary,
            reason = realtimeArrival.reason,
            capturedAtEpochMillis = capturedAtEpochMillis,
            expiresAtEpochMillis = capturedAtEpochMillis + snapshotTtlMillis,
        )
        runCatching {
            routeRealtimeSnapshotRepository?.saveSnapshot(snapshot)
        }
    }

    private suspend fun findFreshSnapshot(
        cacheKey: String,
        nowEpochMillis: Long,
    ): RouteRealtimeSnapshot? {
        return runCatching {
            routeRealtimeSnapshotRepository?.findFreshSnapshot(
                cacheKey = cacheKey,
                nowEpochMillis = nowEpochMillis,
            )
        }.getOrNull()
    }

    private suspend fun cleanupExpiredSnapshots(nowEpochMillis: Long) {
        runCatching {
            routeRealtimeSnapshotRepository?.deleteExpiredSnapshots(nowEpochMillis)
        }
    }

    private fun OdsayPath?.firstTransitArrivalInfo(): FirstTransitArrivalInfo? {
        val path = this ?: return null
        val transitSubPathIndex = path.subPath.indexOfFirst {
            it.trafficType == TRAFFIC_TYPE_SUBWAY || it.trafficType == TRAFFIC_TYPE_BUS
        }
        if (transitSubPathIndex < 0) return null
        val transitSubPath = path.subPath[transitSubPathIndex]
        val accessMinutes = path.subPath
            .take(transitSubPathIndex)
            .filter { it.trafficType == TRAFFIC_TYPE_WALK }
            .sumOf { it.sectionTime ?: 0 }

        val firstLane = transitSubPath.lane.firstOrNull()
        val query = when (transitSubPath.trafficType) {
            TRAFFIC_TYPE_BUS -> TransitArrivalQuery.Bus(
                stationName = transitSubPath.startName,
                stationId = transitSubPath.startId.asString(),
                stationArsId = transitSubPath.startArsId.asString(),
                busRouteId = firstLane?.routeId.asString()
                    ?: firstLane?.busId.asString(),
                routeName = firstLane?.busNo
                    ?: firstLane?.routeNm
                    ?: firstLane?.name,
                stationLatitude = transitSubPath.startY,
                stationLongitude = transitSubPath.startX,
            )
            TRAFFIC_TYPE_SUBWAY -> TransitArrivalQuery.Subway(
                stationName = transitSubPath.startName,
                lineName = firstLane?.name,
                direction = transitSubPath.endName,
            )
            else -> null
        } ?: return null

        return FirstTransitArrivalInfo(
            query = query,
            accessMinutes = accessMinutes,
        )
    }

    private fun TransitArrivalQuery.toSnapshotCacheKey(
        origin: Destination,
        destination: Destination,
        totalTime: Int,
    ): String {
        return listOf(
            "transit",
            origin.latitude.cacheCoordinate(),
            origin.longitude.cacheCoordinate(),
            destination.latitude.cacheCoordinate(),
            destination.longitude.cacheCoordinate(),
            totalTime.toString(),
            toCachePart(),
        ).joinToString("|")
    }

    private fun TransitArrivalQuery.toCachePart(): String {
        return when (this) {
            is TransitArrivalQuery.Bus -> listOf(
                "bus",
                stationName.cacheValue(),
                stationId.cacheValue(),
                stationArsId.cacheValue(),
                busRouteId.cacheValue(),
                routeName.cacheValue(),
                stationLatitude.cacheCoordinate(),
                stationLongitude.cacheCoordinate(),
            )
            is TransitArrivalQuery.Subway -> listOf(
                "subway",
                stationName.cacheValue(),
                lineName.cacheValue(),
                direction.cacheValue(),
            )
        }.joinToString(":")
    }

    private fun Double?.cacheCoordinate(): String {
        return this?.let { String.format(Locale.US, "%.5f", it) } ?: "_"
    }

    private fun String?.cacheValue(): String {
        return this
            ?.trim()
            ?.lowercase(Locale.US)
            ?.takeIf(String::isNotBlank)
            ?: "_"
    }

    private val RouteCandidateEstimate.hasRealtimeAdjustment: Boolean
        get() = realtimeStatus == RealtimeStatus.APPLIED ||
            realtimeStatus == RealtimeStatus.CACHED_SNAPSHOT

    private fun TransitArrivalEstimate.extraDelayMinutes(): Int {
        return (waitMinutes - PLANNED_WAIT_BASELINE_MINUTES).coerceAtLeast(0)
    }

    private fun RouteCandidateEstimate.toEvaluationInput(
        scheduledDepartureEpochMillis: Long?,
        nowEpochMillis: Long,
    ): RouteCandidateEvaluationInput {
        val busQuery = firstTransitQuery as? TransitArrivalQuery.Bus
        val firstBusBoarding = if (busQuery != null) {
            RouteCandidateBoardingInput(
                routeName = busQuery.routeName,
                stationName = busQuery.stationName,
                accessMinutes = firstBusAccessMinutes ?: 0,
                realtimeWaitMinutes = realtimeArrival?.waitMinutes,
                scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
                nowEpochMillis = nowEpochMillis,
            )
        } else {
            null
        }

        return RouteCandidateEvaluationInput(
            pathIndex = pathIndex,
            adjustedTotalMinutes = adjustedTotalMinutes,
            transferCount = transferCount,
            walkingMinutes = walkingMinutes,
            realtimeStatusRank = realtimeStatus.rank,
            firstBusBoarding = firstBusBoarding,
        )
    }

    private fun JsonElement?.asString(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.contentOrNull
            ?.trim()
            ?.takeIf(String::isNotBlank)
    }

    private data class RouteCandidateEstimate(
        val pathIndex: Int,
        val pathInfo: OdsayPathInfo,
        val originalTotalMinutes: Int,
        val adjustedTotalMinutes: Int,
        val realtimeAdjustmentMinutes: Int,
        val transferCount: Int,
        val walkingMinutes: Int,
        val firstTransitQuery: TransitArrivalQuery?,
        val realtimeArrival: TransitArrivalEstimate?,
        val cachedSnapshot: RouteRealtimeSnapshot?,
        val realtimeStatus: RealtimeStatus,
        val routeSegments: List<RouteSegment>,
        val firstBusAccessMinutes: Int?,
    )

    private data class FirstTransitArrivalInfo(
        val query: TransitArrivalQuery,
        val accessMinutes: Int,
    )

    private enum class RealtimeStatus(
        val rank: Int,
    ) {
        APPLIED(rank = 0),
        CACHED_SNAPSHOT(rank = 1),
        NOT_BUS_FIRST_LEG(rank = 2),
        SKIPPED_TOO_EARLY(rank = 3),
        SKIPPED_DEPARTURE_PASSED(rank = 4),
        SKIPPED_NO_SCHEDULED_DEPARTURE(rank = 5),
        UNAVAILABLE(rank = 6),
        FAILED(rank = 7),
    }

    private companion object {
        const val TRAFFIC_TYPE_SUBWAY = 1
        const val TRAFFIC_TYPE_BUS = 2
        const val TRAFFIC_TYPE_WALK = 3
        const val PLANNED_WAIT_BASELINE_MINUTES = 5
        const val DEFAULT_SNAPSHOT_TTL_MILLIS = 20 * 60 * 1000L
        const val MAX_CANDIDATE_PATH_COUNT = 5
        const val REALTIME_ARRIVAL_LOOKAHEAD_MINUTES = 30
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
