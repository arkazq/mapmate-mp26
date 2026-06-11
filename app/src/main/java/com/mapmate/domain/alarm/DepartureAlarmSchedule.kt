package com.mapmate.domain.alarm

import java.time.LocalTime

data class DepartureAlarmSchedule(
    val routineId: Long,
    val routineName: String,
    val destinationName: String,
    val targetArrivalTime: LocalTime,
    val recommendedDepartureTime: LocalTime,
    val routeDurationMinutes: Int,
    val triggerAtEpochMillis: Long,
)
