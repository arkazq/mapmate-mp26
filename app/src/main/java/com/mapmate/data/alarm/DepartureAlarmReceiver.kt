package com.mapmate.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mapmate.di.AppContainer
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
                AppContainer(context).departureAlarmCoordinator.rescheduleAfterAlarmFired(
                    firedSchedule = intent.toDepartureAlarmScheduleOrNull(),
                )
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
            ?: inferTargetArrivalAtEpochMillis(
                targetArrivalTime = targetArrivalTime,
                recommendedDepartureTime = recommendedDepartureTime,
            )

        return DepartureAlarmSchedule(
            routineId = routineId,
            routineName = routineName,
            destinationName = destinationName,
            targetArrivalTime = targetArrivalTime,
            recommendedDepartureTime = recommendedDepartureTime,
            routeDurationMinutes = routeDurationMinutes,
            triggerAtEpochMillis = System.currentTimeMillis(),
            targetArrivalAtEpochMillis = targetArrivalAtEpochMillis,
        )
    }

    private fun String.toLocalTimeOrNull(): LocalTime? {
        return runCatching { LocalTime.parse(this) }.getOrNull()
    }

    private fun inferTargetArrivalAtEpochMillis(
        targetArrivalTime: LocalTime,
        recommendedDepartureTime: LocalTime,
    ): Long {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val arrivalDate = if (
            recommendedDepartureTime > targetArrivalTime &&
            now.toLocalTime() >= recommendedDepartureTime
        ) {
            now.toLocalDate().plusDays(1)
        } else {
            now.toLocalDate()
        }
        return arrivalDate.atTime(targetArrivalTime)
            .atZone(now.zone)
            .toInstant()
            .toEpochMilli()
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

        private const val DEFAULT_ROUTINE_ID = 0L
        private const val MISSING_LONG = Long.MIN_VALUE
        private const val MISSING_INT = Int.MIN_VALUE
    }
}
