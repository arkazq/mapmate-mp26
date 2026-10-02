package com.mapmate.data.preferences

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.eventOrNull
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreDepartureScheduleStoreTest {
    @Test
    fun scheduleAndNotificationClaimPersistAcrossNewRepositoryInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = DataStoreDepartureScheduleStore(context)
        val schedule = schedule()
        first.setSchedule(schedule, "revision-a")
        try {
            val reopened = DataStoreDepartureScheduleStore(context)
            assertEquals(schedule, reopened.read().schedule)
            assertEquals("revision-a", reopened.read().routineFingerprint)
            assertTrue(reopened.claimNotification(schedule))
            assertFalse(first.claimNotification(schedule))
            assertTrue(DataStoreDepartureScheduleStore(context).read().notifiedEvents.contains(schedule.eventOrNull()))
        } finally {
            first.setSchedule(null)
        }
    }

    @Test
    fun concurrentClaimAllowsExactlyOneAndRejectsStaleSchedule() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DataStoreDepartureScheduleStore(context)
        val schedule = schedule()
        store.setSchedule(schedule, "revision-b")
        try {
            assertFalse(store.claimNotification(schedule.copy(triggerAtEpochMillis = schedule.triggerAtEpochMillis - 1)))
            val claimed = (0 until 16).map {
                async { DataStoreDepartureScheduleStore(context).claimNotification(schedule) }
            }.awaitAll()
            assertEquals(1, claimed.count { it })
        } finally {
            store.setSchedule(null)
        }
    }

    private fun schedule(): DepartureAlarmSchedule {
        val trigger = System.currentTimeMillis()
        val time = Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault())
        return DepartureAlarmSchedule(
            routineId = trigger,
            routineName = "Test commute",
            destinationName = "Test destination",
            targetArrivalTime = time.plusMinutes(30).toLocalTime(),
            recommendedDepartureTime = time.toLocalTime(),
            routeDurationMinutes = 30,
            triggerAtEpochMillis = trigger,
            targetArrivalAtEpochMillis = trigger + 30 * 60_000L,
        )
    }
}
