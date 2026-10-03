package com.mapmate.data.alarm

import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureAlarmScheduler
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import com.mapmate.domain.alarm.routineScheduleFingerprint
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.data.mock.MockPlaceSearchProvider
import com.mapmate.presentation.home.HomeViewModel
import com.mapmate.presentation.prediction.PredictionDetailViewModel
import com.mapmate.presentation.routine.RoutinesViewModel
import com.mapmate.presentation.routine.RoutineRegistrationViewModel
import com.mapmate.presentation.routine.RoutineRegistrationEvent
import com.mapmate.presentation.tracking.TrackingViewModel
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestRoutineRepository
import com.mapmate.testing.TestCommuteRecordRepository
import com.mapmate.testing.TestSettingsRepository
import com.mapmate.testing.TestDepartureScheduleStore
import com.mapmate.testing.TestTrackingSessionStore
import com.mapmate.testing.sampleRoutine
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DepartureRecommendationConsistencyTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test fun allScreensReceiveTheActualAlarmClampedAgainstPersistedPreviousSchedule() = runTest {
        val f = Fixture()
        val home = HomeViewModel(f.routines, f.records, f.routes, nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        val detail = PredictionDetailViewModel(f.routine, f.routes, f.records, nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        val list = RoutinesViewModel(f.routines, f.routes, f.records, f.sessions, nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        val tracking = TrackingViewModel(f.routine, f.routes, f.records, f.routines, f.settings, f.sessions,
            nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        val registration = RoutineRegistrationViewModel(f.routines, f.settings,
            placeSearchProvider = MockPlaceSearchProvider(), routeEstimateProvider = f.routes,
            nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        registration.initialize(f.routine)
        home.setActive(true)
        detail.setActive(true)
        list.setActive(true)
        runCurrent()
        registration.onEvent(RoutineRegistrationEvent.CalculateClicked)
        runCurrent()
        val actualAlarm = requireNotNull(f.alarms.scheduled)
        assertEquals(LocalTime.of(8, 15), actualAlarm.recommendedDepartureTime)
        val times = listOf(home.uiState.value.dashboardRecommendation?.recommendedDepartureAtEpochMillis,
            detail.uiState.value.recommendation?.recommendedDepartureAtEpochMillis,
            list.uiState.value.recommendations.single().recommendedDepartureAtEpochMillis,
            tracking.uiState.value.recommendation?.recommendedDepartureAtEpochMillis)
        times.forEach { assertEquals(actualAlarm.triggerAtEpochMillis, it) }
        assertEquals("08:15", registration.uiState.value.recommendedDepartureTimeText)
        assertEquals(2, f.routes.calls)
        assertEquals("Live path", tracking.uiState.value.recommendation?.routeSummary)
        home.setActive(false)
        detail.setActive(false)
        list.setActive(false)
    }

    @Test fun recheckBypassesSharedPlanAndVisibleScreensUpdateFromTheCommittedRevision() = runTest {
        val f = Fixture()
        val home = HomeViewModel(f.routines, f.records, f.routes, nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        val detail = PredictionDetailViewModel(f.routine, f.routes, f.records, nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        val tracking = TrackingViewModel(f.routine, f.routes, f.records, f.routines, f.settings, f.sessions,
            nowProvider = { f.now }, scheduledRouteProvider = f.coordinator)
        home.setActive(true)
        detail.setActive(true)
        runCurrent()
        val before = requireNotNull(f.alarms.scheduled)
        val calls = f.routes.calls
        assertTrue(f.coordinator.recheckScheduledAlarm(before))
        runCurrent()
        val after = requireNotNull(f.alarms.scheduled)
        assertTrue(f.routes.calls > calls)
        assertEquals(LocalTime.of(8, 25), after.recommendedDepartureTime)
        assertEquals(after.triggerAtEpochMillis, home.uiState.value.dashboardRecommendation?.recommendedDepartureAtEpochMillis)
        assertEquals(after.triggerAtEpochMillis, detail.uiState.value.recommendation?.recommendedDepartureAtEpochMillis)
        assertEquals(after.triggerAtEpochMillis, tracking.uiState.value.recommendation?.recommendedDepartureAtEpochMillis)
        home.setActive(false)
        detail.setActive(false)
    }

    @Test fun UnsavedOrEditedDraftDoesNotReplaceTheExistingAlarm() = runTest {
        val f = Fixture()
        f.coordinator.rescheduleNextAlarm()
        val original = f.alarms.scheduled
        f.coordinator.resolve(f.routine.copy(name = "Unsaved edit", destination = Destination("New", "Address", 37.4, 127.2)), f.now)
        assertEquals(original, f.alarms.scheduled)
        f.coordinator.resolve(f.routine.copy(id = null), f.now)
        assertEquals(original, f.alarms.scheduled)
    }

    private class Fixture {
        val now = ZonedDateTime.of(2026, 10, 2, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val routine = sampleRoutine()
        val routines = TestRoutineRepository(listOf(routine))
        val records = TestCommuteRecordRepository()
        val settings = TestSettingsRepository().apply { settings.value = settings.value.copy(notificationsEnabled = true) }
        val sessions = TestTrackingSessionStore()
        val store = TestDepartureScheduleStore().apply {
            state = state.copy(schedule = DepartureAlarmSchedule(1, routine.name, routine.destination.name,
                routine.targetArrivalTime, LocalTime.of(8, 5), 30, now.plusMinutes(5).toInstant().toEpochMilli(),
                now.plusHours(1).toInstant().toEpochMilli()), routineFingerprint = routineScheduleFingerprint(routine))
        }
        val routes = Routes()
        val alarms = Alarms()
        val coordinator = DepartureAlarmCoordinator(settings, routines, records, store, routes, alarms,
            object : DepartureRecheckScheduler {
                override fun schedule(schedule: DepartureAlarmSchedule, replaceExisting: Boolean) = Unit
                override fun cancel() = Unit
            }, nowProvider = { now })
    }

    private class Routes : RouteEstimateProvider {
        var calls = 0
        override suspend fun getRouteEstimate(origin: Destination, destination: Destination, transportMode: TransportMode,
            routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?): RouteEstimate {
            calls++
            return if (scheduledDepartureEpochMillis == null) RouteEstimate(30, "Base path", "Test", "")
            else RouteEstimate(10, "Live path", "Test", "", hasRealtimeAdjustment = true)
        }
    }

    private class Alarms : DepartureAlarmScheduler {
        var scheduled: DepartureAlarmSchedule? = null
        override fun canPostDepartureNotifications() = true
        override fun schedule(schedule: DepartureAlarmSchedule) { scheduled = schedule }
        override fun cancel() { scheduled = null }
    }
}
