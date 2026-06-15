package com.mapmate.domain.alarm

import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.Routine
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class DepartureAlarmPlanner(
    private val departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun nextAlarm(
        routineRouteDurations: List<Pair<Routine, Int>>,
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
        excludedSchedule: DepartureAlarmSchedule? = null,
    ): DepartureAlarmSchedule? {
        return routineRouteDurations
            .mapNotNull { (routine, routeDurationMinutes) ->
                nextAlarmForRoutine(
                    routine = routine,
                    routeDurationMinutes = routeDurationMinutes,
                    now = now,
                    excludedArrivalAtEpochMillis = excludedSchedule
                        ?.takeIf { it.routineId == routine.id }
                        ?.targetArrivalAtEpochMillis,
                )
            }
            .minByOrNull(DepartureAlarmSchedule::triggerAtEpochMillis)
    }

    fun nextAlarmForRoutine(
        routine: Routine,
        routeDurationMinutes: Int,
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
        excludedArrivalAtEpochMillis: Long? = null,
    ): DepartureAlarmSchedule? {
        val routineId = routine.id ?: return null
        val recommendedDepartureTime = departureTimeCalculator.calculate(
            targetArrivalTime = routine.targetArrivalTime,
            routeDurationMinutes = routeDurationMinutes,
            personalBufferMinutes = routine.personalBufferMinutes,
            safetyMarginMinutes = routine.safetyMarginMinutes,
        )
        val nextTrigger = nextTriggerDateTime(
            repeatDays = routine.repeatDays,
            targetArrivalTime = routine.targetArrivalTime,
            recommendedDepartureTime = recommendedDepartureTime,
            now = now,
            excludedArrivalAtEpochMillis = excludedArrivalAtEpochMillis,
        ) ?: return null

        return DepartureAlarmSchedule(
            routineId = routineId,
            routineName = routine.name,
            destinationName = routine.destination.name,
            targetArrivalTime = routine.targetArrivalTime,
            recommendedDepartureTime = nextTrigger.recommendedDepartureTime,
            routeDurationMinutes = routeDurationMinutes,
            triggerAtEpochMillis = nextTrigger.triggerAt.toInstant().toEpochMilli(),
            targetArrivalAtEpochMillis = nextTrigger.arrivalAt.toInstant().toEpochMilli(),
        )
    }

    private fun nextTriggerDateTime(
        repeatDays: Set<RepeatDay>,
        targetArrivalTime: LocalTime,
        recommendedDepartureTime: LocalTime,
        now: ZonedDateTime,
        excludedArrivalAtEpochMillis: Long?,
    ): NextDepartureAlarmTrigger? {
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
                val plannedTriggerAt = departureDate.atTime(recommendedDepartureTime).atZone(now.zone)
                val arrivalAt = arrivalDate.atTime(targetArrivalTime).atZone(now.zone)

                if (plannedTriggerAt.isBefore(now) && !arrivalAt.isBefore(now)) {
                    NextDepartureAlarmTrigger(
                        triggerAt = now,
                        recommendedDepartureTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
                        arrivalAt = arrivalAt,
                    )
                } else {
                    NextDepartureAlarmTrigger(
                        triggerAt = plannedTriggerAt,
                        recommendedDepartureTime = recommendedDepartureTime,
                        arrivalAt = arrivalAt,
                    )
                }
            }
            .filterNot { trigger ->
                excludedArrivalAtEpochMillis != null &&
                    trigger.arrivalAt.toInstant().toEpochMilli() == excludedArrivalAtEpochMillis
            }
            .firstOrNull { trigger -> !trigger.triggerAt.isBefore(now) }
    }

    private fun RepeatDay.toDayOfWeek(): DayOfWeek {
        return DayOfWeek.valueOf(name)
    }

    private data class NextDepartureAlarmTrigger(
        val triggerAt: ZonedDateTime,
        val recommendedDepartureTime: LocalTime,
        val arrivalAt: ZonedDateTime,
    )
}
