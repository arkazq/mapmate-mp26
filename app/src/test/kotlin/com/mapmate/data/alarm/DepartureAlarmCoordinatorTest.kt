package com.mapmate.data.alarm

import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureAlarmScheduler
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import com.mapmate.domain.alarm.PredepartureStatusNotificationPublisher
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteBoardingStatus
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import com.mapmate.testing.TestCommuteRecordRepository
import com.mapmate.testing.TestDepartureScheduleStore
import com.mapmate.domain.repository.CommuteRecordRepository

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DepartureAlarmCoordinatorTest {
    private val routine = sampleRoutine()

    @Test
    fun rescheduleNextAlarm_skipsCompletedEventAndSchedulesNextRoutine() = runTest {
        val completedRoutine = sampleRoutine(LocalTime.of(9, 0))
        val nextRoutine = sampleRoutine(LocalTime.of(10, 0)).copy(id = 8L)
        val alarmScheduler = FakeAlarmScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(completedRoutine, nextRoutine),
            routeEstimateProvider = FixedRouteEstimateProvider(10),
            alarmScheduler = alarmScheduler,
            recheckScheduler = FakeRecheckScheduler(),
            nowProvider = { at(8, 45) },
            commuteRecordRepository = TestCommuteRecordRepository(
                listOf(completedRecord(completedRoutine, epochAt(9, 0))),
            ),
        )

        coordinator.rescheduleNextAlarm()

        assertEquals(8L, alarmScheduler.scheduled!!.routineId)
        assertEquals(epochAt(10, 0), alarmScheduler.scheduled!!.targetArrivalAtEpochMillis)
    }

    @Test
    fun rescheduleNextAlarm_skipsCompletedNextDayArrivalAcrossMidnight() = runTest {
        val overnight = sampleRoutine(LocalTime.of(0, 30))
        val targetArrival = at(0, 30).plusDays(1).toInstant().toEpochMilli()
        val alarmScheduler = FakeAlarmScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(overnight),
            routeEstimateProvider = FixedRouteEstimateProvider(60),
            alarmScheduler = alarmScheduler,
            recheckScheduler = FakeRecheckScheduler(),
            nowProvider = { at(23, 35) },
            commuteRecordRepository = TestCommuteRecordRepository(
                listOf(completedRecord(overnight, targetArrival)),
            ),
        )

        coordinator.rescheduleNextAlarm()

        assertEquals(at(0, 30).plusDays(2).toInstant().toEpochMilli(),
            alarmScheduler.scheduled!!.targetArrivalAtEpochMillis)
    }

    @Test
    fun keepAlarmsInSync_completionReschedulesWithoutSettingsOrRoutineChange() = runTest {
        val targetRoutine = sampleRoutine(LocalTime.of(9, 0))
        val records = TestCommuteRecordRepository()
        val alarmScheduler = FakeAlarmScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(targetRoutine),
            routeEstimateProvider = FixedRouteEstimateProvider(10),
            alarmScheduler = alarmScheduler,
            recheckScheduler = FakeRecheckScheduler(),
            nowProvider = { at(8, 45) },
            commuteRecordRepository = records,
        )
        backgroundScope.launch { coordinator.keepAlarmsInSync() }
        runCurrent()
        assertEquals(epochAt(9, 0), alarmScheduler.scheduled!!.targetArrivalAtEpochMillis)

        records.records.value = listOf(completedRecord(targetRoutine, epochAt(9, 0)))
        runCurrent()

        assertEquals(at(9, 0).plusDays(1).toInstant().toEpochMilli(),
            alarmScheduler.scheduled!!.targetArrivalAtEpochMillis)
    }

    @Test
    fun rescheduleNextAlarm_doesNotQueryRoutesWithoutRepeatDaysOrPersistedId() = runTest {
        val provider = FixedRouteEstimateProvider(10)
        val alarmScheduler = FakeAlarmScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(routine.copy(repeatDays = emptySet()), routine.copy(id = null)),
            routeEstimateProvider = provider,
            alarmScheduler = alarmScheduler,
            recheckScheduler = FakeRecheckScheduler(),
        )

        coordinator.rescheduleNextAlarm()

        assertEquals(emptyList<Long?>(), provider.scheduledDepartureCalls)
        assertNull(alarmScheduler.scheduled)
        assertEquals(1, alarmScheduler.cancelCount)
    }

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
            nowProvider = {
                ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, ZoneId.of("Asia/Seoul"))
            },
        )

        coordinator.rescheduleNextAlarm(previousSchedule = previousSchedule)

        assertNotNull(alarmScheduler.scheduled)
        assertEquals(alarmScheduler.scheduled, recheckScheduler.scheduled)
        assertEquals(false, recheckScheduler.replaceExisting)
        assertEquals(listOf<Long?>(null), routeEstimateProvider.scheduledDepartureCalls)
        assertEquals(0, recheckScheduler.cancelCount)
    }

    @Test
    fun rescheduleNextAlarm_appliesBoardingSafeDepartureFromRouteEstimate() = runTest {
        val now = ZonedDateTime.of(2026, 6, 16, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val routine = sampleRoutine(targetArrivalTime = LocalTime.of(9, 30))
        val previousSchedule = DepartureAlarmSchedule(
            routineId = routine.id ?: 0L,
            routineName = routine.name,
            destinationName = routine.destination.name,
            targetArrivalTime = routine.targetArrivalTime,
            recommendedDepartureTime = LocalTime.of(9, 20),
            routeDurationMinutes = 10,
            triggerAtEpochMillis = epochAt(9, 20),
            targetArrivalAtEpochMillis = epochAt(9, 30),
        )
        val routeEstimateProvider = FixedRouteEstimateProvider(
            estimatedMinutes = 10,
            boardingAdvice = RouteBoardingAdvice(
                selectedCandidateIndex = 1,
                candidateCount = 1,
                routeName = "753",
                stationName = "정류장",
                accessMinutes = 4,
                realtimeWaitMinutes = 12,
                slackMinutes = -12,
                status = RouteBoardingStatus.MISS_RISK,
                estimatedTotalMinutes = 10,
                safeDepartureEpochMillis = epochAt(9, 5),
                earlyDepartureRequiredMinutes = 15,
            ),
        )
        val alarmScheduler = FakeAlarmScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(routine),
            routeEstimateProvider = routeEstimateProvider,
            alarmScheduler = alarmScheduler,
            recheckScheduler = FakeRecheckScheduler(),
            nowProvider = { now },
        )

        coordinator.rescheduleNextAlarm(previousSchedule = previousSchedule)

        assertEquals(epochAt(9, 5), alarmScheduler.scheduled!!.triggerAtEpochMillis)
        assertEquals(LocalTime.of(9, 5), alarmScheduler.scheduled!!.recommendedDepartureTime)
        assertEquals(listOf(null, epochAt(9, 20)), routeEstimateProvider.scheduledDepartureCalls)
        assertEquals(listOf(null, epochAt(9, 30)), routeEstimateProvider.targetArrivalCalls)
    }

    @Test
    fun rescheduleNextAlarm_appliesBoardingSafeDepartureOnInitialSync() = runTest {
        val now = ZonedDateTime.of(2026, 6, 16, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"))
        val routine = sampleRoutine(targetArrivalTime = LocalTime.of(9, 30))
        val routeEstimateProvider = FixedRouteEstimateProvider(
            estimatedMinutes = 10,
            boardingAdvice = RouteBoardingAdvice(
                selectedCandidateIndex = 1,
                candidateCount = 1,
                routeName = "753",
                stationName = "station",
                accessMinutes = 4,
                realtimeWaitMinutes = 12,
                slackMinutes = -12,
                status = RouteBoardingStatus.MISS_RISK,
                estimatedTotalMinutes = 10,
                safeDepartureEpochMillis = epochAt(9, 5),
                earlyDepartureRequiredMinutes = 15,
            ),
        )
        val alarmScheduler = FakeAlarmScheduler()
        val coordinator = coordinator(
            settings = AppSettings(notificationsEnabled = true),
            routines = listOf(routine),
            routeEstimateProvider = routeEstimateProvider,
            alarmScheduler = alarmScheduler,
            recheckScheduler = FakeRecheckScheduler(),
            nowProvider = { now },
        )

        coordinator.rescheduleNextAlarm()

        assertEquals(epochAt(9, 5), alarmScheduler.scheduled!!.triggerAtEpochMillis)
        assertEquals(LocalTime.of(9, 5), alarmScheduler.scheduled!!.recommendedDepartureTime)
        assertEquals(listOf(null, epochAt(9, 20)), routeEstimateProvider.scheduledDepartureCalls)
        assertEquals(listOf(null, epochAt(9, 30)), routeEstimateProvider.targetArrivalCalls)
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
        commuteRecordRepository: CommuteRecordRepository = TestCommuteRecordRepository(),
    ): DepartureAlarmCoordinator {
        return DepartureAlarmCoordinator(
            settingsRepository = FakeSettingsRepository(settings),
            routineRepository = FakeRoutineRepository(routines),
            commuteRecordRepository = commuteRecordRepository,
            scheduleStore = TestDepartureScheduleStore(),
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

        override suspend fun updatePersonalBufferMinutes(expectedRoutine: Routine, minutes: Int) = false

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

        override suspend fun updateBufferDefaults(personalBufferMinutes: Int, safetyMarginMinutes: Int) = Unit

        override suspend fun updateNotificationsEnabled(enabled: Boolean) = Unit

        override suspend fun updatePredepartureStatusNotificationEnabled(enabled: Boolean) = Unit

        override suspend fun updateDefaultTransportMode(transportMode: TransportMode) = Unit
    }

    private class FixedRouteEstimateProvider(
        private val estimatedMinutes: Int,
        private val boardingAdvice: RouteBoardingAdvice? = null,
    ) : RouteEstimateProvider {
        val scheduledDepartureCalls = mutableListOf<Long?>()
        val targetArrivalCalls = mutableListOf<Long?>()

        override suspend fun getRouteEstimate(
            origin: Destination,
            destination: Destination,
            transportMode: TransportMode,
            routineId: Long?,
            scheduledDepartureEpochMillis: Long?,
            targetArrivalEpochMillis: Long?,
        ): RouteEstimate {
            scheduledDepartureCalls += scheduledDepartureEpochMillis
            targetArrivalCalls += targetArrivalEpochMillis
            return RouteEstimate(
                estimatedMinutes = estimatedMinutes,
                summary = "fixed",
                providerName = "fixed",
                reason = "test",
                boardingAdvice = boardingAdvice,
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
            targetArrivalEpochMillis: Long?,
        ): RouteEstimate {
            error("Route provider failed")
        }
    }

    private companion object {
        fun at(hour: Int, minute: Int): ZonedDateTime =
            ZonedDateTime.of(2026, 6, 16, hour, minute, 0, 0, ZoneId.of("Asia/Seoul"))

        fun completedRecord(routine: Routine, targetArrivalEpochMillis: Long) = CommuteRecord(
            id = 1L,
            routineId = routine.id,
            routineName = routine.name,
            originName = routine.origin.name,
            destinationName = routine.destination.name,
            transportMode = routine.transportMode,
            targetArrivalTime = routine.targetArrivalTime,
            targetArrivalAtEpochMillis = targetArrivalEpochMillis,
            recommendedDepartureTime = routine.targetArrivalTime.minusMinutes(10),
            routeDurationMinutes = 10,
            routeSummary = "test",
            startedAtEpochMillis = targetArrivalEpochMillis - 20 * 60_000,
            arrivedAtEpochMillis = targetArrivalEpochMillis - 10 * 60_000,
            arrivalDeltaMinutes = -10,
        )

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

        fun epochAt(hour: Int, minute: Int): Long {
            return ZonedDateTime.of(2026, 6, 16, hour, minute, 0, 0, ZoneId.of("Asia/Seoul"))
                .toInstant()
                .toEpochMilli()
        }
    }
}
