package com.mapmate.presentation.routine

import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.presentation.common.toRecommendationUiModel
import com.mapmate.presentation.tracking.toCommuteRecord
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestCommuteRecordRepository
import com.mapmate.testing.TestRoutineRepository
import com.mapmate.testing.TestTrackingSessionStore
import com.mapmate.testing.sampleRoutine
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val now = ZonedDateTime.of(2026, 10, 2, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))

    @Test
    fun hiddenListDoesNotQueryRoutesAndReturningReloadsCurrentRecommendations() = runTest {
        val provider = CountingProvider()
        val routines = TestRoutineRepository(listOf(sampleRoutine()))
        val model = RoutinesViewModel(routines, provider, TestCommuteRecordRepository(), TestTrackingSessionStore(), nowProvider = { now })
        runCurrent()
        assertEquals(0, provider.calls)
        model.setActive(true)
        runCurrent()
        assertEquals(1, provider.calls)
        model.setActive(false)
        runCurrent()
        routines.routines.value = listOf(sampleRoutine().copy(name = "Changed"))
        runCurrent()
        assertEquals(1, provider.calls)
        model.setActive(true)
        runCurrent()
        assertEquals(2, provider.calls)
        assertEquals("Changed", model.uiState.value.recommendations.single().routine.name)
        model.setActive(false)
        runCurrent()
    }

    @Test
    fun listExcludesCompletedArrivalEventAndSkipsNetworkForInactiveRoutine() = runTest {
        val routine = sampleRoutine()
        val completed = routine.toRecommendationUiModel(RouteEstimate(20, "", "Test", ""),
            targetArrivalAtEpochMillis = now.withHour(9).toInstant().toEpochMilli())
            .toCommuteRecord(now.toInstant().toEpochMilli(), now.plusSeconds(1).toInstant().toEpochMilli(), emptyList())
        val provider = CountingProvider()
        val model = RoutinesViewModel(TestRoutineRepository(listOf(routine, sampleRoutine(2).copy(repeatDays = emptySet()))),
            provider, TestCommuteRecordRepository(listOf(completed)), TestTrackingSessionStore(), nowProvider = { now })
        model.setActive(true)
        runCurrent()
        assertEquals(1, provider.calls)
        val next = model.uiState.value.recommendations.first { it.routine.id == 1L }
        assertTrue(requireNotNull(next.targetArrivalAtEpochMillis) > now.plusHours(24).toInstant().toEpochMilli())
        model.setActive(false)
        runCurrent()
    }

    private class CountingProvider : RouteEstimateProvider {
        var calls = 0
        override suspend fun getRouteEstimate(origin: Destination, destination: Destination, transportMode: TransportMode,
            routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?): RouteEstimate {
            calls++
            return RouteEstimate(20, "", "Test", "")
        }
    }
}
