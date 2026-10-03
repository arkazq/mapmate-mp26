package com.mapmate.presentation.common

import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.calculator.ScheduledRouteCalculator
import com.mapmate.domain.model.Routine
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.provider.ScheduledRouteRecommendation
import com.mapmate.domain.model.RouteEstimate
import java.time.ZonedDateTime

class ScheduleAwareRecommendationResolver(
    private val routeEstimateProvider: RouteEstimateProvider,
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val alarmPlanner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    adjustmentPolicy: DepartureAdjustmentPolicy? = null,
    private val scheduledRouteProvider: ScheduledRouteProvider? = null,
) {
    private val calculator = ScheduledRouteCalculator(routeEstimateProvider, alarmPlanner, adjustmentPolicy)
    suspend fun resolve(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long? = null,
        excludedArrivalEvents: Set<Long> = emptySet(),
    ): ScheduleAwareRecommendation {
        return (scheduledRouteProvider ?: calculator).resolve(routine, now,
            excludedArrivalAtEpochMillis, excludedArrivalEvents).toUi(routine, now)
    }

    fun fallback(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long? = null,
        excludedArrivalEvents: Set<Long> = emptySet(),
    ): ScheduleAwareRecommendation {
        return calculator.fallback(routine, now, excludedArrivalAtEpochMillis, excludedArrivalEvents).toUi(routine, now)
    }

    private fun ScheduledRouteRecommendation.toUi(routine: Routine, now: ZonedDateTime): ScheduleAwareRecommendation {
        return ScheduleAwareRecommendation(
            routeEstimate = routeEstimate,
            recommendation = routine.toRecommendationUiModel(
                routeEstimate = routeEstimate,
                departureTimeCalculator = departureTimeCalculator,
                now = now.toLocalTime(),
                recommendedDepartureAtEpochMillis = schedule?.triggerAtEpochMillis,
                targetArrivalAtEpochMillis = schedule?.targetArrivalAtEpochMillis,
                displayedDepartureTime = schedule?.recommendedDepartureTime,
                isImmediateDepartureOverride = schedule?.triggerAtEpochMillis
                    ?.let { it <= now.toInstant().toEpochMilli() }
                    ?: false,
            ),
        )
    }

}

data class ScheduleAwareRecommendation(
    val routeEstimate: RouteEstimate,
    val recommendation: RoutineRecommendationUiModel,
)
