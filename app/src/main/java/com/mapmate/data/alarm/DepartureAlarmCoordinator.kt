package com.mapmate.data.alarm

import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAlarmScheduler
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import com.mapmate.domain.alarm.PredepartureStatusNotificationPublisher
import com.mapmate.domain.alarm.applyBoardingSafeDeparture
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

class DepartureAlarmCoordinator(
    private val settingsRepository: SettingsRepository,
    private val routineRepository: RoutineRepository,
    private val routeEstimateProvider: RouteEstimateProvider,
    private val alarmScheduler: DepartureAlarmScheduler,
    private val recheckScheduler: DepartureRecheckScheduler,
    private val predepartureStatusNotificationPublisher: PredepartureStatusNotificationPublisher =
        PredepartureStatusNotificationPublisher.NoOp,
    private val planner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy = DepartureAdjustmentPolicy(),
    private val nowProvider: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    suspend fun keepAlarmsInSync() {
        settingsRepository.settings
            .combine(routineRepository.observeRoutines()) { settings, routines ->
                settings to routines
            }
            .distinctUntilChanged()
            .collectLatest { (settings, routines) ->
                sync(settings = settings, routines = routines)
            }
    }

    suspend fun rescheduleNextAlarm(previousSchedule: DepartureAlarmSchedule? = null) {
        sync(
            settings = settingsRepository.settings.first(),
            routines = routineRepository.observeRoutines().first(),
            previousSchedule = previousSchedule,
        )
    }

    suspend fun rescheduleAfterAlarmFired(firedSchedule: DepartureAlarmSchedule?) {
        sync(
            settings = settingsRepository.settings.first(),
            routines = routineRepository.observeRoutines().first(),
            firedSchedule = firedSchedule,
        )
    }

    private suspend fun sync(
        settings: AppSettings,
        routines: List<Routine>,
        previousSchedule: DepartureAlarmSchedule? = null,
        firedSchedule: DepartureAlarmSchedule? = null,
    ) {
        if (!settings.notificationsEnabled || !alarmScheduler.canPostDepartureNotifications()) {
            cancelScheduledWork(routines)
            return
        }

        val now = nowProvider()
        val nowEpochMillis = now.toInstant().toEpochMilli()
        val routineRouteEstimates = routines.map { routine ->
            routine to routeEstimateFor(
                routine = routine,
                schedule = previousSchedule
                    ?.takeIf { it.routineId == routine.id }
                    ?.takeIf { it.triggerAtEpochMillis >= nowEpochMillis }
            )
        }
        val routineRouteDurations = routineRouteEstimates.map { (routine, estimate) ->
            routine to estimate.estimatedMinutes
        }
        val routeEstimateByRoutineId = routineRouteEstimates
            .mapNotNull { (routine, estimate) ->
                routine.id?.let { it to estimate }
            }
            .toMap()
        val proposedNextAlarm = planner.nextAlarm(
            routineRouteDurations = routineRouteDurations,
            now = now,
            excludedSchedule = firedSchedule,
        )

        if (proposedNextAlarm == null) {
            cancelScheduledWork(routines)
        } else {
            val nextAlarm = adjustmentPolicy.adjust(
                previousSchedule = previousSchedule,
                proposedSchedule = proposedNextAlarm,
            ).applyBoardingSafeDeparture(
                boardingAdvice = routeEstimateByRoutineId[proposedNextAlarm.routineId]?.boardingAdvice,
                nowEpochMillis = nowEpochMillis,
                zoneId = now.zone,
            )
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
    }

    private fun cancelScheduledWork(routines: List<Routine>) {
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

    private suspend fun routeEstimateFor(
        routine: Routine,
        schedule: DepartureAlarmSchedule?,
    ): RouteEstimate {
        return runCatching {
            routeEstimateProvider.getRouteEstimate(
                origin = routine.origin,
                destination = routine.destination,
                transportMode = routine.transportMode,
                routineId = routine.id,
                scheduledDepartureEpochMillis = schedule?.triggerAtEpochMillis,
                targetArrivalEpochMillis = schedule?.targetArrivalAtEpochMillis,
            )
        }.getOrElse {
            RouteEstimate(
                estimatedMinutes = routine.transportMode.fallbackRouteDurationMinutes(),
                summary = "기본 예상 이동 시간",
                providerName = "Fallback",
                reason = "경로 계산 실패로 기본 예상 시간을 사용했습니다.",
                isFallbackEstimate = true,
            )
        }
    }

    private fun TransportMode.fallbackRouteDurationMinutes(): Int {
        return when (this) {
            TransportMode.TRANSIT -> 42
            TransportMode.WALK -> 25
            TransportMode.CAR -> 30
        }
    }

    private companion object {
        const val PREDEPARTURE_STATUS_WINDOW_MILLIS = 30 * 60 * 1000L
    }
}
