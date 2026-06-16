package com.mapmate.data.alarm

import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureAlarmScheduler
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import com.mapmate.domain.alarm.PredepartureStatusNotificationPublisher
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DepartureAlarmCoordinatorTest {
    private val routine = sampleRoutine()

    @Test
    fun rescheduleNextAlarm_schedulesAlarmAndRecheckWhenNotificationsAreEnabled() = runTest {
        val alarmScheduler = FakeAlarmScheduler()
        val recheckScheduler = FakeRecheckScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(routine),
            routeEstimateProvider = FixedRouteEstimateProvider(estimatedMinutes = 12),
            alarmScheduler = alarmScheduler,
            recheckScheduler = recheckScheduler,
        )

        coordinator.rescheduleNextAlarm()

        assertNotNull(alarmScheduler.scheduled)
        assertNotNull(recheckScheduler.scheduled)
        assertEquals(12, alarmScheduler.scheduled!!.routeDurationMinutes)
        assertEquals(alarmScheduler.scheduled, recheckScheduler.scheduled)
        assertEquals(true, recheckScheduler.replaceExisting)
        assertEquals(0, alarmScheduler.cancelCount)
        assertEquals(0, recheckScheduler.cancelCount)
    }

    @Test
    fun rescheduleNextAlarm_keepsRunningRecheckWorkWhenPreviousScheduleExists() = runTest {
        val alarmScheduler = FakeAlarmScheduler()
        val recheckScheduler = FakeRecheckScheduler()
        val routeEstimateProvider = FixedRouteEstimateProvider(estimatedMinutes = 12)
        val previousSchedule = previousScheduleFor(routine)
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(routine),
            routeEstimateProvider = routeEstimateProvider,
            alarmScheduler = alarmScheduler,
            recheckScheduler = recheckScheduler,
        )

        coordinator.rescheduleNextAlarm(previousSchedule = previousSchedule)

        assertNotNull(alarmScheduler.scheduled)
        assertEquals(alarmScheduler.scheduled, recheckScheduler.scheduled)
        assertEquals(false, recheckScheduler.replaceExisting)
        assertEquals(listOf(previousSchedule.triggerAtEpochMillis), routeEstimateProvider.scheduledDepartureCalls)
        assertEquals(0, recheckScheduler.cancelCount)
    }

    @Test
    fun rescheduleNextAlarm_cancelsAlarmAndRecheckWhenNotificationsAreDisabled() = runTest {
        val alarmScheduler = FakeAlarmScheduler()
        val recheckScheduler = FakeRecheckScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = false),
            routines = listOf(routine),
            routeEstimateProvider = FixedRouteEstimateProvider(estimatedMinutes = 12),
            alarmScheduler = alarmScheduler,
            recheckScheduler = recheckScheduler,
        )

        coordinator.rescheduleNextAlarm()

        assertNull(alarmScheduler.scheduled)
        assertNull(recheckScheduler.scheduled)
        assertEquals(1, alarmScheduler.cancelCount)
        assertEquals(1, recheckScheduler.cancelCount)
    }

    @Test
    fun rescheduleNextAlarm_usesFallbackDurationWhenRouteProviderFails() = runTest {
        val alarmScheduler = FakeAlarmScheduler()
        val recheckScheduler = FakeRecheckScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(routine),
            routeEstimateProvider = FailingRouteEstimateProvider,
            alarmScheduler = alarmScheduler,
            recheckScheduler = recheckScheduler,
        )

        coordinator.rescheduleNextAlarm()

        assertNotNull(alarmScheduler.scheduled)
        assertEquals(42, alarmScheduler.scheduled!!.routeDurationMinutes)
        assertEquals(alarmScheduler.scheduled, recheckScheduler.scheduled)
    }

    @Test
    fun rescheduleNextAlarm_showsPredepartureStatusNotificationWithinThirtyMinutes() = runTest {
        val now = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val statusNotificationPublisher = FakePredepartureStatusNotificationPublisher()
        val coordinator = coordinator(
            settings = AppSettings(
                notificationsEnabled = true,
                predepartureStatusNotificationEnabled = true,
            ),
            routines = listOf(sampleRoutine(targetArrivalTime = LocalTime.of(8, 20))),
            routeEstimateProvider = FixedRouteEstimateProvider(estimatedMinutes = 10),
            alarmScheduler = FakeAlarmScheduler(),
            recheckScheduler = FakeRecheckScheduler(),
            predepartureStatusNotificationPublisher = statusNotificationPublisher,
            nowProvider = { now },
        )

        coordinator.rescheduleNextAlarm()

        assertNotNull(statusNotificationPublisher.shown)
        assertEquals(LocalTime.of(8, 10), statusNotificationPublisher.shown!!.recommendedDepartureTime)
    }

    @Test
    fun rescheduleNextAlarm_doesNotShowPredepartureStatusNotificationBeforeThirtyMinuteWindow() = runTest {
        val now = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val statusNotificationPublisher = FakePredepartureStatusNotificationPublisher()
        val coordinator = coordinator(
            settings = AppSettings(
                notificationsEnabled = true,
                predepartureStatusNotificationEnabled = true,
            ),
            routines = listOf(sampleRoutine(targetArrivalTime = LocalTime.of(9, 0))),
            routeEstimateProvider = FixedRouteEstimateProvider(estimatedMinutes = 10),
            alarmScheduler = FakeAlarmScheduler(),
            recheckScheduler = FakeRecheckScheduler(),
            predepartureStatusNotificationPublisher = statusNotificationPublisher,
            nowProvider = { now },
        )

        coordinator.rescheduleNextAlarm()

        assertNull(statusNotificationPublisher.shown)
        assertEquals(listOf(7L), statusNotificationPublisher.cancelledAllRoutineIds)
    }

    private fun coordinator(
        settings: AppSettings,
        routines: List<Routine>,
        routeEstimateProvider: RouteEstimateProvider,
        alarmScheduler: DepartureAlarmScheduler,
        recheckScheduler: DepartureRecheckScheduler,
        predepartureStatusNotificationPublisher: PredepartureStatusNotificationPublisher =
            PredepartureStatusNotificationPublisher.NoOp,
        nowProvider: () -> ZonedDateTime = { ZonedDateTime.now(ZoneId.of("Asia/Seoul")) },
    ): DepartureAlarmCoordinator {
        return DepartureAlarmCoordinator(
            settingsRepository = FakeSettingsRepository(settings),
            routineRepository = FakeRoutineRepository(routines),
            routeEstimateProvider = routeEstimateProvider,
            alarmScheduler = alarmScheduler,
            recheckScheduler = recheckScheduler,
            predepartureStatusNotificationPublisher = predepartureStatusNotificationPublisher,
            planner = DepartureAlarmPlanner(zoneId = ZoneId.of("Asia/Seoul")),
            nowProvider = nowProvider,
        )
    }

    private class FakeAlarmScheduler : DepartureAlarmScheduler {
        var scheduled: DepartureAlarmSchedule? = null
        var cancelCount = 0

        override fun canPostDepartureNotifications(): Boolean = true

        override fun schedule(schedule: DepartureAlarmSchedule) {
            scheduled = schedule
        }

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class FakeRecheckScheduler : DepartureRecheckScheduler {
        var scheduled: DepartureAlarmSchedule? = null
        var replaceExisting: Boolean? = null
        var cancelCount = 0

        override fun schedule(
            schedule: DepartureAlarmSchedule,
            replaceExisting: Boolean,
        ) {
            scheduled = schedule
            this.replaceExisting = replaceExisting
        }

        override fun cancel() {
            cancelCount += 1
        }
    }

    private class FakePredepartureStatusNotificationPublisher : PredepartureStatusNotificationPublisher {
        var shown: DepartureAlarmSchedule? = null
        var cancelledRoutineId: Long? = null
        var cancelledAllRoutineIds: List<Long> = emptyList()

        override fun show(schedule: DepartureAlarmSchedule) {
            shown = schedule
        }

        override fun cancel(routineId: Long) {
            cancelledRoutineId = routineId
        }

        override fun cancelAll(routineIds: Collection<Long>) {
            cancelledAllRoutineIds = routineIds.toList()
        }
    }

    private class FakeRoutineRepository(
        private val routines: List<Routine>,
    ) : RoutineRepository {
        override suspend fun saveRoutine(routine: Routine): Long = routine.id ?: 1L

        override suspend fun deleteRoutine(id: Long) = Unit

        override fun observeRoutines(): Flow<List<Routine>> = flowOf(routines)
    }

    private class FakeSettingsRepository(
        settingsValue: AppSettings,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> = flowOf(settingsValue)

        override suspend fun updatePersonalBufferMinutes(minutes: Int) = Unit

        override suspend fun updatePersonalBufferForRecentArrivalDeltas(recentArrivalDeltaMinutes: List<Int>): Int {
            return AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES
        }

        override suspend fun updateSafetyMarginMinutes(minutes: Int) = Unit

        override suspend fun updateNotificationsEnabled(enabled: Boolean) = Unit

        override suspend fun updatePredepartureStatusNotificationEnabled(enabled: Boolean) = Unit

        override suspend fun updateDefaultTransportMode(transportMode: TransportMode) = Unit
    }

    private class FixedRouteEstimateProvider(
        private val estimatedMinutes: Int,
    ) : RouteEstimateProvider {
        val scheduledDepartureCalls = mutableListOf<Long?>()

        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routineId: Long?,
            scheduledDepartureEpochMillis: Long?,
        ): RouteEstimate {
            scheduledDepartureCalls += scheduledDepartureEpochMillis
            return RouteEstimate(
                estimatedMinutes = estimatedMinutes,
                summary = "fixed",
                providerName = "fixed",
                reason = "test",
            )
        }
    }

    private object FailingRouteEstimateProvider : RouteEstimateProvider {
        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routineId: Long?,
            scheduledDepartureEpochMillis: Long?,
        ): RouteEstimate {
            error("Route provider failed")
        }
    }

    private companion object {
        fun sampleRoutine(
            targetArrivalTime: LocalTime = LocalTime.of(23, 59),
        ): Routine {
            return Routine(
                id = 7L,
                name = "등교",
                origin = Destination(
                    name = "집",
                    address = "집 주소",
                    latitude = 37.0,
                    longitude = 127.0,
                ),
                destination = Destination(
                    name = "학교",
                    address = "학교 주소",
                    latitude = 37.5,
                    longitude = 127.5,
                ),
                targetArrivalTime = targetArrivalTime,
                repeatDays = RepeatDay.entries.toSet(),
                transportMode = TransportMode.TRANSIT,
                personalBufferMinutes = 0,
                safetyMarginMinutes = 0,
            )
        }

        fun previousScheduleFor(routine: Routine): DepartureAlarmSchedule {
            return DepartureAlarmSchedule(
                routineId = routine.id ?: 0L,
                routineName = routine.name,
                destinationName = routine.destination.name,
                targetArrivalTime = routine.targetArrivalTime,
                recommendedDepartureTime = LocalTime.of(23, 17),
                routeDurationMinutes = 42,
                triggerAtEpochMillis = Long.MAX_VALUE,
            )
        }
    }
}
