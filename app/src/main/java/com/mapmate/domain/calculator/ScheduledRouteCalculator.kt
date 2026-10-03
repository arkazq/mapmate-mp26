package com.mapmate.domain.calculator

import com.mapmate.domain.alarm.DepartureAdjustmentPolicy
import com.mapmate.domain.alarm.DepartureAlarmPlanner
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.applyBoardingSafeDeparture
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import com.mapmate.domain.provider.RouteEstimateProvider
import com.mapmate.domain.provider.ScheduledRouteProvider
import com.mapmate.domain.provider.ScheduledRouteRecommendation
import com.mapmate.domain.util.runCatchingCancellable
import java.time.ZonedDateTime

class ScheduledRouteCalculator(
    private val routeEstimateProvider: RouteEstimateProvider,
    private val planner: DepartureAlarmPlanner = DepartureAlarmPlanner(),
    private val adjustmentPolicy: DepartureAdjustmentPolicy? = null,
) : ScheduledRouteProvider {
    override suspend fun resolve(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long?,
        excludedArrivalEvents: Set<Long>,
    ): ScheduledRouteRecommendation = calculate(routine, now,
        excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
        excludedArrivalEvents = excludedArrivalEvents)

    suspend fun calculate(
        routine: Routine,
        now: ZonedDateTime,
        previousSchedule: DepartureAlarmSchedule? = null,
        excludedArrivalAtEpochMillis: Long? = null,
        excludedArrivalEvents: Set<Long> = emptySet(),
    ): ScheduledRouteRecommendation {
        val schedulable = if (routine.id == null) routine.copy(id = Long.MIN_VALUE) else routine
        suspend fun estimate(schedule: DepartureAlarmSchedule?): RouteEstimate = runCatchingCancellable {
            routeEstimateProvider.getRouteEstimate(routine.origin, routine.destination, routine.transportMode,
                routine.id, schedule?.triggerAtEpochMillis, schedule?.targetArrivalAtEpochMillis)
        }.getOrElse { fallbackEstimate(routine) }
        fun plan(estimate: RouteEstimate) = planner.nextAlarmForRoutine(schedulable, estimate.estimatedMinutes,
            now, excludedArrivalAtEpochMillis, excludedArrivalEvents)

        val base = estimate(null)
        val baseSchedule = plan(base) ?: return ScheduledRouteRecommendation(base, null)
        val previous = previousSchedule?.takeIf {
            it.routineId == schedulable.id && it.targetArrivalAtEpochMillis == baseSchedule.targetArrivalAtEpochMillis
        }
        val querySchedule = previous ?: baseSchedule
        val route = if (isWithinRealtimeDepartureWindow(querySchedule.triggerAtEpochMillis, now.toInstant().toEpochMilli())) {
            estimate(querySchedule)
        } else base
        val proposed = plan(route) ?: return ScheduledRouteRecommendation(route, null)
        val policy = adjustmentPolicy ?: DepartureAdjustmentPolicy({ now.toInstant().toEpochMilli() }, now.zone)
        val schedule = policy.adjust(previous ?: baseSchedule, proposed)
            .applyBoardingSafeDeparture(route.boardingAdvice, now.toInstant().toEpochMilli(), now.zone)
        return ScheduledRouteRecommendation(route, schedule)
    }

    fun fallback(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long? = null,
        excludedArrivalEvents: Set<Long> = emptySet(),
    ): ScheduledRouteRecommendation {
        val route = fallbackEstimate(routine)
        val schedulable = if (routine.id == null) routine.copy(id = Long.MIN_VALUE) else routine
        return ScheduledRouteRecommendation(route, planner.nextAlarmForRoutine(schedulable,
            route.estimatedMinutes, now, excludedArrivalAtEpochMillis, excludedArrivalEvents))
    }

    private fun fallbackEstimate(routine: Routine): RouteEstimate {
        val minutes = when (routine.transportMode) {
            TransportMode.TRANSIT -> 42
            TransportMode.WALK -> 25
            TransportMode.CAR -> 30
        }
        return RouteEstimate(minutes, "${routine.origin.name} → ${routine.destination.name} · 약 ${minutes}분",
            "MapMateFallback", "경로를 확인하지 못해 기본 예상 시간을 사용했습니다.",
            isFallbackEstimate = true,
            statusMessage = "경로 정보를 불러오지 못했어요. 네트워크 연결을 확인한 뒤 다시 시도해 주세요.")
    }
}
