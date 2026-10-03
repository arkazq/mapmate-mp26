package com.mapmate.presentation.common

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestCommuteRecordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StoredRecordViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun missingRecordStopsLoadingAndDoesNotShowDatabaseError() = runTest {
        val model = StoredRecordViewModel(TestCommuteRecordRepository(), 404L)
        runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertNull(model.uiState.value.record)
        assertNull(model.uiState.value.errorMessage)
    }

    @Test
    fun databaseFailureIsVisibleAndRetryClearsErrorAfterSuccessfulRead() = runTest {
        var shouldFail = true
        val repository = object : CommuteRecordRepository by TestCommuteRecordRepository() {
            override suspend fun getRecord(recordId: Long): CommuteRecord? {
                if (shouldFail) error("database unavailable")
                return null
            }
        }
        val model = StoredRecordViewModel(repository, 1L)
        runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertNotNull(model.uiState.value.errorMessage)
        shouldFail = false
        model.retry()
        runCurrent()
        assertFalse(model.uiState.value.isLoading)
        assertNull(model.uiState.value.errorMessage)
    }
}
