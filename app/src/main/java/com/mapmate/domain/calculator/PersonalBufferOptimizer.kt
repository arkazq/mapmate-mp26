package com.mapmate.domain.calculator

import com.mapmate.domain.model.AppSettings
import kotlin.math.roundToInt

class PersonalBufferOptimizer(
    private val recentRecordLimit: Int = RECENT_RECORD_LIMIT,
    private val historyWeight: Double = HISTORY_WEIGHT,
    private val maxSingleAdjustmentMinutes: Int = AppSettings.MAX_AUTO_BUFFER_ADJUSTMENT_MINUTES,
) {
    fun optimize(
        currentPersonalBufferMinutes: Int,
        recentArrivalDeltaMinutes: List<Int>,
    ): Int {
        val currentMinutes = currentPersonalBufferMinutes.coerceIn(
            minimumValue = AppSettings.MIN_BUFFER_MINUTES,
            maximumValue = AppSettings.MAX_BUFFER_MINUTES,
        )
        val recentDeltas = recentArrivalDeltaMinutes.take(recentRecordLimit)
        if (recentDeltas.isEmpty()) return currentMinutes

        val recentAverageDeltaMinutes = recentDeltas.average()
        val adjustmentMinutes = (recentAverageDeltaMinutes * historyWeight)
            .roundToInt()
            .coerceIn(
                minimumValue = -maxSingleAdjustmentMinutes,
                maximumValue = maxSingleAdjustmentMinutes,
            )

        return (currentMinutes + adjustmentMinutes).coerceIn(
            minimumValue = AppSettings.MIN_BUFFER_MINUTES,
            maximumValue = AppSettings.MAX_BUFFER_MINUTES,
        )
    }

    companion object {
        const val RECENT_RECORD_LIMIT = 3
        private const val HISTORY_WEIGHT = 0.3
    }
}
