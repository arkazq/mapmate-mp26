package com.mapmate.presentation.common

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.Routine
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

internal fun List<CommuteRecord>.completedArrivalEventToExclude(
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

internal fun List<CommuteRecord>.hasCompletedCommuteToday(
    now: ZonedDateTime,
): Boolean {
    val today = now.toLocalDate()
    return any { record ->
        record.completedTargetArrivalAt(now)
            .toLocalDate() == today
    }
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
