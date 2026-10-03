package com.mapmate.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmate.domain.model.TrackingSession
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreTrackingSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun session() = TrackingSession(
        8_888L, "revision", 1_800_000L, 900_000L, 20, "walk and bus", 1_000L,
        listOf(RouteSegment(routineId = 8_888L, segmentIndex = 0, segmentType = RouteSegmentType.BUS_RIDE,
            plannedDurationMinutes = 20, actualStartedAtEpochMillis = 1_000L, status = RouteSegmentStatus.IN_PROGRESS)),
    )

    @Test
    fun sessionSurvivesStoreRecreationAndIsRemovedOnlyOnExplicitClear() = runTest {
        val first = DataStoreTrackingSessionStore(context)
        val value = session()
        first.clear(value.routineId)
        try {
            first.save(value)
            val second = DataStoreTrackingSessionStore(context)
            assertEquals(value, second.read(value.routineId))
            second.clear(value.routineId)
            assertNull(first.read(value.routineId))
        } finally { first.clear(value.routineId) }
    }

    @Test
    fun invalidCompletedTimingCannotOverwriteValidSession() = runTest {
        val store = DataStoreTrackingSessionStore(context)
        val value = session()
        try {
            store.save(value)
            val invalid = value.copy(routeSegments = value.routeSegments.map { it.copy(
                status = RouteSegmentStatus.COMPLETED, actualEndedAtEpochMillis = 0L, actualDurationMinutes = 0,
            ) })
            assertTrue(runCatching { store.save(invalid) }.isFailure)
            assertEquals(value, store.read(value.routineId))
        } finally { store.clear(value.routineId) }
    }
}
