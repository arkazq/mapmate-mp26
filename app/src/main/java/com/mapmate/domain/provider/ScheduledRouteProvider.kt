package com.mapmate.domain.provider

import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface ScheduledRouteProvider {
    val revisions: Flow<Long> get() = emptyFlow()
    suspend fun invalidate() = Unit

    suspend fun resolve(
        routine: Routine,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long? = null,
        excludedArrivalEvents: Set<Long> = emptySet(),
    ): ScheduledRouteRecommendation
}

data class ScheduledRouteRecommendation(
    val routeEstimate: RouteEstimate,
    val schedule: DepartureAlarmSchedule?,
)
