package com.mapmate.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mapmate.di.AppContainer
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import java.time.LocalTime
import com.mapmate.domain.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DepartureAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEPARTURE_ALARM) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(8_000L) {
                    runCatchingCancellable {
                        val schedule = intent.toDepartureAlarmScheduleOrNull() ?: return@runCatchingCancellable
                        AppContainer.getInstance(context).departureAlarmCoordinator.publishCurrentAlarm(schedule) {
                            publishNotification(context, it)
                        }
                    }
                }
                DepartureRecheckWorker.enqueueReschedule(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun publishNotification(
        context: Context,
        schedule: DepartureAlarmSchedule,
    ) {
        DepartureAlarmNotificationPublisher(context).show(
            routineId = schedule.routineId,
            routineName = schedule.routineName,
            destinationName = schedule.destinationName,
            recommendedDepartureTime = schedule.recommendedDepartureTime.toString(),
            targetArrivalTime = schedule.targetArrivalTime.toString(),
            routeDurationMinutes = schedule.routeDurationMinutes,
        )
    }

    private fun Intent.toDepartureAlarmScheduleOrNull(): DepartureAlarmSchedule? {
        val routineId = getLongExtra(EXTRA_ROUTINE_ID, MISSING_LONG).takeIf { it != MISSING_LONG } ?: return null
        val routineName = getStringExtra(EXTRA_ROUTINE_NAME) ?: return null
        val destinationName = getStringExtra(EXTRA_DESTINATION_NAME) ?: return null
        val targetArrivalTime = getStringExtra(EXTRA_TARGET_ARRIVAL_TIME)?.toLocalTimeOrNull() ?: return null
        val recommendedDepartureTime = getStringExtra(EXTRA_RECOMMENDED_DEPARTURE_TIME)
            ?.toLocalTimeOrNull()
            ?: return null
        val routeDurationMinutes = getIntExtra(EXTRA_ROUTE_DURATION_MINUTES, MISSING_INT)
            .takeIf { it != MISSING_INT }
            ?: return null
        val targetArrivalAtEpochMillis = getLongExtra(EXTRA_TARGET_ARRIVAL_AT_EPOCH_MILLIS, MISSING_LONG)
            .takeIf { it != MISSING_LONG }
            ?: return null
        val triggerAtEpochMillis = getLongExtra(EXTRA_TRIGGER_AT_EPOCH_MILLIS, MISSING_LONG)
            .takeIf { it != MISSING_LONG }
            ?: return null

        return DepartureAlarmSchedule(
            routineId = routineId,
            routineName = routineName,
            destinationName = destinationName,
            targetArrivalTime = targetArrivalTime,
            recommendedDepartureTime = recommendedDepartureTime,
            routeDurationMinutes = routeDurationMinutes,
            triggerAtEpochMillis = triggerAtEpochMillis,
            targetArrivalAtEpochMillis = targetArrivalAtEpochMillis,
        )
    }

    private fun String.toLocalTimeOrNull(): LocalTime? {
        return runCatching { LocalTime.parse(this) }.getOrNull()
    }

    companion object {
        const val ACTION_DEPARTURE_ALARM = "com.mapmate.action.DEPARTURE_ALARM"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_ROUTINE_NAME = "routine_name"
        const val EXTRA_DESTINATION_NAME = "destination_name"
        const val EXTRA_RECOMMENDED_DEPARTURE_TIME = "recommended_departure_time"
        const val EXTRA_TARGET_ARRIVAL_TIME = "target_arrival_time"
        const val EXTRA_ROUTE_DURATION_MINUTES = "route_duration_minutes"
        const val EXTRA_TARGET_ARRIVAL_AT_EPOCH_MILLIS = "target_arrival_at_epoch_millis"
        const val EXTRA_TRIGGER_AT_EPOCH_MILLIS = "trigger_at_epoch_millis"

        private const val MISSING_LONG = Long.MIN_VALUE
        private const val MISSING_INT = Int.MIN_VALUE
    }
}
