package com.mapmate.data.alarm

import android.content.Context
import androidx.work.Constraints
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

    override fun schedule(schedule: DepartureAlarmSchedule) {
        val recheckAtEpochMillis = schedule.triggerAtEpochMillis - RECHECK_LEAD_TIME_MILLIS
        val delayMillis = recheckAtEpochMillis - nowEpochMillis()

        if (delayMillis < MIN_RECHECK_DELAY_MILLIS) {
            cancel()
            return
        }

        val request = OneTimeWorkRequestBuilder<DepartureRecheckWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "departure_route_recheck"
        const val WORK_TAG = "departure_route_recheck"
        const val RECHECK_LEAD_TIME_MINUTES = 30L
        const val MIN_RECHECK_DELAY_MINUTES = 1L
        const val RECHECK_LEAD_TIME_MILLIS = RECHECK_LEAD_TIME_MINUTES * 60 * 1000
        private const val MIN_RECHECK_DELAY_MILLIS = MIN_RECHECK_DELAY_MINUTES * 60 * 1000
    }
}
