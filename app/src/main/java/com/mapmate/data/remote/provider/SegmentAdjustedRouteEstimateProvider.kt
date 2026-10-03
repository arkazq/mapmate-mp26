package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.SegmentTimeAdjustment
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.SegmentTimeAdjustmentRepository
import com.mapmate.domain.util.runCatchingCancellable
import com.mapmate.domain.calculator.SegmentTimeAdjustmentCalculator
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
        scheduledDepartureEpochMillis: Long?,
        targetArrivalEpochMillis: Long?,
    ): RouteEstimate {
        val estimate = delegate.getRouteEstimate(
            origin = origin,
            destination = destination,
            transportMode = transportMode,
            routineId = routineId,
            scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
            targetArrivalEpochMillis = targetArrivalEpochMillis,
        )
        if (routineId == null || estimate.segments.isEmpty()) return estimate

        val adjustments = runCatchingCancellable {
            segmentTimeAdjustmentRepository.getAdjustmentsForRoutine(routineId)
        }.getOrElse { return estimate }
        if (adjustments.isEmpty()) return estimate

        val adjustedDelayMinutes = estimate.segments.sumOf { segment ->
            val adjustment = adjustments.firstOrNull { it.matches(segment.copy(routineId = routineId)) }
            (adjustment?.effectiveDelayMinutes() ?: 0).coerceAtLeast(-segment.plannedDurationMinutes.coerceAtLeast(0))
        }
        if (adjustedDelayMinutes == 0) return estimate

        return estimate.copy(
            estimatedMinutes = (estimate.estimatedMinutes.toLong() + adjustedDelayMinutes)
                .coerceIn(1, Int.MAX_VALUE.toLong()).toInt(),
            reason = listOf(
                estimate.reason,
                "구간별 이동 기록 보정 ${adjustedDelayMinutes}분을 반영했습니다.",
            ).joinToString(" "),
        )
    }

    private fun SegmentTimeAdjustment.effectiveDelayMinutes(): Int {
        if (!confidence.isFinite() || confidence !in MIN_APPLIED_CONFIDENCE..1.0 || sampleCount <= 0 ||
            averageDelayMinutes !in -SegmentTimeAdjustmentCalculator.MAX_USABLE_DELAY_MINUTES..SegmentTimeAdjustmentCalculator.MAX_USABLE_DELAY_MINUTES
        ) return 0
        return (averageDelayMinutes * confidence).roundToInt()
    }

    private companion object {
        const val MIN_APPLIED_CONFIDENCE = 0.34
    }
}
