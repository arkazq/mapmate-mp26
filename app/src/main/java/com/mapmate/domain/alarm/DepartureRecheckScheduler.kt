package com.mapmate.domain.alarm

interface DepartureRecheckScheduler {
    fun schedule(schedule: DepartureAlarmSchedule)

    fun cancel()
}
