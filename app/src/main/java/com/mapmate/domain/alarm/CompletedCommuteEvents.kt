package com.mapmate.domain.alarm

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Routine
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

fun List<CommuteRecord>.completedArrivalEventToExclude(
    routine: Routine,
    now: ZonedDateTime,
): Long? {
    val routineId = routine.id ?: return null
    return asSequence()
        .filter { record -> record.routineId == routineId }
        .sortedByDescending { record -> record.arrivedAtEpochMillis }
        .map { record -> record.completedTargetArrivalAt(now) }
        .firstOrNull { targetArrivalAt ->
            !targetArrivalAt.isBefore(now)
        }
        ?.toInstant()
        ?.toEpochMilli()
}

fun List<CommuteRecord>.hasCompletedCommuteToday(
    now: ZonedDateTime,
): Boolean {
    val today = now.toLocalDate()
    return any { record ->
        record.completedTargetArrivalAt(now)
            .toLocalDate() == today
    }
}

fun List<CommuteRecord>.completedArrivalEventsToExclude(
    routine: Routine,
    now: ZonedDateTime,
): Set<Long> {
    val routineId = routine.id ?: return emptySet()
    return asSequence()
        .filter { it.routineId == routineId }
        .map { it.completedTargetArrivalAt(now) }
        .filter { !it.isBefore(now) }
        .map { it.toInstant().toEpochMilli() }
        .toSet()
}

private fun CommuteRecord.completedTargetArrivalAt(now: ZonedDateTime): ZonedDateTime {
    targetArrivalAtEpochMillis?.let {
        return Instant.ofEpochMilli(it).atZone(now.zone)
    }

    val arrivedAt = Instant.ofEpochMilli(arrivedAtEpochMillis).atZone(now.zone)
    val targetAtArrivedDate = arrivedAt.toLocalDate()
        .atTime(targetArrivalTime)
        .atZone(now.zone)
    return listOf(
        targetAtArrivedDate.minusDays(1),
        targetAtArrivedDate,
        targetAtArrivedDate.plusDays(1),
    ).minBy { candidate -> candidate.absoluteDistanceMillisFrom(arrivedAt) }
}

private fun ZonedDateTime.absoluteDistanceMillisFrom(other: ZonedDateTime): Long {
    val deltaMillis = Duration.between(this, other).toMillis()
    return if (deltaMillis < 0L) -deltaMillis else deltaMillis
}
