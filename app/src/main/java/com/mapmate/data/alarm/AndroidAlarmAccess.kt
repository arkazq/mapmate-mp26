package com.mapmate.data.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.mapmate.domain.model.AlarmAccessState
import com.mapmate.domain.provider.AlarmAccessProvider
import com.mapmate.domain.provider.AlarmSettingsDestination

class AndroidAlarmAccessProvider(context: Context) : AlarmAccessProvider {
    private val context = context.applicationContext
    override fun read() = AndroidAlarmAccess.read(context)
    override fun openSettings(destination: AlarmSettingsDestination): Boolean = runCatching {
        val intent = when (destination) {
            AlarmSettingsDestination.NOTIFICATIONS -> AndroidAlarmAccess.notificationSettingsIntent(context)
            AlarmSettingsDestination.EXACT_ALARM -> AndroidAlarmAccess.exactAlarmSettingsIntent(context)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

object AndroidAlarmAccess {
    fun read(context: Context): AlarmAccessState {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return AlarmAccessState(
            runtimeNotificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            appNotificationsEnabled = notifications.areNotificationsEnabled(),
            departureChannelEnabled = notifications.getNotificationChannel(DepartureAlarmNotificationPublisher.CHANNEL_ID)
                ?.importance != NotificationManager.IMPORTANCE_NONE,
            statusChannelEnabled = notifications.getNotificationChannel(AndroidPredepartureStatusNotificationPublisher.CHANNEL_ID)
                ?.importance != NotificationManager.IMPORTANCE_NONE,
            exactAlarmAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        )
    }

    fun notificationSettingsIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarmSettingsIntent(context: Context): Intent = Intent(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    ).setData(Uri.parse("package:${context.packageName}"))
}
