package com.mapmate.presentation.home

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestCommuteRecordRepository
import com.mapmate.testing.TestRoutineRepository
import com.mapmate.testing.TestTrackingSessionStore
import com.mapmate.domain.repository.TrackingSessionStore
import com.mapmate.testing.sampleRoutine
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import com.mapmate.domain.repository.RoutineRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private val now = ZonedDateTime.of(2026, 10, 2, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))

    @Test
    fun routesAreRefreshedOnlyWhileHomeIsVisibleAndRefreshOnReturn() = runTest {
        val provider = CountingProvider()
        val viewModel = HomeViewModel(TestRoutineRepository(listOf(sampleRoutine())),
            TestCommuteRecordRepository(), provider, nowProvider = { now })
        runCurrent()
        assertEquals(0, provider.calls)
        viewModel.setActive(true)
        runCurrent()
        assertEquals(1, provider.calls)
        assertFalse(viewModel.uiState.value.isLoading)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, provider.calls)

        viewModel.setActive(false)
        runCurrent()
        advanceTimeBy(180_000)
        runCurrent()
        assertEquals(2, provider.calls)
        viewModel.setActive(true)
        runCurrent()
        assertEquals(3, provider.calls)
        viewModel.setActive(false)
        runCurrent()
    }

    @Test
    fun manualRefreshRecomputesCurrentRecommendationWithoutWaitingForMinuteTick() = runTest {
        val provider = CountingProvider()
        val viewModel = HomeViewModel(TestRoutineRepository(listOf(sampleRoutine())),
            TestCommuteRecordRepository(), provider, nowProvider = { now })
        viewModel.setActive(true)
        runCurrent()
        viewModel.refresh()
        runCurrent()
        assertEquals(2, provider.calls)
        assertFalse(viewModel.uiState.value.isRefreshing)
        viewModel.setActive(false)
        runCurrent()
    }

    @Test
    fun refreshResubscribesAfterStorageReadFailure() = runTest {
        var failed = false
        val original = TestRoutineRepository(listOf(sampleRoutine()))
        val repository = object : RoutineRepository by original {
            override fun observeRoutines() = flow {
                if (!failed) { failed = true; error("Storage failed") }
                emit(listOf(sampleRoutine()))
            }
        }
        val viewModel = HomeViewModel(repository, TestCommuteRecordRepository(), CountingProvider(), nowProvider = { now })
        viewModel.setActive(true)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("저장된 루틴을 불러오지 못했습니다.", viewModel.uiState.value.errorMessage)
        viewModel.refresh()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.savedRoutines.size)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        viewModel.setActive(false)
        runCurrent()
    }

    @Test
    fun damagedPendingSessionDoesNotHideValidRoutinesAndRefreshRetriesSessionRead() = runTest {
        var failSessionRead = true
        val original = TestTrackingSessionStore()
        val store = object : TrackingSessionStore by original {
            override fun observeSessions() = flow {
                if (failSessionRead) error("Damaged pending session")
                emitAll(original.observeSessions())
            }
        }
        val viewModel = HomeViewModel(TestRoutineRepository(listOf(sampleRoutine())),
            TestCommuteRecordRepository(), CountingProvider(), nowProvider = { now }, trackingSessionStore = store)
        viewModel.setActive(true)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.savedRoutines.size)
        assertNotNull(viewModel.uiState.value.dashboardRecommendation)
        assertTrue(viewModel.uiState.value.errorMessage!!.startsWith("진행 중인 측정 내역"))
        assertFalse(viewModel.uiState.value.isLoading)

        failSessionRead = false
        viewModel.refresh()
        runCurrent()
        assertEquals(1, viewModel.uiState.value.savedRoutines.size)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        viewModel.setActive(false)
        runCurrent()
    }

    private class CountingProvider : RouteEstimateProvider {
        var calls = 0
        override suspend fun getRouteEstimate(origin: Destination, destination: Destination,
            transportMode: TransportMode, routineId: Long?, scheduledDepartureEpochMillis: Long?,
            targetArrivalEpochMillis: Long?): RouteEstimate {
            calls++
            return RouteEstimate(20, "test", "Test", "test")
        }
    }
}
