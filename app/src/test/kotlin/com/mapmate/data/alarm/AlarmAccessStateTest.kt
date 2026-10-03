package com.mapmate.data.alarm

import com.mapmate.domain.model.AlarmAccessState

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmAccessStateTest {
    @Test fun runtimeOrAppBlockPreventsBothNotificationKinds() {
        listOf(AlarmAccessState(runtimeNotificationGranted = false), AlarmAccessState(appNotificationsEnabled = false))
            .forEach {
                assertFalse(it.canPostDeparture)
                assertFalse(it.canPostStatus)
            }
    }

    @Test fun ChannelBlockIsSpecificAndExactAlarmDenialDoesNotBlockNotifications() {
        assertFalse(AlarmAccessState(departureChannelEnabled = false).canPostDeparture)
        assertTrue(AlarmAccessState(departureChannelEnabled = false).canPostStatus)
        assertFalse(AlarmAccessState(statusChannelEnabled = false).canPostStatus)
        assertTrue(AlarmAccessState(statusChannelEnabled = false).canPostDeparture)
        assertTrue(AlarmAccessState(exactAlarmAllowed = false).canPostDeparture)
    }
}
