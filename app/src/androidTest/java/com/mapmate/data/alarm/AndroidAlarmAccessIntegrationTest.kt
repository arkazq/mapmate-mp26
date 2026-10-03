package com.mapmate.data.alarm

import android.app.PendingIntent
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAlarmAccessIntegrationTest {
    @Test fun notificationAndExactAlarmAccessMatchSystemStateAndSettingsResolve() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val access = AndroidAlarmAccess.read(context)
        val arguments = InstrumentationRegistry.getArguments()
        arguments.getString("expectedExact")?.let { assertEquals(it.toBoolean(), access.exactAlarmAllowed) }
        arguments.getString("expectedNotifications")?.let { assertEquals(it.toBoolean(), access.canPostDeparture) }
        assertNotNull(AndroidAlarmAccess.notificationSettingsIntent(context).resolveActivity(context.packageManager))
        assertNotNull(AndroidAlarmAccess.exactAlarmSettingsIntent(context).resolveActivity(context.packageManager))
    }

    @Test fun schedulingWithEitherExactOrInexactAccessCreatesACancellableSystemAlarm() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scheduler = AndroidDepartureAlarmScheduler(context)
        val expectedExact = AndroidAlarmAccess.read(context).exactAlarmAllowed
        val now = System.currentTimeMillis()
        val schedule = DepartureAlarmSchedule(routineId = 987_654, routineName = "QA", destinationName = "QA",
            recommendedDepartureTime = LocalTime.of(9, 0), targetArrivalTime = LocalTime.of(10, 0),
            routeDurationMinutes = 20, triggerAtEpochMillis = now + 30 * 60_000L,
            targetArrivalAtEpochMillis = now + 60 * 60_000L)
        try {
            scheduler.schedule(schedule)
            val intent = Intent(context, DepartureAlarmReceiver::class.java).setAction(DepartureAlarmReceiver.ACTION_DEPARTURE_ALARM)
            assertNotNull(PendingIntent.getBroadcast(context, 1001, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
            val dump = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
            ).bufferedReader().use { it.readText() }
            val action = DepartureAlarmReceiver.ACTION_DEPARTURE_ALARM
            val lines = dump.lines()
            val alarmIndex = lines.indexOfFirst { it.contains("tag=*walarm*:$action") }
            assertTrue("The app's alarm must be registered with AlarmManager", alarmIndex >= 0)
            val block = lines.subList((alarmIndex - 1).coerceAtLeast(0), (alarmIndex + 6).coerceAtMost(lines.size)).joinToString("\n")
            assertTrue("Alarm window must reflect special access ($expectedExact)",
                if (expectedExact) block.contains("window=0") else !block.contains("window=0"))
        } finally {
            scheduler.cancel()
        }
    }
}
