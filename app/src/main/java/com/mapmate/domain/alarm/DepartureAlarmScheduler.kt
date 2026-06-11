package com.mapmate.domain.alarm

interface DepartureAlarmScheduler {
    fun canPostDepartureNotifications(): Boolean

    fun schedule(schedule: DepartureAlarmSchedule)

    fun cancel()
}
