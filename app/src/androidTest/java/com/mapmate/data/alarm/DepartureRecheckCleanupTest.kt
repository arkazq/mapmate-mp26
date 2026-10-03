package com.mapmate.data.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mapmate.domain.alarm.DepartureAlarmSchedule
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DepartureRecheckCleanupTest {
    @Test fun rescheduleCancelsLegacyAndOldPendingWorkWithoutAccumulation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workManager = WorkManager.getInstance(context)
        val legacy = OneTimeWorkRequestBuilder<DepartureRecheckWorker>()
            .setInitialDelay(1, TimeUnit.DAYS).build()
        workManager.enqueueUniqueWork(AndroidDepartureRecheckScheduler.WORK_TAG, ExistingWorkPolicy.REPLACE, legacy)
            .result.get(5, TimeUnit.SECONDS)
        val now = System.currentTimeMillis()
        val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val original = DepartureAlarmSchedule(
            999_001L, "Cleanup test", "Destination", time.plusMinutes(75).toLocalTime(),
            time.plusMinutes(45).toLocalTime(), 30, now + 45 * 60_000,
            now + 75 * 60_000,
        )
        val scheduler = AndroidDepartureRecheckScheduler(context) { now }
        try {
            scheduler.schedule(original, replaceExisting = true)
            eventually {
                workManager.getWorkInfoById(legacy.id).get()?.state == WorkInfo.State.CANCELLED && pending(workManager).size == 4
            }
            val previousIds = pending(workManager).map { it.id }.toSet()
            scheduler.schedule(original.copy(triggerAtEpochMillis = original.triggerAtEpochMillis - 2 * 60_000), replaceExisting = false)
            eventually {
                val pending = pending(workManager)
                pending.size == 4 && pending.none { it.id in previousIds }
            }
            assertTrue(previousIds.all { workManager.getWorkInfoById(it).get()?.state == WorkInfo.State.CANCELLED })
        } finally {
            scheduler.cancel()
            eventually { pending(workManager).isEmpty() }
        }
    }

    private fun pending(workManager: WorkManager) = workManager.getWorkInfosByTag(AndroidDepartureRecheckScheduler.WORK_TAG)
        .get(5, TimeUnit.SECONDS).filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    private suspend fun eventually(check: () -> Boolean) {
        repeat(50) {
            if (check()) return
            delay(100)
        }
        fail("WorkManager cleanup did not settle within five seconds")
    }
}
