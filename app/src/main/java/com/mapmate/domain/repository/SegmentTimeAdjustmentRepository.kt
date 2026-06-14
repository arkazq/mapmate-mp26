package com.mapmate.domain.repository

import com.mapmate.domain.model.SegmentTimeAdjustment

interface SegmentTimeAdjustmentRepository {
    suspend fun replaceAdjustmentsForRoutine(
        routineId: Long,
        adjustments: List<SegmentTimeAdjustment>,
    )

    suspend fun getAdjustmentsForRoutine(routineId: Long): List<SegmentTimeAdjustment>
}
