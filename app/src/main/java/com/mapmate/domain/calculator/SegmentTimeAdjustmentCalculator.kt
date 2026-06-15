package com.mapmate.domain.calculator

import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.SegmentAdjustmentKey
import com.mapmate.domain.model.SegmentTimeAdjustment
import kotlin.math.roundToInt

class SegmentTimeAdjustmentCalculator(
    private val recentSampleLimit: Int = RECENT_SAMPLE_LIMIT,
    private val maxUsableDelayMinutes: Int = MAX_USABLE_DELAY_MINUTES,
) {
    fun calculate(
        routineId: Long,
        completedSegments: List<RouteSegment>,
        updatedAtEpochMillis: Long,
    ): List<SegmentTimeAdjustment> {
        return completedSegments
            .filter { it.routineId == routineId }
            .filter { it.status == RouteSegmentStatus.COMPLETED }
            .filter { it.actualDurationMinutes != null }
            .filter { it.plannedDurationMinutes >= 0 }
            .mapNotNull { segment ->
                val actualDurationMinutes = segment.actualDurationMinutes ?: return@mapNotNull null
                val delayMinutes = actualDurationMinutes - segment.plannedDurationMinutes
                if (delayMinutes !in -maxUsableDelayMinutes..maxUsableDelayMinutes) return@mapNotNull null
                SegmentSample(
                    segment = segment,
                    key = segment.adjustmentKey(),
                    delayMinutes = delayMinutes,
                )
            }
            .groupBy { it.key }
            .mapNotNull { (key, samples) ->
                if (key.routineId == null) return@mapNotNull null
                val recentSamples = samples
                    .sortedByDescending { it.segment.actualEndedAtEpochMillis ?: 0L }
                    .take(recentSampleLimit)
                if (recentSamples.isEmpty()) return@mapNotNull null

                val weightedDelay = recentSamples.weightedAverageDelay()
                val actualDurations = recentSamples.map { checkNotNull(it.segment.actualDurationMinutes) }
                SegmentTimeAdjustment(
                    routineId = routineId,
                    segmentType = key.segmentType,
                    routeName = key.routeName,
                    startName = key.startName,
                    endName = key.endName,
                    averageDelayMinutes = weightedDelay.roundToInt(),
                    averageActualDurationMinutes = actualDurations.average().roundToInt(),
                    minActualDurationMinutes = actualDurations.minOrNull() ?: 0,
                    maxActualDurationMinutes = actualDurations.maxOrNull() ?: 0,
                    sampleCount = recentSamples.size,
                    confidence = recentSamples.confidence(),
                    updatedAtEpochMillis = updatedAtEpochMillis,
                )
            }
    }

    private fun List<SegmentSample>.weightedAverageDelay(): Double {
        var totalWeight = 0.0
        var totalDelay = 0.0
        forEachIndexed { index, sample ->
            val recencyWeight = (size - index).toDouble()
            val editWeight = if (sample.segment.isUserEdited) USER_EDITED_WEIGHT else 1.0
            val weight = recencyWeight * editWeight
            totalWeight += weight
            totalDelay += sample.delayMinutes * weight
        }
        return if (totalWeight == 0.0) 0.0 else totalDelay / totalWeight
    }

    private fun List<SegmentSample>.confidence(): Double {
        val sampleConfidence = (size.toDouble() / MIN_CONFIDENT_SAMPLE_COUNT).coerceAtMost(1.0)
        val editPenalty = if (any { it.segment.isUserEdited }) USER_EDITED_CONFIDENCE_MULTIPLIER else 1.0
        return sampleConfidence * editPenalty
    }

    private data class SegmentSample(
        val segment: RouteSegment,
        val key: SegmentAdjustmentKey,
        val delayMinutes: Int,
    )

    companion object {
        const val RECENT_SAMPLE_LIMIT = 5
        const val MIN_CONFIDENT_SAMPLE_COUNT = 3
        const val MAX_USABLE_DELAY_MINUTES = 60
        private const val USER_EDITED_WEIGHT = 0.5
        private const val USER_EDITED_CONFIDENCE_MULTIPLIER = 0.75
    }
}
