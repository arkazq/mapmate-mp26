package com.mapmate.testing

import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureScheduleState
import com.mapmate.domain.alarm.DepartureScheduleStore
import com.mapmate.domain.alarm.eventOrNull

class TestDepartureScheduleStore : DepartureScheduleStore {
    var state = DepartureScheduleState()
    override suspend fun read() = state
    override suspend fun setSchedule(schedule: DepartureAlarmSchedule?, routineFingerprint: String?) {
        state = state.copy(schedule = schedule, routineFingerprint = routineFingerprint)
    }
    override suspend fun claimNotification(schedule: DepartureAlarmSchedule): Boolean {
        val event = schedule.eventOrNull() ?: return false
        if (state.schedule != schedule || event in state.notifiedEvents) return false
        state = state.copy(notifiedEvents = state.notifiedEvents + event)
        return true
    }
    override suspend fun releaseNotificationClaim(schedule: DepartureAlarmSchedule) {
        state = state.copy(notifiedEvents = state.notifiedEvents - listOfNotNull(schedule.eventOrNull()).toSet())
    }
}
