package com.mapmate.presentation.segmentedit

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.domain.model.TransportMode
import com.mapmate.testing.MainDispatcherRule
import com.mapmate.testing.TestCommuteRecordRepository
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RouteSegmentEditViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savingWithoutChangesPreservesMeasuredConfidenceAndDoesNotWrite() = runTest {
        val record = record()
        val repository = TestCommuteRecordRepository(listOf(record))
        val viewModel = RouteSegmentEditViewModel(record, repository)
        viewModel.onSaveClick()
        runCurrent()
        assertTrue(repository.batches.isEmpty())
        assertEquals(record, viewModel.uiState.value.savedRecord)
        assertFalse(viewModel.uiState.value.segments.first().isUserEdited)
    }

    @Test
    fun changingOnlyOneSegmentUpdatesOnlyThatSegmentAndRecomputesDuration() = runTest {
        val record = record()
        val repository = TestCommuteRecordRepository(listOf(record))
        val viewModel = RouteSegmentEditViewModel(record, repository)
        viewModel.onEndTimeSelected(11L, 8, 9)
        assertEquals(9, viewModel.uiState.value.segments.first().actualDurationMinutes)
        viewModel.onSaveClick()
        runCurrent()
        val saved = repository.batches.single().single()
        assertEquals(11L, saved.id)
        assertEquals(9, saved.actualDurationMinutes)
        assertTrue(saved.isUserEdited)
        assertFalse(viewModel.uiState.value.savedRecord!!.routeSegments.last().isUserEdited)
    }

    @Test
    fun skippedSegmentWithNoTimesRemainsSkipped() = runTest {
        val record = record().let { it.copy(routeSegments = it.routeSegments + segment(13, 2).copy(
            actualStartedAtEpochMillis = null,
            actualEndedAtEpochMillis = null,
            actualDurationMinutes = null,
            status = RouteSegmentStatus.SKIPPED,
        )) }
        val repository = TestCommuteRecordRepository(listOf(record))
        val viewModel = RouteSegmentEditViewModel(record, repository)
        viewModel.onEndTimeSelected(11L, 8, 9)
        viewModel.onSaveClick()
        runCurrent()
        assertEquals(RouteSegmentStatus.SKIPPED, viewModel.uiState.value.savedRecord!!.routeSegments.last().status)
    }

    @Test
    fun endBeforeStartIsRejectedWithoutPersistingAnyChanges() = runTest {
        val record = record()
        val repository = TestCommuteRecordRepository(listOf(record))
        val viewModel = RouteSegmentEditViewModel(record, repository)
        viewModel.onEndTimeSelected(11L, 7, 59)
        viewModel.onSaveClick()
        runCurrent()
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(repository.batches.isEmpty())
    }

    @Test
    fun invalidHourAndMinuteDoNotCrashOrChangeTimes() = runTest {
        val record = record()
        val viewModel = RouteSegmentEditViewModel(record, TestCommuteRecordRepository(listOf(record)))
        viewModel.onStartTimeSelected(11L, 24, 0)
        assertEquals(record.routeSegments, viewModel.uiState.value.segments)
        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.onEndTimeSelected(11L, 8, 60)
        assertEquals(record.routeSegments, viewModel.uiState.value.segments)
    }

    @Test
    fun overnightRecordKeepsEndOnFollowingDate() = runTest {
        val start = epoch(1, 23, 58)
        val end = epoch(2, 0, 7)
        val record = record().copy(
            startedAtEpochMillis = start,
            arrivedAtEpochMillis = end,
            routeSegments = listOf(segment(11, 0).copy(
                actualStartedAtEpochMillis = start,
                actualEndedAtEpochMillis = end,
            )),
        )
        val repository = TestCommuteRecordRepository(listOf(record))
        val viewModel = RouteSegmentEditViewModel(record, repository)
        viewModel.onEndTimeSelected(11L, 0, 10)
        viewModel.onSaveClick()
        runCurrent()
        assertEquals(epoch(2, 0, 10), repository.batches.single().single().actualEndedAtEpochMillis)
        assertEquals(12, repository.batches.single().single().actualDurationMinutes)
    }

    @Test fun explicitDateSelectionMovesStartAcrossMidnightWithoutGuessingThePreviousDate() = runTest {
        val original = record().copy(startedAtEpochMillis = epoch(1, 23, 58), arrivedAtEpochMillis = epoch(2, 0, 7),
            routeSegments = listOf(segment(11, 0).copy(actualStartedAtEpochMillis = epoch(1, 23, 58),
                actualEndedAtEpochMillis = epoch(2, 0, 7), actualDurationMinutes = 9)))
        val repository = TestCommuteRecordRepository(listOf(original))
        val model = RouteSegmentEditViewModel(original, repository)
        model.onStartDateTimeSelected(11, epoch(2, 0, 2))
        model.onEndDateTimeSelected(11, epoch(2, 0, 10))
        assertEquals(8, model.uiState.value.segments.single().actualDurationMinutes)
        assertTrue(model.uiState.value.hasUnsavedChanges)
        model.onSaveClick()
        runCurrent()
        val saved = repository.batches.single().single()
        assertEquals(epoch(2, 0, 2), saved.actualStartedAtEpochMillis)
        assertEquals(epoch(2, 0, 10), saved.actualEndedAtEpochMillis)
        assertTrue(saved.isUserEdited)
    }

    private fun record() = CommuteRecord(
        id = 1L, routineId = 1L, routineName = "Commute", originName = "Origin",
        destinationName = "Destination", transportMode = TransportMode.TRANSIT,
        targetArrivalTime = LocalTime.of(9, 0), targetArrivalAtEpochMillis = epoch(1, 9, 0),
        recommendedDepartureTime = LocalTime.of(8, 0), routeDurationMinutes = 20, routeSummary = "Route",
        startedAtEpochMillis = epoch(1, 8, 0), arrivedAtEpochMillis = epoch(1, 8, 20),
        arrivalDeltaMinutes = -40,
        routeSegments = listOf(segment(11, 0), segment(12, 1)),
    )

    private fun segment(id: Long, index: Int) = RouteSegment(
        id = id, commuteRecordId = 1L, routineId = 1L, segmentIndex = index,
        segmentType = RouteSegmentType.BUS_RIDE, plannedDurationMinutes = 10,
        actualStartedAtEpochMillis = epoch(1, 8, index * 10),
        actualEndedAtEpochMillis = epoch(1, 8, (index + 1) * 10),
        actualDurationMinutes = 10, status = RouteSegmentStatus.COMPLETED,
    )

    private fun epoch(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli()
}
