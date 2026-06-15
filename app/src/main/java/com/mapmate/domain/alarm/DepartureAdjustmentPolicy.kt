package com.mapmate.domain.alarm

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DepartureAdjustmentPolicy(
    private val nowProvider: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun adjust(
        previousSchedule: DepartureAlarmSchedule?,
        proposedSchedule: DepartureAlarmSchedule,
    ): DepartureAlarmSchedule {
        val previous = previousSchedule ?: return proposedSchedule
        if (!previous.matchesSameDepartureEvent(proposedSchedule)) return proposedSchedule

        val now = nowProvider()
        val oldDepartureMillis = previous.triggerAtEpochMillis
        val newDepartureMillis = proposedSchedule.triggerAtEpochMillis

        if (oldDepartureMillis <= now) {
            return proposedSchedule
        }

        val diffMinutes = (newDepartureMillis - oldDepartureMillis) / MILLIS_PER_MINUTE

        return when {
            newDepartureMillis <= now + NOTIFY_NOW_WINDOW_MILLIS -> {
                proposedSchedule.withTriggerAt(now)
            }
            diffMinutes <= EARLY_RESCHEDULE_THRESHOLD_MINUTES -> {
                proposedSchedule
            }
            diffMinutes >= LATE_RESCHEDULE_THRESHOLD_MINUTES -> {
                proposedSchedule.withTriggerAt(
                    minOf(
                        newDepartureMillis,
                        oldDepartureMillis + MAX_LATE_SHIFT_MILLIS,
                    ),
                )
            }
            else -> {
                previous
            }
        }
    }

    private fun DepartureAlarmSchedule.withTriggerAt(triggerAtMillis: Long): DepartureAlarmSchedule {
        return copy(
            recommendedDepartureTime = triggerAtMillis.toLocalTime(),
            triggerAtEpochMillis = triggerAtMillis,
        )
    }

    private fun Long.toLocalTime(): LocalTime {
        return Instant.ofEpochMilli(this)
            .atZone(zoneId)
            .toLocalTime()
            .truncatedTo(ChronoUnit.MINUTES)
    }

    private fun DepartureAlarmSchedule.matchesSameDepartureEvent(
        other: DepartureAlarmSchedule,
    ): Boolean {
        if (routineId != other.routineId) return false
        val previousArrivalAt = targetArrivalAtEpochMillis
        val otherArrivalAt = other.targetArrivalAtEpochMillis
        return if (previousArrivalAt != null && otherArrivalAt != null) {
            previousArrivalAt == otherArrivalAt
        } else {
            targetArrivalTime == other.targetArrivalTime
        }
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val NOTIFY_NOW_WINDOW_MILLIS = 5 * MILLIS_PER_MINUTE
        const val EARLY_RESCHEDULE_THRESHOLD_MINUTES = -3L
        const val LATE_RESCHEDULE_THRESHOLD_MINUTES = 5L
        const val MAX_LATE_SHIFT_MILLIS = 10 * MILLIS_PER_MINUTE
    }
}
