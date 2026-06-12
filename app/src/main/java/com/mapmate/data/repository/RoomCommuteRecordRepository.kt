package com.mapmate.data.repository

import com.mapmate.data.local.CommuteRecordDao
import com.mapmate.data.local.toDomain
import com.mapmate.data.local.toEntity
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.repository.CommuteRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCommuteRecordRepository(
    private val commuteRecordDao: CommuteRecordDao,
) : CommuteRecordRepository {
    override suspend fun saveRecord(record: CommuteRecord): Long {
        return commuteRecordDao.insertRecord(record.toEntity())
    }

    override fun observeRecords(): Flow<List<CommuteRecord>> {
        return commuteRecordDao.observeRecords()
            .map { entities -> entities.map { it.toDomain() } }
    }
}
