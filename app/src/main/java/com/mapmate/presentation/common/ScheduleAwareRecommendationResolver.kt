package com.mapmate.presentation.common

import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.applyBoardingSafeDeparture
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import java.time.ZonedDateTime

class ScheduleAwareRecommendationResolver(
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy = DepartureAdjustmentPolicy(),
) {
    suspend fun resolve(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long? = null,
    ): ScheduleAwareRecommendation {
        val baseRouteEstimate = routeEstimateProvider.getRouteEstimate(
            origin = routine.origin,
            destination = routine.destination,
            transportMode = routine.transportMode,
            routineId = routine.id,
        )
        val scheduleRoutine = routine.withSchedulableId()
        val baseSchedule = alarmPlanner.nextAlarmForRoutine(
            routine = scheduleRoutine,
            routeDurationMinutes = baseRouteEstimate.estimatedMinutes,
            now = now,
            excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
        )
        val routeEstimate = if (baseSchedule?.shouldApplyRealtime(now) == true) {
            routeEstimateProvider.getRouteEstimate(
                origin = routine.origin,
                destination = routine.destination,
                transportMode = routine.transportMode,
                routineId = routine.id,
                scheduledDepartureEpochMillis = baseSchedule.triggerAtEpochMillis,
                targetArrivalEpochMillis = baseSchedule.targetArrivalAtEpochMillis,
            )
        } else {
            baseRouteEstimate
        }
        val finalSchedule = alarmPlanner.nextAlarmForRoutine(
            routine = scheduleRoutine,
            routeDurationMinutes = routeEstimate.estimatedMinutes,
            now = now,
            excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
        )
        val displaySchedule = finalSchedule?.let {
            adjustmentPolicy.adjust(
                previousSchedule = baseSchedule,
                proposedSchedule = it,
            ).applyBoardingSafeDeparture(
                boardingAdvice = routeEstimate.boardingAdvice,
                nowEpochMillis = now.toInstant().toEpochMilli(),
                zoneId = now.zone,
            )
        }
        return ScheduleAwareRecommendation(
            routeEstimate = routeEstimate,
            recommendation = routine.toRecommendationUiModel(
                routeEstimate = routeEstimate,
                departureTimeCalculator = departureTimeCalculator,
                now = now.toLocalTime(),
                recommendedDepartureAtEpochMillis = displaySchedule?.triggerAtEpochMillis,
                displayedDepartureTime = displaySchedule?.recommendedDepartureTime,
                isImmediateDepartureOverride = displaySchedule?.triggerAtEpochMillis
                    ?.let { it <= now.toInstant().toEpochMilli() }
                    ?: false,
            ),
        )
    }

    fun fallback(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long? = null,
    ): ScheduleAwareRecommendation {
        val routeEstimate = fallbackRouteEstimate(routine)
        val scheduleRoutine = routine.withSchedulableId()
        val fallbackSchedule = alarmPlanner.nextAlarmForRoutine(
            routine = scheduleRoutine,
            routeDurationMinutes = routeEstimate.estimatedMinutes,
            now = now,
            excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
        )
        return ScheduleAwareRecommendation(
            routeEstimate = routeEstimate,
            recommendation = routine.toRecommendationUiModel(
                routeEstimate = routeEstimate,
                departureTimeCalculator = departureTimeCalculator,
                now = now.toLocalTime(),
                recommendedDepartureAtEpochMillis = fallbackSchedule?.triggerAtEpochMillis,
                displayedDepartureTime = fallbackSchedule?.recommendedDepartureTime,
                isImmediateDepartureOverride = fallbackSchedule?.triggerAtEpochMillis
                    ?.let { it <= now.toInstant().toEpochMilli() }
                    ?: false,
            ),
        )
    }

    private fun DepartureAlarmSchedule.shouldApplyRealtime(now: ZonedDateTime): Boolean {
        val minutesUntilDeparture = (triggerAtEpochMillis - now.toInstant().toEpochMilli()) / MILLIS_PER_MINUTE
        return minutesUntilDeparture in 0..REALTIME_LOOKAHEAD_MINUTES
    }

    private fun Routine.withSchedulableId(): Routine {
        return if (id != null) this else copy(id = TEMP_ROUTINE_ID)
    }

    private fun fallbackRouteEstimate(routine: Routine): RouteEstimate {
        val estimatedMinutes = routine.transportMode.fallbackRouteDurationMinutes()
        return RouteEstimate(
            estimatedMinutes = estimatedMinutes,
            summary = "${routine.origin.name} to ${routine.destination.name} fallback estimate ${estimatedMinutes} min",
            providerName = "MapMateFallback",
            reason = "Fallback route duration was used.",
            isFallbackEstimate = true,
            statusMessage = "Route calculation failed, so a fallback duration was used.",
        )
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
        const val TEMP_ROUTINE_ID = Long.MIN_VALUE
    }
}

data class ScheduleAwareRecommendation(
    val routeEstimate: RouteEstimate,
    val recommendation: RoutineRecommendationUiModel,
)
