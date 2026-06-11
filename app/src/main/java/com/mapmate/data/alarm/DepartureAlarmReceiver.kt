package com.mapmate.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mapmate.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DepartureAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEPARTURE_ALARM) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                publishNotification(context, intent)
                AppContainer(context).departureAlarmCoordinator.rescheduleNextAlarm()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun publishNotification(
        context: Context,
        intent: Intent,
    ) {
        val routineName = intent.getStringExtra(EXTRA_ROUTINE_NAME) ?: return
        val destinationName = intent.getStringExtra(EXTRA_DESTINATION_NAME) ?: return
        val recommendedDepartureTime = intent.getStringExtra(EXTRA_RECOMMENDED_DEPARTURE_TIME) ?: return
        val targetArrivalTime = intent.getStringExtra(EXTRA_TARGET_ARRIVAL_TIME) ?: return
        val routineId = intent.getLongExtra(EXTRA_ROUTINE_ID, DEFAULT_ROUTINE_ID)
        val routeDurationMinutes = intent.getIntExtra(EXTRA_ROUTE_DURATION_MINUTES, 0)

        DepartureAlarmNotificationPublisher(context).show(
            routineId = routineId,
            routineName = routineName,
            destinationName = destinationName,
            recommendedDepartureTime = recommendedDepartureTime,
            targetArrivalTime = targetArrivalTime,
            routeDurationMinutes = routeDurationMinutes,
        )
    }

    companion object {
        const val ACTION_DEPARTURE_ALARM = "com.mapmate.action.DEPARTURE_ALARM"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_ROUTINE_NAME = "routine_name"
        const val EXTRA_DESTINATION_NAME = "destination_name"
        const val EXTRA_RECOMMENDED_DEPARTURE_TIME = "recommended_departure_time"
        const val EXTRA_TARGET_ARRIVAL_TIME = "target_arrival_time"
        const val EXTRA_ROUTE_DURATION_MINUTES = "route_duration_minutes"

        private const val DEFAULT_ROUTINE_ID = 0L
    }
}
