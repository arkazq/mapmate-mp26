package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.SegmentTimeAdjustment
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.SegmentTimeAdjustmentRepository
import kotlin.math.roundToInt

class SegmentAdjustedRouteEstimateProvider(
    private val delegate: RouteEstimateProvider,
    private val segmentTimeAdjustmentRepository: SegmentTimeAdjustmentRepository,
) : RouteEstimateProvider {
    override suspend fun getRouteEstimate(
        origin: Destination,
        destination: Destination,
        transportMode: TransportMode,
        routineId: Long?,
    ): RouteEstimate {
        val estimate = delegate.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = transportMode,
            routineId = routineId,
        )
        if (routineId == null || estimate.segments.isEmpty()) return estimate

        val adjustments = segmentTimeAdjustmentRepository.getAdjustmentsForRoutine(routineId)
        if (adjustments.isEmpty()) return estimate

        val adjustedDelayMinutes = estimate.segments.sumOf { segment ->
            val adjustment = adjustments.firstOrNull { it.matches(segment) }
            adjustment?.effectiveDelayMinutes() ?: 0
        }
        if (adjustedDelayMinutes == 0) return estimate

        return estimate.copy(
            estimatedMinutes = (estimate.estimatedMinutes + adjustedDelayMinutes).coerceAtLeast(1),
            reason = listOf(
                estimate.reason,
                "Segment history adjustment ${adjustedDelayMinutes} min applied.",
            ).joinToString(" "),
        )
    }

    private fun SegmentTimeAdjustment.effectiveDelayMinutes(): Int {
        if (confidence < MIN_APPLIED_CONFIDENCE) return 0
        return (averageDelayMinutes * confidence).roundToInt()
    }

    private companion object {
        const val MIN_APPLIED_CONFIDENCE = 0.34
    }
}
