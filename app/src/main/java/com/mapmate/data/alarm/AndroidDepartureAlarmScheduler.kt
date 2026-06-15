package com.mapmate.data.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureAlarmScheduler

class AndroidDepartureAlarmScheduler(
    context: Context,
) : DepartureAlarmScheduler {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    override fun canPostDepartureNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    override fun schedule(schedule: DepartureAlarmSchedule) {
        val operation = departureAlarmPendingIntent(schedule)
        val triggerAtMillis = schedule.triggerAtEpochMillis

        val canScheduleExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)

        if (canScheduleExactAlarm) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation,
                )
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation,
                )
            }
            return
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            operation,
        )
    }

    override fun cancel() {
        val operation = existingPendingIntent() ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun departureAlarmPendingIntent(schedule: DepartureAlarmSchedule): PendingIntent {
        val intent = baseIntent()
            .putExtra(DepartureAlarmReceiver.EXTRA_ROUTINE_ID, schedule.routineId)
            .putExtra(DepartureAlarmReceiver.EXTRA_ROUTINE_NAME, schedule.routineName)
            .putExtra(DepartureAlarmReceiver.EXTRA_DESTINATION_NAME, schedule.destinationName)
            .putExtra(
                DepartureAlarmReceiver.EXTRA_RECOMMENDED_DEPARTURE_TIME,
                schedule.recommendedDepartureTime.toString(),
            )
            .putExtra(
                DepartureAlarmReceiver.EXTRA_TARGET_ARRIVAL_TIME,
                schedule.targetArrivalTime.toString(),
            )
            .putExtra(DepartureAlarmReceiver.EXTRA_ROUTE_DURATION_MINUTES, schedule.routeDurationMinutes)
            .apply {
                schedule.targetArrivalAtEpochMillis?.let {
                    putExtra(DepartureAlarmReceiver.EXTRA_TARGET_ARRIVAL_AT_EPOCH_MILLIS, it)
                }
            }

        return PendingIntent.getBroadcast(
            applicationContext,
            DEPARTURE_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingPendingIntent(): PendingIntent? {
        return PendingIntent.getBroadcast(
            applicationContext,
            DEPARTURE_ALARM_REQUEST_CODE,
            baseIntent(),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun baseIntent(): Intent {
        return Intent(applicationContext, DepartureAlarmReceiver::class.java)
            .setAction(DepartureAlarmReceiver.ACTION_DEPARTURE_ALARM)
    }

    private companion object {
        const val DEPARTURE_ALARM_REQUEST_CODE = 1001
    }
}
