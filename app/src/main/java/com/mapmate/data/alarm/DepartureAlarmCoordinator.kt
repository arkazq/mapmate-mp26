package com.mapmate.data.alarm

import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAlarmScheduler
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.repository.RoutineRepository
import com.mapmate.domain.repository.SettingsRepository
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
    private val planner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
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

    suspend fun rescheduleNextAlarm() {
        sync(
            settings = settingsRepository.settings.first(),
            routines = routineRepository.observeRoutines().first(),
        )
    }

    private suspend fun sync(
        settings: AppSettings,
        routines: List<Routine>,
    ) {
        if (!settings.notificationsEnabled || !alarmScheduler.canPostDepartureNotifications()) {
            cancelScheduledWork()
            return
        }

        val routineRouteDurations = routines.map { routine ->
            routine to routeDurationMinutes(routine)
        }
        val nextAlarm = planner.nextAlarm(routineRouteDurations)

        if (nextAlarm == null) {
            cancelScheduledWork()
        } else {
            alarmScheduler.schedule(nextAlarm)
            recheckScheduler.schedule(nextAlarm)
        }
    }

    private fun cancelScheduledWork() {
        alarmScheduler.cancel()
        recheckScheduler.cancel()
    }

    private suspend fun routeDurationMinutes(routine: Routine): Int {
        return runCatching {
            routeEstimateProvider.getRouteEstimate(
                origin = routine.origin,
                destination = routine.destination,
                transportMode = routine.transportMode,
            ).estimatedMinutes
        }.getOrElse {
            routine.transportMode.fallbackRouteDurationMinutes()
        }
    }

    private fun TransportMode.fallbackRouteDurationMinutes(): Int {
        return when (this) {
            TransportMode.TRANSIT -> 42
            TransportMode.WALK -> 25
            TransportMode.CAR -> 30
        }
    }
}
