package com.mapmate.data.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.mapmate.MainActivity
import com.mapmate.R
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.PredepartureStatusNotificationPublisher

class AndroidPredepartureStatusNotificationPublisher(
    context: Context,
) : PredepartureStatusNotificationPublisher {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    private val preferences: SharedPreferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun show(schedule: DepartureAlarmSchedule) {
        if (!canPostNotifications()) return

        ensureNotificationChannel()
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mapmate_notifications)
            .setContentTitle("${schedule.recommendedDepartureTime} 출발 권장")
            .setContentText("목표 도착 ${schedule.targetArrivalTime} · ${schedule.destinationName}")
            .setStyle(
                Notification.BigTextStyle().bigText(
                    "목표 도착 ${schedule.targetArrivalTime} · ${schedule.destinationName}\n" +
                        "출발 전 경로를 다시 확인했어요.",
                ),
            )
            .setContentIntent(contentIntent())
            .setAutoCancel(false)
            .setOngoing(false)
            .setShowWhen(true)
            .build()

        notificationManager.notify(schedule.routineId.toStatusNotificationId(), notification)
        track(schedule.routineId)
    }

    override fun cancel(routineId: Long) {
        notificationManager.cancel(routineId.toStatusNotificationId())
        untrack(routineId)
    }

    override fun cancelAll(routineIds: Collection<Long>) {
        (trackedRoutineIds() + routineIds).forEach(::cancel)
    }

    private fun track(routineId: Long) {
        preferences.edit()
            .putStringSet(ACTIVE_ROUTINE_IDS, (trackedRoutineIds() + routineId).map(Long::toString).toSet())
            .apply()
    }

    private fun untrack(routineId: Long) {
        preferences.edit()
            .putStringSet(ACTIVE_ROUTINE_IDS, (trackedRoutineIds() - routineId).map(Long::toString).toSet())
            .apply()
    }

    private fun trackedRoutineIds(): Set<Long> {
        return preferences.getStringSet(ACTIVE_ROUTINE_IDS, emptySet())
            .orEmpty()
            .mapNotNull(String::toLongOrNull)
            .toSet()
    }

    private fun canPostNotifications(): Boolean {
        return AndroidAlarmAccess.read(applicationContext).canPostStatus
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
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

    private fun Long.toStatusNotificationId(): Int {
        val baseId = (this % STATUS_NOTIFICATION_ID_MODULO).toInt().takeIf { it > 0 } ?: 1
        return STATUS_NOTIFICATION_ID_OFFSET + baseId
    }

    companion object {
        internal const val CHANNEL_ID = "predeparture_status"
        private const val CHANNEL_NAME = "출발 전 상태 알림"
        private const val CHANNEL_DESCRIPTION = "출발 30분 전부터 권장 출발 시각을 알려주는 상태 알림"
        private const val CONTENT_REQUEST_CODE = 1004
        private const val STATUS_NOTIFICATION_ID_OFFSET = 200_000
        private const val STATUS_NOTIFICATION_ID_MODULO = 100_000
        private const val PREFERENCES_NAME = "predeparture_status_notifications"
        private const val ACTIVE_ROUTINE_IDS = "active_routine_ids"
    }
}
