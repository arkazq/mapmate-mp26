package com.mapmate.data.alarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mapmate.di.AppContainer
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import java.time.LocalTime
import com.mapmate.domain.util.runCatchingCancellable

class DepartureRecheckWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        return runCatchingCancellable {
            val coordinator = AppContainer.getInstance(applicationContext).departureAlarmCoordinator
            if (inputData.getBoolean(KEY_RESCHEDULE_ONLY, false)) {
                coordinator.rescheduleNextAlarm()
            } else {
                val schedule = inputData.toDepartureAlarmScheduleOrNull() ?: return Result.success()
                coordinator.recheckScheduledAlarm(schedule)
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    private fun androidx.work.Data.toDepartureAlarmScheduleOrNull(): DepartureAlarmSchedule? {
        val routineId = getLong(KEY_ROUTINE_ID, MISSING_LONG).takeIf { it != MISSING_LONG } ?: return null
        val routineName = getString(KEY_ROUTINE_NAME) ?: return null
        val destinationName = getString(KEY_DESTINATION_NAME) ?: return null
        val targetArrivalTime = getString(KEY_TARGET_ARRIVAL_TIME)?.toLocalTimeOrNull() ?: return null
        val recommendedDepartureTime = getString(KEY_RECOMMENDED_DEPARTURE_TIME)?.toLocalTimeOrNull() ?: return null
        val routeDurationMinutes = getInt(KEY_ROUTE_DURATION_MINUTES, MISSING_INT)
            .takeIf { it != MISSING_INT }
            ?: return null
        val triggerAtEpochMillis = getLong(KEY_TRIGGER_AT_EPOCH_MILLIS, MISSING_LONG)
            .takeIf { it != MISSING_LONG }
            ?: return null
        val targetArrivalAtEpochMillis = getLong(KEY_TARGET_ARRIVAL_AT_EPOCH_MILLIS, MISSING_LONG)
            .takeIf { it != MISSING_LONG }

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
        private const val KEY_RESCHEDULE_ONLY = "reschedule_only"
        fun enqueueReschedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<DepartureRecheckWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_RESCHEDULE_ONLY, true).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "departure_alarm_reschedule", ExistingWorkPolicy.REPLACE, request,
            )
        }
        const val KEY_ROUTINE_ID = "routine_id"
        const val KEY_ROUTINE_NAME = "routine_name"
        const val KEY_DESTINATION_NAME = "destination_name"
        const val KEY_TARGET_ARRIVAL_TIME = "target_arrival_time"
        const val KEY_RECOMMENDED_DEPARTURE_TIME = "recommended_departure_time"
        const val KEY_ROUTE_DURATION_MINUTES = "route_duration_minutes"
        const val KEY_TRIGGER_AT_EPOCH_MILLIS = "trigger_at_epoch_millis"
        const val KEY_TARGET_ARRIVAL_AT_EPOCH_MILLIS = "target_arrival_at_epoch_millis"
        private const val MISSING_LONG = Long.MIN_VALUE
        private const val MISSING_INT = Int.MIN_VALUE
    }
}
