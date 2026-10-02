package com.mapmate.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapmate.data.local.MapMateDatabase
import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCommuteRecordRepositoryTest {
    private lateinit var database: MapMateDatabase
    private lateinit var repository: RoomCommuteRecordRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MapMateDatabase::class.java,
        ).build()
        repository = RoomCommuteRecordRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun savedRecordContainsSegmentsWithPersistedRecordId() = runTest {
        val id = repository.saveRecord(record())
        val saved = requireNotNull(repository.getRecord(id))
        assertEquals(2, saved.routeSegments.size)
        assertTrue(saved.routeSegments.all { it.commuteRecordId == id })
        assertTrue(saved.routeSegments.all { it.id != null })
        assertEquals(saved.routeSegments, repository.getRecentRecords(5, null).single().routeSegments)
    }

    @Test
    fun invalidSecondSegmentRollsBackFirstTimingUpdate() = runTest {
        val id = repository.saveRecord(record())
        val saved = requireNotNull(repository.getRecord(id))
        val changed = saved.routeSegments.first().copy(
            actualEndedAtEpochMillis = 5 * MINUTE,
            actualDurationMinutes = 5,
            isUserEdited = true,
        )
        val invalid = saved.routeSegments.last().copy(id = Long.MAX_VALUE)
        val result = runCatching { repository.updateRouteSegments(id, listOf(changed, invalid)) }
        assertTrue(result.isFailure)
        assertEquals(saved, repository.getRecord(id))
    }

    @Test
    fun completedBoundaryEditUpdatesOverallArrivalAndKeepsUntouchedSegmentMeasured() = runTest {
        val id = repository.saveRecord(record())
        val saved = requireNotNull(repository.getRecord(id))
        val changed = saved.routeSegments.last().copy(
            actualEndedAtEpochMillis = 25 * MINUTE,
            actualDurationMinutes = 15,
            isUserEdited = true,
        )
        val updated = repository.updateRouteSegments(id, listOf(changed))
        assertEquals(25 * MINUTE, updated.arrivedAtEpochMillis)
        assertEquals(-5, updated.arrivalDeltaMinutes)
        assertFalse(updated.routeSegments.first().isUserEdited)
        assertTrue(updated.routeSegments.last().isUserEdited)
    }

    @Test
    fun segmentFromDifferentRecordIsRejected() = runTest {
        val firstId = repository.saveRecord(record())
        val secondId = repository.saveRecord(record())
        val foreignSegment = requireNotNull(repository.getRecord(secondId)).routeSegments.first()
        assertTrue(runCatching { repository.updateRouteSegments(firstId, listOf(foreignSegment)) }.isFailure)
    }

    @Test
    fun durationInconsistentWithTimestampsIsRejectedWithoutChangingRecord() = runTest {
        val id = repository.saveRecord(record())
        val saved = requireNotNull(repository.getRecord(id))
        val inconsistent = saved.routeSegments.first().copy(actualDurationMinutes = 99)
        assertTrue(runCatching { repository.updateRouteSegments(id, listOf(inconsistent)) }.isFailure)
        assertEquals(saved, repository.getRecord(id))
    }

    @Test
    fun concurrentCompletionOfSameArrivalEventCreatesOnlyOneRecordAndOneSegmentSet() = runTest {
        val input = record().copy(routineId = 88L)
        val ids = coroutineScope { List(8) { async { repository.saveRecord(input) } }.awaitAll() }
        assertEquals(1, ids.distinct().size)
        val records = repository.getRecentRecords(50, 88L)
        assertEquals(1, records.size)
        assertEquals(2, records.single().routeSegments.size)
        val next = input.copy(targetArrivalAtEpochMillis = requireNotNull(input.targetArrivalAtEpochMillis) + 86_400_000L)
        repository.saveRecord(next)
        assertEquals(2, repository.getRecentRecords(50, 88L).size)
    }

    @Test
    fun arrivalBeforeDepartureIsRejectedWithoutInsertingRecord() = runTest {
        assertTrue(runCatching { repository.saveRecord(record().copy(arrivedAtEpochMillis = -1)) }.isFailure)
        assertTrue(repository.getRecentRecords(50, null).isEmpty())
    }

    @Test
    fun overlappingMeasuredSegmentsAreRejectedAndAllChangesRollBack() = runTest {
        val id = repository.saveRecord(record())
        val before = requireNotNull(repository.getRecord(id))
        val overlapping = before.routeSegments.last().copy(actualStartedAtEpochMillis = 9 * MINUTE,
            actualDurationMinutes = 11, isUserEdited = true)
        assertTrue(runCatching { repository.updateRouteSegments(id, listOf(overlapping)) }.isFailure)
        assertEquals(before, repository.getRecord(id))
    }

    @Test
    fun skippedMiddleSegmentDoesNotPreventReliableBoundarySummaryUpdate() = runTest {
        val base = record().copy(routeSegments = listOf(segment(0),
            RouteSegment(segmentIndex = 1, segmentType = RouteSegmentType.TRANSFER_WALK,
                plannedDurationMinutes = 0, status = RouteSegmentStatus.SKIPPED), segment(1).copy(segmentIndex = 2)))
        val id = repository.saveRecord(base)
        val before = requireNotNull(repository.getRecord(id))
        val last = before.routeSegments.last().copy(actualEndedAtEpochMillis = 25 * MINUTE,
            actualDurationMinutes = 15, isUserEdited = true)
        val updated = repository.updateRouteSegments(id, listOf(last))
        assertEquals(25 * MINUTE, updated.arrivedAtEpochMillis)
        assertEquals(-5, updated.arrivalDeltaMinutes)
        assertEquals(RouteSegmentStatus.SKIPPED, updated.routeSegments[1].status)
    }

    @Test
    fun inconsistentOrUnfinishedSegmentCannotBeSavedAsCompletedCommute() = runTest {
        val original = record()
        for (invalid in listOf(original.routeSegments.first().copy(actualDurationMinutes = 99),
            original.routeSegments.first().copy(status = RouteSegmentStatus.SKIPPED),
            original.routeSegments.first().copy(status = RouteSegmentStatus.IN_PROGRESS,
                actualEndedAtEpochMillis = null, actualDurationMinutes = null))) {
            assertTrue(runCatching { repository.saveRecord(original.copy(routeSegments = listOf(invalid))) }.isFailure)
        }
        assertTrue(repository.getRecentRecords(50, null).isEmpty())
    }

    @Test
    fun timingEditCannotChangePlannedRouteIdentityOrMakeTheRecordInProgress() = runTest {
        val id = repository.saveRecord(record())
        val saved = requireNotNull(repository.getRecord(id))
        val segment = saved.routeSegments.first()
        for (invalid in listOf(segment.copy(plannedDurationMinutes = 100), segment.copy(routeName = "Other route"),
            segment.copy(status = RouteSegmentStatus.IN_PROGRESS, actualEndedAtEpochMillis = null, actualDurationMinutes = null))) {
            assertTrue(runCatching { repository.updateRouteSegments(id, listOf(invalid)) }.isFailure)
            assertEquals(saved, repository.getRecord(id))
        }
    }

    @Test
    fun saveRejectsForeignRoutineOrRecordOwnershipBeforeWritingAnything() = runTest {
        val base = record().copy(routineId = 88L)
        for (invalid in listOf(base.routeSegments.first().copy(routineId = 99L),
            base.routeSegments.first().copy(commuteRecordId = 999L))) {
            assertTrue(runCatching { repository.saveRecord(base.copy(routeSegments = listOf(invalid))) }.isFailure)
            assertTrue(repository.getRecentRecords(50, null).isEmpty())
        }
    }

    @Test
    fun newRecordCannotReplaceAnotherRecordsSegmentsByReusingDatabaseIds() = runTest {
        val originalId = repository.saveRecord(record())
        val original = requireNotNull(repository.getRecord(originalId))
        val borrowed = original.routeSegments.map { it.copy(commuteRecordId = null) }
        assertTrue(runCatching { repository.saveRecord(record().copy(routeSegments = borrowed)) }.isFailure)
        assertEquals(original, repository.getRecord(originalId))
        assertEquals(1, repository.getRecentRecords(50, null).size)
    }

    @Test
    fun repositoryDerivesManualEditProvenanceAndCannotEraseOrInventIt() = runTest {
        val id = repository.saveRecord(record())
        val before = requireNotNull(repository.getRecord(id))
        val changed = before.routeSegments.last().copy(actualEndedAtEpochMillis = 25 * MINUTE,
            actualDurationMinutes = 15, isUserEdited = false)
        val updated = repository.updateRouteSegments(id, listOf(changed))
        assertTrue(updated.routeSegments.last().isUserEdited)
        assertFalse(updated.routeSegments.first().isUserEdited)
        val unchanged = repository.updateRouteSegments(id, listOf(
            updated.routeSegments.last().copy(isUserEdited = false),
            updated.routeSegments.first().copy(isUserEdited = true),
        ))
        assertTrue(unchanged.routeSegments.last().isUserEdited)
        assertFalse(unchanged.routeSegments.first().isUserEdited)
    }

    private fun record() = CommuteRecord(
        routineId = null, routineName = "Commute", originName = "Origin", destinationName = "Destination",
        transportMode = TransportMode.TRANSIT, targetArrivalTime = LocalTime.of(9, 0),
        targetArrivalAtEpochMillis = 30 * MINUTE, recommendedDepartureTime = LocalTime.of(8, 0),
        routeDurationMinutes = 20, routeSummary = "Route", startedAtEpochMillis = 0L,
        arrivedAtEpochMillis = 20 * MINUTE, arrivalDeltaMinutes = -10,
        routeSegments = listOf(segment(0), segment(1)),
    )

    private fun segment(index: Int) = RouteSegment(
        segmentIndex = index, segmentType = RouteSegmentType.WALK_TO_DESTINATION,
        plannedDurationMinutes = 10, actualStartedAtEpochMillis = index * 10 * MINUTE,
        actualEndedAtEpochMillis = (index + 1) * 10 * MINUTE, actualDurationMinutes = 10,
        status = RouteSegmentStatus.COMPLETED,
    )

    private companion object { const val MINUTE = 60_000L }
}
