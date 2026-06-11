package com.mapmate.domain.repository

import com.mapmate.domain.model.CommuteRecord
import kotlinx.coroutines.flow.Flow

interface CommuteRecordRepository {
    suspend fun saveRecord(record: CommuteRecord): Long

    fun observeRecords(): Flow<List<CommuteRecord>>
}
