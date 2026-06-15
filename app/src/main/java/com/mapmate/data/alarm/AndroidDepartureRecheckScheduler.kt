package com.mapmate.data.alarm

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import com.mapmate.domain.alarm.DepartureRecheckScheduler
import java.util.concurrent.TimeUnit

class AndroidDepartureRecheckScheduler(
    context: Context,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : DepartureRecheckScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(
        schedule: DepartureAlarmSchedule,
        replaceExisting: Boolean,
    ) {
        // A running recheck worker also calls schedule(); broad tag cancellation would stop itself.
        if (replaceExisting) {
            cancel()
        }
        val now = nowEpochMillis()

        recheckOffsetsFor(schedule.routeDurationMinutes).forEach { offsetMinutes ->
            val recheckAtEpochMillis = schedule.triggerAtEpochMillis - offsetMinutes * MILLIS_PER_MINUTE
            val delayMillis = recheckAtEpochMillis - now
            if (delayMillis < MIN_RECHECK_DELAY_MILLIS) return@forEach

            val request = OneTimeWorkRequestBuilder<DepartureRecheckWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraints)
                .setInputData(schedule.toInputData())
                .addTag(WORK_TAG)
                .addTag(routineTag(schedule.routineId))
                .build()

            workManager.enqueueUniqueWork(
                uniqueWorkName(
                    schedule = schedule,
                    offsetMinutes = offsetMinutes,
                ),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    override fun cancel() {
        workManager.cancelAllWorkByTag(WORK_TAG)
    }

    private val networkConstraints: Constraints
        get() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private fun recheckOffsetsFor(routeDurationMinutes: Int): List<Long> {
        return when {
            routeDurationMinutes >= 90 -> listOf(120, 90, 60, 30, 15, 5)
            routeDurationMinutes >= 60 -> listOf(90, 60, 30, 15, 5)
            else -> listOf(60, 30, 15, 5)
        }
    }

    private fun uniqueWorkName(
        schedule: DepartureAlarmSchedule,
        offsetMinutes: Long,
    ): String {
        return "departure_recheck_${schedule.routineId}_${schedule.triggerAtEpochMillis}_${offsetMinutes}"
    }

    private fun routineTag(routineId: Long): String {
        return "departure_recheck_routine_$routineId"
    }

    private fun DepartureAlarmSchedule.toInputData(): Data {
        return Data.Builder()
            .putLong(DepartureRecheckWorker.KEY_ROUTINE_ID, routineId)
            .putString(DepartureRecheckWorker.KEY_ROUTINE_NAME, routineName)
            .putString(DepartureRecheckWorker.KEY_DESTINATION_NAME, destinationName)
            .putString(DepartureRecheckWorker.KEY_TARGET_ARRIVAL_TIME, targetArrivalTime.toString())
            .putString(DepartureRecheckWorker.KEY_RECOMMENDED_DEPARTURE_TIME, recommendedDepartureTime.toString())
            .putInt(DepartureRecheckWorker.KEY_ROUTE_DURATION_MINUTES, routeDurationMinutes)
            .putLong(DepartureRecheckWorker.KEY_TRIGGER_AT_EPOCH_MILLIS, triggerAtEpochMillis)
            .apply {
                targetArrivalAtEpochMillis?.let {
                    putLong(DepartureRecheckWorker.KEY_TARGET_ARRIVAL_AT_EPOCH_MILLIS, it)
                }
            }
            .build()
    }

    companion object {
        const val WORK_TAG = "departure_route_recheck"
        const val MIN_RECHECK_DELAY_MINUTES = 1L
        private const val MILLIS_PER_MINUTE = 60 * 1000L
        private const val MIN_RECHECK_DELAY_MILLIS = MIN_RECHECK_DELAY_MINUTES * MILLIS_PER_MINUTE
    }
}
