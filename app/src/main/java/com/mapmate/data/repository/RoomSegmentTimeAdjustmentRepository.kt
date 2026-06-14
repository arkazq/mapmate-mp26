package com.mapmate.data.repository

import com.mapmate.data.local.SegmentTimeAdjustmentDao
import com.mapmate.data.local.toDomain
import com.mapmate.data.local.toEntity
import com.mapmate.domain.model.SegmentTimeAdjustment
import com.mapmate.domain.repository.SegmentTimeAdjustmentRepository

class RoomSegmentTimeAdjustmentRepository(
    private val segmentTimeAdjustmentDao: SegmentTimeAdjustmentDao,
) : SegmentTimeAdjustmentRepository {
    override suspend fun replaceAdjustmentsForRoutine(
        routineId: Long,
        adjustments: List<SegmentTimeAdjustment>,
    ) {
        segmentTimeAdjustmentDao.deleteByRoutineId(routineId)
        if (adjustments.isNotEmpty()) {
            segmentTimeAdjustmentDao.insertAdjustments(adjustments.map { it.toEntity() })
        }
    }

    override suspend fun getAdjustmentsForRoutine(routineId: Long): List<SegmentTimeAdjustment> {
        return segmentTimeAdjustmentDao.getAdjustmentsForRoutine(routineId)
            .map { it.toDomain() }
    }
}
