package com.mapmate.domain.alarm

import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class DepartureAlarmPlanner(
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun nextAlarm(
        routineRouteDurations: List<Pair<Routine, Int>>,
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
    ): DepartureAlarmSchedule? {
        return routineRouteDurations
            .mapNotNull { (routine, routeDurationMinutes) ->
                nextAlarmForRoutine(
                    routine = routine,
                    routeDurationMinutes = routeDurationMinutes,
                    now = now,
                )
            }
            .minByOrNull(DepartureAlarmSchedule::triggerAtEpochMillis)
    }

    fun nextAlarmForRoutine(
        routine: Routine,
        routeDurationMinutes: Int,
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
    ): DepartureAlarmSchedule? {
        val routineId = routine.id ?: return null
        val recommendedDepartureTime = departureTimeCalculator.calculate(
            targetArrivalTime = routine.targetArrivalTime,
            routeDurationMinutes = routeDurationMinutes,
            personalBufferMinutes = routine.personalBufferMinutes,
            safetyMarginMinutes = routine.safetyMarginMinutes,
        )
        val triggerAt = nextTriggerDateTime(
            repeatDays = routine.repeatDays,
            targetArrivalTime = routine.targetArrivalTime,
            recommendedDepartureTime = recommendedDepartureTime,
            now = now,
        ) ?: return null

        return DepartureAlarmSchedule(
            routineId = routineId,
            routineName = routine.name,
            destinationName = routine.destination.name,
            targetArrivalTime = routine.targetArrivalTime,
            recommendedDepartureTime = recommendedDepartureTime,
            routeDurationMinutes = routeDurationMinutes,
            triggerAtEpochMillis = triggerAt.toInstant().toEpochMilli(),
        )
    }

    private fun nextTriggerDateTime(
        repeatDays: Set<RepeatDay>,
        targetArrivalTime: java.time.LocalTime,
        recommendedDepartureTime: java.time.LocalTime,
        now: ZonedDateTime,
    ): ZonedDateTime? {
        val repeatDayOfWeeks = repeatDays.mapTo(mutableSetOf()) { it.toDayOfWeek() }
        if (repeatDayOfWeeks.isEmpty()) return null

        val today = now.toLocalDate()
        return (0..13).asSequence()
            .map { dayOffset -> today.plusDays(dayOffset.toLong()) }
            .filter { arrivalDate -> arrivalDate.dayOfWeek in repeatDayOfWeeks }
            .map { arrivalDate ->
                val departureDate = if (recommendedDepartureTime > targetArrivalTime) {
                    arrivalDate.minusDays(1)
                } else {
                    arrivalDate
                }
                departureDate.atTime(recommendedDepartureTime).atZone(now.zone)
            }
            .firstOrNull { triggerAt -> triggerAt.isAfter(now) }
    }

    private fun RepeatDay.toDayOfWeek(): DayOfWeek {
        return DayOfWeek.valueOf(name)
    }
}
