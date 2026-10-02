package com.mapmate.domain.alarm

import com.mapmate.domain.model.Routine
import java.security.MessageDigest

interface DepartureScheduleStore {
    suspend fun read(): DepartureScheduleState
    suspend fun setSchedule(schedule: DepartureAlarmSchedule?, routineFingerprint: String? = null)
    suspend fun claimNotification(schedule: DepartureAlarmSchedule): Boolean
    suspend fun releaseNotificationClaim(schedule: DepartureAlarmSchedule)
}

data class DepartureEvent(val routineId: Long, val targetArrivalAtEpochMillis: Long)

data class DepartureScheduleState(
    val schedule: DepartureAlarmSchedule? = null,
    val routineFingerprint: String? = null,
    val notifiedEvents: List<DepartureEvent> = emptyList(),
)

fun DepartureAlarmSchedule.eventOrNull(): DepartureEvent? =
    targetArrivalAtEpochMillis?.takeIf { it > 0L }?.let { DepartureEvent(routineId, it) }

fun routineScheduleFingerprint(routine: Routine): String {
    val fields = listOf(
        routine.id.toString(), routine.name,
        routine.origin.name, routine.origin.address,
        routine.origin.latitude.toString(), routine.origin.longitude.toString(),
        routine.destination.name, routine.destination.address,
        routine.destination.latitude.toString(), routine.destination.longitude.toString(),
        routine.targetArrivalTime.toString(),
        routine.repeatDays.map { it.name }.sorted().joinToString(","),
        routine.transportMode.name,
        routine.personalBufferMinutes.toString(), routine.safetyMarginMinutes.toString(),
    )
    // Length-prefix user strings so delimiters in place/routine names cannot alias a revision.
    val bytes = fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
