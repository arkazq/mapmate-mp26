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
        val nextAlarm = routines
            .mapNotNull { routine ->
                alarmCandidateFor(
                    routine = routine,
                    now = now,
                    nowEpochMillis = nowEpochMillis,
                    previousSchedule = previousSchedule,
                    firedSchedule = firedSchedule,
                )
            }
            .minByOrNull { it.schedule.triggerAtEpochMillis }
            ?.schedule

        if (nextAlarm == null) {
            cancelScheduledWork(routines)
        } else {
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

    private suspend fun alarmCandidateFor(
        routine: Routine,
        now: ZonedDateTime,
        nowEpochMillis: Long,
        previousSchedule: DepartureAlarmSchedule?,
        firedSchedule: DepartureAlarmSchedule?,
    ): AlarmCandidate? {
        val baseEstimate = routeEstimateFor(
            routine = routine,
            schedule = null,
        )
        val excludedArrivalAtEpochMillis = firedSchedule
            ?.takeIf { it.routineId == routine.id }
            ?.targetArrivalAtEpochMillis
        val baseSchedule = planner.nextAlarmForRoutine(
            routine = routine,
            routeDurationMinutes = baseEstimate.estimatedMinutes,
            now = now,
            excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
        ) ?: return null
        val routeEstimate = if (baseSchedule.shouldApplyRealtime(now)) {
            routeEstimateFor(
                routine = routine,
                schedule = baseSchedule,
            )
        } else {
            baseEstimate
        }
        val proposedSchedule = if (routeEstimate === baseEstimate) {
            baseSchedule
        } else {
            planner.nextAlarmForRoutine(
                routine = routine,
                routeDurationMinutes = routeEstimate.estimatedMinutes,
                now = now,
                excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
            ) ?: return null
        }

        val adjustedSchedule = adjustmentPolicy.adjust(
            previousSchedule = previousSchedule?.takeIf { it.routineId == routine.id },
            proposedSchedule = proposedSchedule,
        ).applyBoardingSafeDeparture(
            boardingAdvice = routeEstimate.boardingAdvice,
            nowEpochMillis = nowEpochMillis,
            zoneId = now.zone,
        )

        return AlarmCandidate(schedule = adjustedSchedule)
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

    private data class AlarmCandidate(
        val schedule: DepartureAlarmSchedule,
    )

    private fun DepartureAlarmSchedule.shouldApplyRealtime(now: ZonedDateTime): Boolean {
        val minutesUntilDeparture = (triggerAtEpochMillis - now.toInstant().toEpochMilli()) / MILLIS_PER_MINUTE
        return minutesUntilDeparture in 0..REALTIME_LOOKAHEAD_MINUTES
    }

    private fun TransportMode.fallbackRouteDurationMinutes(): Int {
        return when (this) {
            TransportMode.TRANSIT -> 42
            TransportMode.WALK -> 25
            TransportMode.CAR -> 30
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val REALTIME_LOOKAHEAD_MINUTES = 30L
        const val PREDEPARTURE_STATUS_WINDOW_MILLIS = 30 * 60 * 1000L
    }
}
