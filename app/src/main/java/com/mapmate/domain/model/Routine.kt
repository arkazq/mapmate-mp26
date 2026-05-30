package com.mapmate.domain.model

import java.time.LocalTime

data class Routine(
    val id: Long? = null,
    val name: String,
    val destination: Destination,
    val targetArrivalTime: LocalTime,
    val repeatDays: Set<RepeatDay>,
    val transportMode: TransportMode,
    val personalBufferMinutes: Int,
    val safetyMarginMinutes: Int,
)
