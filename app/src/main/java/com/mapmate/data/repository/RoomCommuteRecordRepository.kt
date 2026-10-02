package com.mapmate.data.repository

import androidx.room.withTransaction
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.data.local.toDomain
import com.mapmate.data.local.toEntity
import com.mapmate.domain.calculator.SegmentTimeAdjustmentCalculator
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.hasChronologicalMeasuredSegments
import com.mapmate.domain.model.hasValidTiming
import com.mapmate.domain.repository.CommuteRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.Duration

class RoomCommuteRecordRepository(
    private val database: MapMateDatabase,
    private val segmentTimeAdjustmentCalculator: SegmentTimeAdjustmentCalculator = SegmentTimeAdjustmentCalculator(),
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) : CommuteRecordRepository {
    private val commuteRecordDao = database.commuteRecordDao()
    private val routeSegmentDao = database.routeSegmentDao()
    private val segmentTimeAdjustmentDao = database.segmentTimeAdjustmentDao()

    override suspend fun saveRecord(record: CommuteRecord): Long {
        return database.withTransaction { saveRecordWithSegments(record) }
    }

    private suspend fun saveRecordWithSegments(record: CommuteRecord): Long {
        if (record.id == null && record.routineId != null && record.targetArrivalAtEpochMillis != null) {
            commuteRecordDao.getRecordIdByArrivalEvent(record.routineId, record.targetArrivalAtEpochMillis)?.let { return it }
        }
        require(record.arrivedAtEpochMillis >= record.startedAtEpochMillis) { "Arrival precedes departure." }
        require(record.routeDurationMinutes >= 0) { "Invalid planned duration." }
        require(record.routeSegments.map { it.segmentIndex }.distinct().size == record.routeSegments.size) { "Duplicate segment indices." }
        require(record.routeSegments.all { it.routineId == null || it.routineId == record.routineId }) { "Segment belongs to another routine." }
        require(record.routeSegments.all { it.commuteRecordId == null || it.commuteRecordId == record.id }) { "Segment belongs to another record." }
        require(record.id != null || record.routeSegments.all { it.id == null }) { "New records cannot reuse saved segment IDs." }
        require(record.routeSegments.all { it.hasValidTiming() && it.status != RouteSegmentStatus.IN_PROGRESS }) { "Invalid segment timing." }
        require(record.routeSegments.hasChronologicalMeasuredSegments()) { "Measured segments overlap." }
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
        return commuteRecordDao.getRecentRecordsWithSegments(
            limit = limit,
            routineId = routineId,
        ).map { it.toDomain() }
    }

    override suspend fun getRecord(recordId: Long): CommuteRecord? {
        return commuteRecordDao.getRecordWithSegments(recordId)?.toDomain()
    }

    override suspend fun updateRouteSegment(segment: RouteSegment) {
        val recordId = segment.commuteRecordId ?: return
        updateRouteSegments(recordId, listOf(segment))
    }

    override suspend fun updateRouteSegments(
        recordId: Long,
        segments: List<RouteSegment>,
    ): CommuteRecord = database.withTransaction {
        val record = requireNotNull(getRecord(recordId)) { "Commute record does not exist." }
        val existingSegments = record.routeSegments.associateBy { it.id }
        require(segments.map { it.id }.distinct().size == segments.size) { "Duplicate segment IDs." }
        segments.forEach { segment ->
            val original = requireNotNull(existingSegments[segment.id]) { "Segment does not belong to record." }
            require(segment.id != null && segment.commuteRecordId == recordId) { "Invalid record ID." }
            require(segment.routineId == original.routineId) { "Invalid routine ID." }
            require(segment.copy(actualStartedAtEpochMillis = original.actualStartedAtEpochMillis,
                actualEndedAtEpochMillis = original.actualEndedAtEpochMillis,
                actualDurationMinutes = original.actualDurationMinutes, isUserEdited = original.isUserEdited,
                status = original.status) == original) { "Only timing can be edited." }
            require(segment.hasValidTiming() && segment.status in setOf(RouteSegmentStatus.COMPLETED, RouteSegmentStatus.SKIPPED)) { "Invalid edited segment." }
            val start = segment.actualStartedAtEpochMillis
            val end = segment.actualEndedAtEpochMillis
            if (segment.status == RouteSegmentStatus.COMPLETED) {
                require(start != null && end != null && end >= start) { "Invalid segment timing." }
                val expectedDuration = Duration.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end))
                    .toMinutes().toInt().coerceAtLeast(0)
                require(segment.actualDurationMinutes == expectedDuration) { "Duration does not match segment timing." }
            }
            val timingChanged = segment.actualStartedAtEpochMillis != original.actualStartedAtEpochMillis ||
                segment.actualEndedAtEpochMillis != original.actualEndedAtEpochMillis ||
                segment.actualDurationMinutes != original.actualDurationMinutes || segment.status != original.status
            updateSegmentTiming(segment.copy(isUserEdited = original.isUserEdited || timingChanged))
        }
        val updatedRecord = requireNotNull(getRecord(recordId))
        val orderedSegments = updatedRecord.routeSegments.sortedBy { it.segmentIndex }
        require(orderedSegments.hasChronologicalMeasuredSegments()) { "Measured segments overlap." }
        if (orderedSegments.isNotEmpty()) {
            val start = orderedSegments.first().takeIf { it.status == RouteSegmentStatus.COMPLETED }
                ?.actualStartedAtEpochMillis ?: record.startedAtEpochMillis
            val end = orderedSegments.last().takeIf { it.status == RouteSegmentStatus.COMPLETED }
                ?.actualEndedAtEpochMillis ?: record.arrivedAtEpochMillis
            require(end >= start) { "Invalid commute timing." }
            val target = record.targetArrivalAtEpochMillis
            val delta = target?.let { Duration.between(Instant.ofEpochMilli(it), Instant.ofEpochMilli(end)).toMinutes().toInt() }
                ?: record.arrivalDeltaMinutes
            commuteRecordDao.updateActualTiming(recordId, start, end, delta)
        }
        record.routineId?.let { refreshSegmentAdjustments(it) }
        requireNotNull(getRecord(recordId))
    }

    private suspend fun updateSegmentTiming(segment: RouteSegment) {
        val segmentId = segment.id ?: return
        routeSegmentDao.updateSegmentTiming(
            segmentId = segmentId,
            actualStartedAtEpochMillis = segment.actualStartedAtEpochMillis,
            actualEndedAtEpochMillis = segment.actualEndedAtEpochMillis,
            actualDurationMinutes = segment.actualDurationMinutes,
            isUserEdited = segment.isUserEdited,
            status = segment.status.name,
        )
    }

    override fun observeRecords(): Flow<List<CommuteRecord>> {
        return commuteRecordDao.observeRecordsWithSegments()
            .map { rows -> rows.map { it.toDomain() } }
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
