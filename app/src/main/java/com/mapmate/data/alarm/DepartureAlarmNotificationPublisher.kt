package com.mapmate.data.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.mapmate.MainActivity
import com.mapmate.R

class DepartureAlarmNotificationPublisher(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

    fun show(
        routineId: Long,
        routineName: String,
        destinationName: String,
        recommendedDepartureTime: String,
        targetArrivalTime: String,
        routeDurationMinutes: Int,
    ) {
        if (!canPostNotifications()) return

        ensureNotificationChannel()
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mapmate_notifications)
            .setContentTitle("${routineName} 출발할 시간이에요")
            .setContentText("${targetArrivalTime}까지 ${destinationName}에 도착하려면 지금 출발하세요.")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "권장 출발 시각은 ${recommendedDepartureTime}입니다. " +
                        "예상 이동 시간 ${routeDurationMinutes}분을 기준으로 계산했어요.",
                ),
            )
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        notificationManager.notify(routineId.toNotificationId(), notification)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            applicationContext,
            CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun Long.toNotificationId(): Int {
        return (this % Int.MAX_VALUE).toInt().takeIf { it > 0 } ?: DEFAULT_NOTIFICATION_ID
    }

    private companion object {
        const val CHANNEL_ID = "departure_alarm"
        const val CHANNEL_NAME = "출발 알림"
        const val CHANNEL_DESCRIPTION = "저장된 루틴의 권장 출발 시각 알림"
        const val CONTENT_REQUEST_CODE = 1002
        const val DEFAULT_NOTIFICATION_ID = 1003
    }
}
