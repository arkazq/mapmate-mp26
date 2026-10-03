package com.mapmate.data.alarm

import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureAlarmScheduler
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import com.mapmate.domain.alarm.eventOrNull
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.testing.TestCommuteRecordRepository
import com.mapmate.testing.TestDepartureScheduleStore
import com.mapmate.testing.TestRoutineRepository
import com.mapmate.testing.TestSettingsRepository
import com.mapmate.testing.sampleRoutine
import com.mapmate.domain.model.CommuteRecord
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DepartureAlarmGuardTest {
    @Test
    fun currentRecheckQueriesRoutesButOldTriggerDoesNot() = runTest {
        val fixture = Fixture()
        fixture.coordinator().rescheduleNextAlarm()
        val original = requireNotNull(fixture.store.state.schedule)
        val calls = fixture.routes.calls

        assertTrue(fixture.coordinator().recheckScheduledAlarm(original))
        assertTrue(fixture.routes.calls > calls)
        val beforeStale = fixture.routes.calls
        assertFalse(fixture.coordinator().recheckScheduledAlarm(original.copy(triggerAtEpochMillis = original.triggerAtEpochMillis - 60_000)))
        assertEquals(beforeStale, fixture.routes.calls)
    }

    @Test
    fun deletedEditedInactiveDisabledAndCompletedEventsCannotRecheckOrNotify() = runTest {
        for (change in listOf("deleted", "edited", "inactive", "disabled", "permission", "completed")) {
            val fixture = Fixture()
            fixture.coordinator().rescheduleNextAlarm()
            val schedule = requireNotNull(fixture.store.state.schedule)
            when (change) {
                "deleted" -> fixture.routines.routines.value = emptyList()
                "edited" -> fixture.routines.routines.value = listOf(fixture.routine.copy(destination = fixture.routine.destination.copy(latitude = 38.0)))
                "inactive" -> fixture.routines.routines.value = listOf(fixture.routine.copy(repeatDays = emptySet()))
                "disabled" -> fixture.settings.updateNotificationsEnabled(false)
                "permission" -> fixture.alarms.permission = false
                "completed" -> fixture.records.records.value = listOf(completedRecord(schedule, fixture))
            }
            val calls = fixture.routes.calls
            assertFalse(change, fixture.coordinator().recheckScheduledAlarm(schedule))
            fixture.now = Instant.ofEpochMilli(schedule.triggerAtEpochMillis).atZone(fixture.now.zone)
            var published = false
            assertFalse(change, fixture.coordinator().publishCurrentAlarm(schedule) { published = true })
            assertFalse(change, published)
            assertEquals(change, calls, fixture.routes.calls)
        }
    }

    @Test
    fun delayedRecheckAfterDepartureDoesNotOverwriteAlarm() = runTest {
        val fixture = Fixture()
        fixture.coordinator().rescheduleNextAlarm()
        val schedule = requireNotNull(fixture.store.state.schedule)
        fixture.now = Instant.ofEpochMilli(schedule.triggerAtEpochMillis + 1).atZone(fixture.now.zone)
        val calls = fixture.routes.calls
        assertFalse(fixture.coordinator().recheckScheduledAlarm(schedule))
        assertEquals(calls, fixture.routes.calls)
    }

    @Test
    fun notificationClaimSurvivesCoordinatorRecreationAndNextSyncSkipsFiredEvent() = runTest {
        val fixture = Fixture()
        fixture.coordinator().rescheduleNextAlarm()
        val schedule = requireNotNull(fixture.store.state.schedule)
        fixture.now = Instant.ofEpochMilli(schedule.triggerAtEpochMillis).atZone(fixture.now.zone)
        var notifications = 0

        assertTrue(fixture.coordinator().publishCurrentAlarm(schedule) { notifications++ })
        assertFalse(fixture.coordinator().publishCurrentAlarm(schedule) { notifications++ })
        assertEquals(1, notifications)
        assertEquals(listOf(schedule.eventOrNull()), fixture.store.state.notifiedEvents)

        fixture.coordinator().rescheduleNextAlarm()
        assertTrue(requireNotNull(fixture.store.state.schedule?.targetArrivalAtEpochMillis) >
            requireNotNull(schedule.targetArrivalAtEpochMillis))
    }

    @Test
    fun failedNotificationPublicationCanRetryWithoutLosingTheEvent() = runTest {
        val fixture = Fixture()
        fixture.coordinator().rescheduleNextAlarm()
        val schedule = requireNotNull(fixture.store.state.schedule)
        fixture.now = Instant.ofEpochMilli(schedule.triggerAtEpochMillis).atZone(fixture.now.zone)
        assertTrue(runCatching {
            fixture.coordinator().publishCurrentAlarm(schedule) { error("notification service failed") }
        }.isFailure)
        assertTrue(fixture.store.state.notifiedEvents.isEmpty())
        var notifications = 0
        assertTrue(fixture.coordinator().publishCurrentAlarm(schedule) { notifications++ })
        assertEquals(1, notifications)
    }

    @Test
    fun notificationCannotFireEarlyOrAfterTargetArrival() = runTest {
        val fixture = Fixture()
        fixture.coordinator().rescheduleNextAlarm()
        val schedule = requireNotNull(fixture.store.state.schedule)
        assertFalse(fixture.coordinator().publishCurrentAlarm(schedule) { fail("early notification") })
        fixture.now = Instant.ofEpochMilli(requireNotNull(schedule.targetArrivalAtEpochMillis) + 1).atZone(fixture.now.zone)
        assertFalse(fixture.coordinator().publishCurrentAlarm(schedule) { fail("expired notification") })
    }

    @Test
    fun dueNotificationDoesNotWaitForSlowRouteQueryAndRefreshCannotReinstallFiredEvent() = runTest {
        val fixture = Fixture()
        val coordinator = fixture.coordinator()
        coordinator.rescheduleNextAlarm()
        val scheduled = requireNotNull(fixture.store.state.schedule)
        fixture.now = Instant.ofEpochMilli(scheduled.triggerAtEpochMillis).atZone(fixture.now.zone)
        val queryGate = CompletableDeferred<Unit>()
        fixture.routes.gate = queryGate
        val refresh = launch { coordinator.rescheduleNextAlarm() }
        runCurrent()
        var notifications = 0
        val publication = launch { coordinator.publishCurrentAlarm(scheduled) { notifications++ } }
        try {
            runCurrent()
            assertEquals("A due notification must not wait for a network response", 1, notifications)
            assertTrue(publication.isCompleted)
        } finally {
            queryGate.complete(Unit)
            refresh.join()
            publication.join()
        }
        assertEquals(scheduled, fixture.store.state.schedule)
        assertFalse(coordinator.publishCurrentAlarm(scheduled) { fail("Duplicate notification") })
        coordinator.rescheduleNextAlarm()
        assertTrue(requireNotNull(fixture.store.state.schedule?.targetArrivalAtEpochMillis) >
            requireNotNull(scheduled.targetArrivalAtEpochMillis))
    }

    @Test
    fun routineEditDuringRouteQueryDoesNotCommitOldSchedule() = runTest {
        val fixture = Fixture()
        fixture.routes.gate = CompletableDeferred()
        val job = launch { fixture.coordinator().rescheduleNextAlarm() }
        runCurrent()
        fixture.routines.routines.value = listOf(fixture.routine.copy(name = "Changed"))
        fixture.routes.gate!!.complete(Unit)
        job.join()

        assertNull(fixture.store.state.schedule)
        assertNull(fixture.alarms.scheduled)
    }

    private class Fixture {
        var now = ZonedDateTime.of(2026, 10, 2, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val routine = sampleRoutine()
        val routines = TestRoutineRepository(listOf(routine))
        val records = TestCommuteRecordRepository()
        val settings = TestSettingsRepository().apply {
            this.settings.value = this.settings.value.copy(notificationsEnabled = true)
        }
        val store = TestDepartureScheduleStore()
        val routes = Routes()
        val alarms = Alarms()
        fun coordinator() = DepartureAlarmCoordinator(
            settingsRepository = settings,
            routineRepository = routines,
            commuteRecordRepository = records,
            scheduleStore = store,
            routeEstimateProvider = routes,
            alarmScheduler = alarms,
            recheckScheduler = object : DepartureRecheckScheduler {
                override fun schedule(schedule: DepartureAlarmSchedule, replaceExisting: Boolean) = Unit
                override fun cancel() = Unit
            },
            nowProvider = { now },
        )
    }

    private class Routes : RouteEstimateProvider {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun getRouteEstimate(
            origin: Destination, destination: Destination, transportMode: TransportMode,
            routineId: Long?, scheduledDepartureEpochMillis: Long?, targetArrivalEpochMillis: Long?,
        ): RouteEstimate {
            calls++
            gate?.await()
            return RouteEstimate(30, "Test", "Test", "Test")
        }
    }

    private class Alarms : DepartureAlarmScheduler {
        var permission = true
        var scheduled: DepartureAlarmSchedule? = null
        override fun canPostDepartureNotifications() = permission
        override fun schedule(schedule: DepartureAlarmSchedule) { scheduled = schedule }
        override fun cancel() { scheduled = null }
    }

    private fun completedRecord(schedule: DepartureAlarmSchedule, fixture: Fixture) = CommuteRecord(
        id = 1L, routineId = schedule.routineId, routineName = fixture.routine.name,
        originName = fixture.routine.origin.name, destinationName = fixture.routine.destination.name,
        transportMode = fixture.routine.transportMode, targetArrivalTime = schedule.targetArrivalTime,
        targetArrivalAtEpochMillis = schedule.targetArrivalAtEpochMillis,
        recommendedDepartureTime = schedule.recommendedDepartureTime,
        routeDurationMinutes = schedule.routeDurationMinutes, routeSummary = "Test",
        startedAtEpochMillis = schedule.triggerAtEpochMillis, arrivedAtEpochMillis = schedule.triggerAtEpochMillis + 1_000,
        arrivalDeltaMinutes = 0,
    )
}
