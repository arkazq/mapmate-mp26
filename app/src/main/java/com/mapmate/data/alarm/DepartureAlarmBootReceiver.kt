package com.mapmate.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.os.Build

class DepartureAlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val exactAlarmAccessChanged = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (intent.action !in RESCHEDULE_ACTIONS && !exactAlarmAccessChanged) return
        if (exactAlarmAccessChanged && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) return

        DepartureRecheckWorker.enqueueReschedule(context)
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
