package com.mapmate.data.repository

import com.mapmate.data.local.CommuteRecordDao
import com.mapmate.data.local.RouteSegmentDao
import com.mapmate.data.local.SegmentTimeAdjustmentDao
import com.mapmate.data.local.toDomain
import com.mapmate.data.local.toEntity
import com.mapmate.domain.calculator.SegmentTimeAdjustmentCalculator
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.repository.CommuteRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCommuteRecordRepository(
    private val commuteRecordDao: CommuteRecordDao,
    private val routeSegmentDao: RouteSegmentDao,
    private val segmentTimeAdjustmentDao: SegmentTimeAdjustmentDao,
    private val segmentTimeAdjustmentCalculator: SegmentTimeAdjustmentCalculator = SegmentTimeAdjustmentCalculator(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : CommuteRecordRepository {
    override suspend fun saveRecord(record: CommuteRecord): Long {
        val recordId = commuteRecordDao.insertRecord(record.toEntity())
        val segmentEntities = record.routeSegments.map {
            it.copy(
                commuteRecordId = recordId,
                routineId = it.routineId ?: record.routineId,
            ).toEntity(commuteRecordId = recordId)
        }
        if (segmentEntities.isNotEmpty()) {
            routeSegmentDao.insertSegments(segmentEntities)
            val routineId = record.routineId
            if (routineId != null) {
                refreshSegmentAdjustments(routineId)
            }
        }
        return recordId
    }

    override suspend fun getRecentRecords(
        limit: Int,
        routineId: Long?,
    ): List<CommuteRecord> {
        return commuteRecordDao.getRecentRecords(
            limit = limit,
            routineId = routineId,
        ).map { it.toDomain() }
    }

    override suspend fun getRecord(recordId: Long): CommuteRecord? {
        return commuteRecordDao.getRecordById(recordId)?.toDomainWithSegments()
    }

    override suspend fun updateRouteSegment(segment: RouteSegment) {
        val segmentId = segment.id ?: return
        routeSegmentDao.updateSegmentTiming(
            segmentId = segmentId,
            actualStartedAtEpochMillis = segment.actualStartedAtEpochMillis,
            actualEndedAtEpochMillis = segment.actualEndedAtEpochMillis,
            actualDurationMinutes = segment.actualDurationMinutes,
            isUserEdited = segment.isUserEdited,
            status = segment.status.name,
        )
        segment.routineId?.let { refreshSegmentAdjustments(it) }
    }

    override fun observeRecords(): Flow<List<CommuteRecord>> {
        return commuteRecordDao.observeRecords()
            .map { entities -> entities.map { it.toDomainWithSegments() } }
    }

    private suspend fun com.mapmate.data.local.CommuteRecordEntity.toDomainWithSegments(): CommuteRecord {
        val record = toDomain()
        val recordId = record.id ?: return record
        return record.copy(
            routeSegments = routeSegmentDao.getSegmentsByCommuteRecordId(recordId)
                .map { it.toDomain() },
        )
    }

    private suspend fun refreshSegmentAdjustments(routineId: Long) {
        val recentSegments = routeSegmentDao.getRecentCompletedSegments(
            routineId = routineId,
            limit = RECENT_SEGMENT_LOOKBACK_LIMIT,
        ).map { it.toDomain() }
        val adjustments = segmentTimeAdjustmentCalculator.calculate(
            routineId = routineId,
            completedSegments = recentSegments,
            updatedAtEpochMillis = nowEpochMillis(),
        )
        segmentTimeAdjustmentDao.deleteByRoutineId(routineId)
        if (adjustments.isNotEmpty()) {
            segmentTimeAdjustmentDao.insertAdjustments(adjustments.map { it.toEntity() })
        }
    }

    private companion object {
        const val RECENT_SEGMENT_LOOKBACK_LIMIT = 100
    }
}
