package com.mapmate.domain.alarm

interface DepartureRecheckScheduler {
    fun schedule(
        schedule: DepartureAlarmSchedule,
        replaceExisting: Boolean = true,
    )

    fun cancel()
}
