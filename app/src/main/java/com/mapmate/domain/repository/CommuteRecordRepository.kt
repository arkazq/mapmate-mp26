package com.mapmate.domain.repository

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import kotlinx.coroutines.flow.Flow

interface CommuteRecordRepository {
    suspend fun saveRecord(record: CommuteRecord): Long

    suspend fun getRecentRecords(
        limit: Int,
        routineId: Long? = null,
    ): List<CommuteRecord>

    suspend fun getRecord(recordId: Long): CommuteRecord?

    suspend fun updateRouteSegment(segment: RouteSegment)

    fun observeRecords(): Flow<List<CommuteRecord>>
}
