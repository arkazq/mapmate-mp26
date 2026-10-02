package com.mapmate.presentation.history

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestCommuteRecordRepository
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun retryRecoversStorageErrorWithoutPresentingItAsEmptyHistory() = runTest {
        var failRead = true
        val original = TestCommuteRecordRepository(listOf(record(1L, 2)))
        val repository = object : CommuteRecordRepository by original {
            override fun observeRecords() = flow {
                if (failRead) error("Storage unavailable")
                emitAll(original.observeRecords())
            }
        }
        val viewModel = RecordsViewModel(repository)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.errorMessage)

        failRead = false
        viewModel.retry()
        runCurrent()
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.stats.totalRecords)
    }

    @Test
    fun missingRoutineFilterResetsToAllRecordsWhenHistoryChanges() = runTest {
        val repository = TestCommuteRecordRepository(listOf(record(1L, 2), record(2L, 8)))
        val viewModel = RecordsViewModel(repository)
        runCurrent()
        viewModel.selectRoutineFilter(2L)
        assertEquals(8, viewModel.uiState.value.stats.averageArrivalDeltaMinutes)

        repository.records.value = listOf(record(1L, 2))
        runCurrent()
        assertNull(viewModel.uiState.value.selectedRoutineId)
        assertEquals(2, viewModel.uiState.value.stats.averageArrivalDeltaMinutes)

        viewModel.selectRoutineFilter(999L)
        assertNull(viewModel.uiState.value.selectedRoutineId)
    }

    private fun record(routineId: Long, delta: Int) = CommuteRecord(
        id = routineId,
        routineId = routineId,
        routineName = "Routine $routineId",
        originName = "Origin",
        destinationName = "Destination",
        transportMode = TransportMode.TRANSIT,
        targetArrivalTime = LocalTime.of(9, 0),
        recommendedDepartureTime = LocalTime.of(8, 20),
        routeDurationMinutes = 30,
        routeSummary = "Route",
        startedAtEpochMillis = 1_000L,
        arrivedAtEpochMillis = 1_801_000L,
        arrivalDeltaMinutes = delta,
    )
}
