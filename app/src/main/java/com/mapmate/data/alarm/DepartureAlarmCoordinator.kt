package com.mapmate.data.alarm

import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAlarmScheduler
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import com.mapmate.domain.alarm.DepartureScheduleStore
import com.mapmate.domain.alarm.DepartureScheduleState
import com.mapmate.domain.alarm.eventOrNull
import com.mapmate.domain.alarm.routineScheduleFingerprint
import com.mapmate.domain.alarm.PredepartureStatusNotificationPublisher
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import com.mapmate.domain.repository.CommuteRecordRepository
import com.mapmate.domain.alarm.completedArrivalEventsToExclude
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.calculator.ScheduledRouteCalculator
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.provider.ScheduledRouteRecommendation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DepartureAlarmCoordinator(
    private val settingsRepository: SettingsRepository,
    private val routineRepository: RoutineRepository,
    private val commuteRecordRepository: CommuteRecordRepository,
    private val scheduleStore: DepartureScheduleStore,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val alarmScheduler: DepartureAlarmScheduler,
    private val recheckScheduler: DepartureRecheckScheduler,
    private val predepartureStatusNotificationPublisher: PredepartureStatusNotificationPublisher =
        PredepartureStatusNotificationPublisher.NoOp,
    private val planner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy? = null,
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now() },
    private val schedulingMutex: Mutex = Mutex(),
    private val publicationMutex: Mutex = Mutex(),
) : ScheduledRouteProvider {
    private val calculator = ScheduledRouteCalculator(routeEstimateProvider, planner, adjustmentPolicy)
    private val revision = MutableStateFlow(0L)
    override val revisions = revision.asStateFlow()
    private var lastPlan: ResolvedPlan? = null

    override suspend fun invalidate() {
        schedulingMutex.withLock { lastPlan = null }
    }

    override suspend fun resolve(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long?,
        excludedArrivalEvents: Set<Long>,
    ): ScheduledRouteRecommendation = schedulingMutex.withLock {
        val source = readSource()
        val persistedRoutine = source.routines.firstOrNull { it.id == routine.id }
        val completed = source.records.completedArrivalEventsToExclude(routine, now) + excludedArrivalEvents
        if (persistedRoutine != routine || routine.repeatDays.isEmpty()) {
            return@withLock calculator.calculate(routine, now,
                excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis, excludedArrivalEvents = completed)
        }
        val canSchedule = source.settings.notificationsEnabled && alarmScheduler.canPostDepartureNotifications()
        val cached = lastPlan?.takeIf {
            it.source == source && it.canSchedule == canSchedule &&
                now.toInstant().toEpochMilli() - it.calculatedAtEpochMillis in 0L..PLAN_REUSE_MILLIS
        }?.recommendations?.get(requireNotNull(routine.id))?.takeUnless {
            it.schedule?.targetArrivalAtEpochMillis in completed ||
                it.schedule?.targetArrivalAtEpochMillis == excludedArrivalAtEpochMillis
        }
        if (cached != null) return@withLock cached
        if (!canSchedule) {
            val recommendation = calculator.calculate(routine, now,
                excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis, excludedArrivalEvents = completed)
            val reusable = lastPlan?.takeIf { it.source == source && !it.canSchedule &&
                now.toInstant().toEpochMilli() - it.calculatedAtEpochMillis in 0L..PLAN_REUSE_MILLIS }
            lastPlan = ResolvedPlan(source, reusable?.calculatedAtEpochMillis ?: now.toInstant().toEpochMilli(),
                reusable?.recommendations.orEmpty() + (requireNotNull(routine.id) to recommendation), false)
            return@withLock recommendation
        }
        val recommendations = sync(source.settings, source.routines, source.records,
            nowOverride = now, notifyObservers = false)
        val resolved = recommendations[routine.id]
        if (resolved != null && resolved.schedule?.targetArrivalAtEpochMillis !in completed &&
            resolved.schedule?.targetArrivalAtEpochMillis != excludedArrivalAtEpochMillis) return@withLock resolved
        calculator.calculate(routine, now, excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
            excludedArrivalEvents = completed)
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun keepAlarmsInSync() {
        combine(
            settingsRepository.settings,
            routineRepository.observeRoutines(),
            commuteRecordRepository.observeRecords(),
        ) { settings, routines, records ->
                AlarmSourceState(settings, routines, records)
            }
            .distinctUntilChanged()
            .transformLatest { source ->
                schedulingMutex.withLock {
                    sync(settings = source.settings, routines = source.routines, records = source.records)
                }
                emit(Unit)
            }
            .retryWhen { _, attempt ->
                delay((attempt + 1).coerceAtMost(12) * 5_000L)
                true
            }
            .collect()
    }

    suspend fun rescheduleNextAlarm(previousSchedule: DepartureAlarmSchedule? = null) {
        schedulingMutex.withLock {
            val source = readSource()
            sync(source.settings, source.routines, source.records, previousSchedule)
        }
    }

    suspend fun rescheduleAfterAlarmFired(firedSchedule: DepartureAlarmSchedule?) {
        schedulingMutex.withLock {
            val source = readSource()
            sync(source.settings, source.routines, source.records, firedSchedule = firedSchedule)
        }
    }

    suspend fun recheckScheduledAlarm(schedule: DepartureAlarmSchedule): Boolean = schedulingMutex.withLock {
        val source = readSource()
        val now = nowProvider()
        val stored = scheduleStore.read()
        if (!isCurrentEvent(schedule, stored, source, now) ||
            schedule.triggerAtEpochMillis < now.toInstant().toEpochMilli()
        ) return@withLock false
        sync(source.settings, source.routines, source.records, previousSchedule = schedule)
        true
    }

    suspend fun publishCurrentAlarm(
        schedule: DepartureAlarmSchedule,
        publish: (DepartureAlarmSchedule) -> Unit,
    ): Boolean = publicationMutex.withLock {
        val source = readSource()
        val now = nowProvider()
        if (!isCurrentEvent(schedule, scheduleStore.read(), source, now) ||
            schedule.triggerAtEpochMillis > now.toInstant().toEpochMilli()
        ) return@withLock false
        if (!scheduleStore.claimNotification(schedule)) return@withLock false
        try {
            predepartureStatusNotificationPublisher.cancel(schedule.routineId)
            publish(schedule)
        } catch (error: Exception) {
            withContext(NonCancellable) { scheduleStore.releaseNotificationClaim(schedule) }
            throw error
        }
        true
    }

    private suspend fun readSource() = AlarmSourceState(
        settingsRepository.settings.first(),
        routineRepository.observeRoutines().first(),
        commuteRecordRepository.observeRecords().first(),
    )

    private fun isCurrentEvent(
        schedule: DepartureAlarmSchedule,
        stored: DepartureScheduleState,
        source: AlarmSourceState,
        now: ZonedDateTime,
    ): Boolean {
        if (!source.settings.notificationsEnabled || !alarmScheduler.canPostDepartureNotifications()) return false
        if (stored.schedule != schedule) return false
        val event = schedule.eventOrNull() ?: return false
        if (event.targetArrivalAtEpochMillis < now.toInstant().toEpochMilli()) return false
        val routine = source.routines.firstOrNull { it.id == schedule.routineId } ?: return false
        if (routine.repeatDays.isEmpty() || stored.routineFingerprint != routineScheduleFingerprint(routine)) return false
        val target = java.time.Instant.ofEpochMilli(event.targetArrivalAtEpochMillis).atZone(now.zone)
        if (target.toLocalTime() != routine.targetArrivalTime ||
            routine.repeatDays.none { it.name == target.dayOfWeek.name }
        ) return false
        return event.targetArrivalAtEpochMillis !in source.records.completedArrivalEventsToExclude(routine, now) &&
            event !in stored.notifiedEvents
    }

    private suspend fun sync(
        settings: AppSettings,
        routines: List<Routine>,
        records: List<CommuteRecord>,
        previousSchedule: DepartureAlarmSchedule? = null,
        firedSchedule: DepartureAlarmSchedule? = null,
        nowOverride: ZonedDateTime? = null,
        notifyObservers: Boolean = true,
    ): Map<Long, ScheduledRouteRecommendation> {
        if (!settings.notificationsEnabled || !alarmScheduler.canPostDepartureNotifications()) {
            publicationMutex.withLock { cancelScheduledWork(routines) }
            return emptyMap()
        }

        val now = nowOverride ?: nowProvider()
        val nowEpochMillis = now.toInstant().toEpochMilli()
        val stored = scheduleStore.read()
        val recommendations = mutableMapOf<Long, ScheduledRouteRecommendation>()
        val alarmCandidates = mutableListOf<DepartureAlarmSchedule>()
        for (routine in routines.filter { it.id != null && it.repeatDays.isNotEmpty() }) {
            val completed = records.completedArrivalEventsToExclude(routine, now)
            val previous = previousSchedule?.takeIf { it.routineId == routine.id } ?: stored.schedule?.takeIf {
                it.routineId == routine.id && stored.routineFingerprint == routineScheduleFingerprint(routine)
            }
            val recommendation = calculator.calculate(routine, now, previous, excludedArrivalEvents = completed)
            recommendations[requireNotNull(routine.id)] = recommendation
            val notified = stored.notifiedEvents.filter { it.routineId == routine.id }.map { it.targetArrivalAtEpochMillis }.toSet()
            val fired = firedSchedule?.takeIf { it.routineId == routine.id }?.targetArrivalAtEpochMillis
            val alarmRecommendation = if (recommendation.schedule?.targetArrivalAtEpochMillis in notified ||
                (fired != null && recommendation.schedule?.targetArrivalAtEpochMillis == fired)) {
                calculator.calculate(routine, now, previous, fired, completed + notified)
            } else recommendation
            alarmRecommendation.schedule?.let(alarmCandidates::add)
        }
        val nextAlarm = alarmCandidates.minByOrNull { it.triggerAtEpochMillis }

        // Network calculations must not hold up a due notification. Revalidate before committing.
        return publicationMutex.withLock commit@ {
            if (readSource() != AlarmSourceState(settings, routines, records) || scheduleStore.read() != stored) {
                lastPlan = null
                return@commit emptyMap()
            }
            if (nextAlarm == null) {
                cancelScheduledWork(routines)
            } else {
                val scheduledRoutine = routines.first { it.id == nextAlarm.routineId }
                scheduleStore.setSchedule(nextAlarm, routineScheduleFingerprint(scheduledRoutine))
                alarmScheduler.schedule(nextAlarm)
                recheckScheduler.schedule(
                    schedule = nextAlarm,
                    replaceExisting = previousSchedule == null,
                )
                updatePredepartureStatusNotification(
                    settings = settings,
                    schedule = nextAlarm,
                    routines = routines,
                    nowEpochMillis = nowEpochMillis,
                )
            }
            lastPlan = ResolvedPlan(AlarmSourceState(settings, routines, records), nowEpochMillis, recommendations, true)
            if (notifyObservers) revision.value += 1L
            recommendations
        }
    }

    private suspend fun cancelScheduledWork(routines: List<Routine>) {
        lastPlan = null
        scheduleStore.setSchedule(null)
        alarmScheduler.cancel()
        recheckScheduler.cancel()
        predepartureStatusNotificationPublisher.cancelAll(routines.mapNotNull(Routine::id))
    }

    private fun updatePredepartureStatusNotification(
        settings: AppSettings,
        schedule: DepartureAlarmSchedule,
        routines: List<Routine>,
        nowEpochMillis: Long,
    ) {
        if (!settings.predepartureStatusNotificationEnabled) {
            predepartureStatusNotificationPublisher.cancelAll(routines.mapNotNull(Routine::id))
            return
        }

        val remainingMillis = schedule.triggerAtEpochMillis - nowEpochMillis
        when {
            remainingMillis <= 0L -> predepartureStatusNotificationPublisher.cancel(schedule.routineId)
            remainingMillis <= PREDEPARTURE_STATUS_WINDOW_MILLIS -> {
                predepartureStatusNotificationPublisher.cancelAll(
                    routines.mapNotNull(Routine::id).filterNot { it == schedule.routineId },
                )
                predepartureStatusNotificationPublisher.show(schedule)
            }
            else -> predepartureStatusNotificationPublisher.cancelAll(routines.mapNotNull(Routine::id))
        }
    }

    private data class AlarmSourceState(
        val settings: AppSettings,
        val routines: List<Routine>,
        val records: List<CommuteRecord>,
    )

    private data class ResolvedPlan(
        val source: AlarmSourceState,
        val calculatedAtEpochMillis: Long,
        val recommendations: Map<Long, ScheduledRouteRecommendation>,
        val canSchedule: Boolean,
    )

    private companion object {
        const val PREDEPARTURE_STATUS_WINDOW_MILLIS = 30 * 60 * 1000L
        const val PLAN_REUSE_MILLIS = 20_000L
    }
}
