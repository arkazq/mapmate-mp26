package com.mapmate.presentation.tracking

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.presentation.common.toRecommendationUiModel
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestCommuteRecordRepository
import com.mapmate.testing.TestRoutineRepository
import com.mapmate.testing.TestSettingsRepository
import com.mapmate.testing.TestTrackingSessionStore
import com.mapmate.testing.sampleRoutine
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackingViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()
    private val routine = sampleRoutine()
    private val now = ZonedDateTime.of(2026, 10, 2, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))
    private val provider = object : RouteEstimateProvider {
        override suspend fun getRouteEstimate(origin: Destination, destination: Destination,
            transportMode: TransportMode, routineId: Long?, scheduledDepartureEpochMillis: Long?,
            targetArrivalEpochMillis: Long?) = RouteEstimate(20, "test", "Test", "test")
    }

    @Test
    fun repeatedArrivalClicksBeforeCoroutineStartsSaveOnlyOneRecord() = runTest {
        val records = TestCommuteRecordRepository()
        val model = TrackingViewModel(routine, provider, records, TestRoutineRepository(listOf(routine)),
            TestSettingsRepository(), TestTrackingSessionStore(), nowProvider = { now })
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(TrackingStage.Boarded, model.uiState.value.stage)
        model.onPrimaryActionClick()
        assertTrue(model.uiState.value.isSavingRecord)
        model.onPrimaryActionClick()
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(1, records.savedCount)
        assertNotNull(model.uiState.value.completedRecord)
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(1, records.savedCount)
    }

    @Test
    fun failedReadAfterSuccessfulSaveStillCompletesAndDoesNotSaveAgain() = runTest {
        val original = TestCommuteRecordRepository()
        val repository = object : CommuteRecordRepository by original {
            override suspend fun getRecord(recordId: Long): CommuteRecord? = error("read failed after save")
        }
        val model = TrackingViewModel(routine, provider, repository, TestRoutineRepository(listOf(routine)),
            TestSettingsRepository(), TestTrackingSessionStore(), nowProvider = { now })
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(1, original.savedCount)
        assertEquals(1L, model.uiState.value.completedRecord!!.id)
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(1, original.savedCount)
    }

    @Test
    fun completionKeepsConcurrentRenameAndDoesNotChangeGlobalDefaults() = runTest {
        val routines = TestRoutineRepository(listOf(routine))
        val settings = TestSettingsRepository()
        val initialSettings = settings.settings.value
        var clock = now
        val model = TrackingViewModel(routine, provider, TestCommuteRecordRepository(), routines,
            settings, TestTrackingSessionStore(), nowProvider = { clock })
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        routines.routines.value = listOf(routine.copy(name = "Renamed commute"))
        clock = now.withHour(9).withMinute(10)
        model.onPrimaryActionClick()
        runCurrent()

        assertEquals("Renamed commute", routines.routines.value.single().name)
        assertTrue(routines.routines.value.single().personalBufferMinutes > routine.personalBufferMinutes)
        assertEquals(initialSettings, settings.settings.value)
    }

    @Test
    fun oneSecondDemoCompletionIsSavedButDoesNotChangePersonalBuffer() = runTest {
        val routines = TestRoutineRepository(listOf(routine))
        var clock = now
        val model = TrackingViewModel(routine, provider, TestCommuteRecordRepository(), routines,
            TestSettingsRepository(), TestTrackingSessionStore(), nowProvider = { clock })
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        clock = now.plusSeconds(1)
        model.onPrimaryActionClick()
        runCurrent()
        assertNotNull(model.uiState.value.completedRecord)
        assertEquals(routine.personalBufferMinutes, routines.routines.value.single().personalBufferMinutes)
    }

    @Test
    fun completionDoesNotRecreateDeletedRoutine() = runTest {
        val routines = TestRoutineRepository(listOf(routine))
        var clock = now
        val model = TrackingViewModel(routine, provider, TestCommuteRecordRepository(), routines,
            TestSettingsRepository(), TestTrackingSessionStore(), nowProvider = { clock })
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        routines.deleteRoutine(requireNotNull(routine.id))
        clock = now.withHour(9).withMinute(10)
        model.onPrimaryActionClick()
        runCurrent()
        assertTrue(routines.routines.value.isEmpty())
        assertNotNull(model.uiState.value.completedRecord)
    }

    @Test
    fun completionDoesNotApplyOldRouteHistoryToEditedDestination() = runTest {
        val routines = TestRoutineRepository(listOf(routine))
        var clock = now
        val model = TrackingViewModel(routine, provider, TestCommuteRecordRepository(), routines,
            TestSettingsRepository(), TestTrackingSessionStore(), nowProvider = { clock })
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        val changed = routine.copy(destination = Destination("New office", "New address", 38.0, 128.0))
        routines.routines.value = listOf(changed)
        clock = now.withHour(9).withMinute(10)
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(changed, routines.routines.value.single())
        assertNotNull(model.uiState.value.completedRecord)
    }

    @Test
    fun arrivalDeltaUsesExactTargetDateEvenWhenMoreThanTwelveHoursLate() {
        val target = now.withHour(9)
        val recommendation = routine.toRecommendationUiModel(
            RouteEstimate(20, "test", "Test", "test"), now = now.toLocalTime(),
            targetArrivalAtEpochMillis = target.toInstant().toEpochMilli(),
        )
        val record = recommendation.toCommuteRecord(
            startedAtEpochMillis = now.toInstant().toEpochMilli(),
            arrivedAtEpochMillis = target.plusHours(14).toInstant().toEpochMilli(),
            routeSegments = emptyList(),
        )
        assertEquals(14 * 60, record.arrivalDeltaMinutes)
    }

    @Test
    fun measuredSegmentsAndFrozenDepartureSurviveNewViewModelWithoutRouteRefetch() = runTest {
        val sessions = TestTrackingSessionStore()
        val records = TestCommuteRecordRepository()
        val routines = TestRoutineRepository(listOf(routine))
        val settings = TestSettingsRepository()
        var calls = 0
        val segmentedProvider = object : RouteEstimateProvider by provider {
            override suspend fun getRouteEstimate(origin: Destination, destination: Destination, transportMode: TransportMode,
                routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?): RouteEstimate {
                calls++
                return RouteEstimate(20, "frozen route", "Test", "", segments = listOf(
                    RouteSegment(segmentIndex = 0, segmentType = RouteSegmentType.WALK_TO_TRANSIT, plannedDurationMinutes = 3),
                    RouteSegment(segmentIndex = 1, segmentType = RouteSegmentType.BUS_RIDE, plannedDurationMinutes = 17),
                ))
            }
        }
        var clock = now
        val first = TrackingViewModel(routine, segmentedProvider, records, routines, settings, sessions, nowProvider = { clock })
        runCurrent()
        val departure = first.uiState.value.recommendation!!.recommendedDepartureAtEpochMillis
        first.onSegmentStart(0)
        runCurrent()
        clock = now.plusSeconds(45)
        first.onSegmentComplete(0)
        runCurrent()
        first.onSegmentStart(1)
        runCurrent()
        val callsBeforeRestore = calls
        clock = now.plusMinutes(5)
        val restored = TrackingViewModel(routine, segmentedProvider, records, routines, settings, sessions, nowProvider = { clock })
        runCurrent()
        assertEquals(callsBeforeRestore, calls)
        assertTrue(restored.uiState.value.isRestoredSession)
        assertEquals(departure, restored.uiState.value.recommendation!!.recommendedDepartureAtEpochMillis)
        assertEquals(RouteSegmentStatus.COMPLETED, restored.uiState.value.routeSegments[0].status)
        assertEquals(RouteSegmentStatus.IN_PROGRESS, restored.uiState.value.currentSegment!!.status)
        assertEquals(now.plusSeconds(45).toInstant().toEpochMilli(), restored.uiState.value.currentSegment!!.actualStartedAtEpochMillis)
        restored.onPrimaryActionClick()
        runCurrent()
        assertEquals(1, records.savedCount)
        assertTrue(sessions.sessions.isEmpty())
    }

    @Test
    fun failedSessionWriteDoesNotStartMeasurementOrEnableCompletion() = runTest {
        val sessions = TestTrackingSessionStore().apply { failWrites = true }
        val model = TrackingViewModel(routine, provider, TestCommuteRecordRepository(),
            TestRoutineRepository(listOf(routine)), TestSettingsRepository(), sessions, nowProvider = { now })
        runCurrent()
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(TrackingStage.Planned, model.uiState.value.stage)
        assertNotNull(model.uiState.value.errorMessage)
        assertTrue(sessions.sessions.isEmpty())
    }

    @Test
    fun failedHistoryReadBlocksMeasurementInsteadOfAssumingNoCompletedEvents() = runTest {
        val repository = object : CommuteRecordRepository by TestCommuteRecordRepository() {
            override suspend fun getRecentRecords(limit: Int, routineId: Long?): List<CommuteRecord> = error("DB unavailable")
        }
        val sessions = TestTrackingSessionStore()
        val model = TrackingViewModel(routine, provider, repository, TestRoutineRepository(listOf(routine)),
            TestSettingsRepository(), sessions, nowProvider = { now })
        runCurrent()
        assertTrue(model.uiState.value.isLoadError)
        model.onPrimaryActionClick()
        runCurrent()
        assertTrue(sessions.sessions.isEmpty())
    }

    @Test
    fun completedTodayCannotBeRestartedAsTomorrowsCommute() = runTest {
        val completed = routine.toRecommendationUiModel(RouteEstimate(20, "test", "Test", ""),
            targetArrivalAtEpochMillis = now.withHour(9).toInstant().toEpochMilli()).toCommuteRecord(
                now.toInstant().toEpochMilli(), now.plusSeconds(1).toInstant().toEpochMilli(), emptyList(),
            )
        val records = TestCommuteRecordRepository(listOf(completed))
        val model = TrackingViewModel(routine, provider, records, TestRoutineRepository(listOf(routine)),
            TestSettingsRepository(), TestTrackingSessionStore(), nowProvider = { now })
        runCurrent()
        assertTrue(!model.uiState.value.canRecord)
        model.onPrimaryActionClick()
        model.onPrimaryActionClick()
        runCurrent()
        assertEquals(0, records.savedCount)
    }

    @Test
    fun savedSessionAfterSuccessfulCompletionIsNotRecordedTwice() = runTest {
        val sessions = TestTrackingSessionStore()
        val records = TestCommuteRecordRepository()
        val routines = TestRoutineRepository(listOf(routine))
        val first = TrackingViewModel(routine, provider, records, routines, TestSettingsRepository(), sessions, nowProvider = { now })
        runCurrent()
        first.onPrimaryActionClick()
        runCurrent()
        val snapshot = sessions.sessions.getValue(requireNotNull(routine.id))
        first.onPrimaryActionClick()
        runCurrent()
        sessions.sessions[requireNotNull(routine.id)] = snapshot
        val second = TrackingViewModel(routine, provider, records, routines, TestSettingsRepository(), sessions, nowProvider = { now })
        runCurrent()
        assertNotNull(second.uiState.value.completedRecord)
        second.onPrimaryActionClick()
        runCurrent()
        assertEquals(1, records.savedCount)
    }
}
