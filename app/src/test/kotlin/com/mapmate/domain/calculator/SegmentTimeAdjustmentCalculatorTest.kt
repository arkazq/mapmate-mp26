package com.mapmate.domain.calculator

import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentStatus
import com.mapmate.domain.model.RouteSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentTimeAdjustmentCalculatorTest {
    private val calculator = SegmentTimeAdjustmentCalculator()

    @Test
    fun calculate_groupsByStableSegmentKeyIgnoringSegmentIndex() {
        val adjustments = calculator.calculate(
            routineId = 1L,
            completedSegments = listOf(
                segment(index = 0, actual = 13, endedAt = 3000L),
                segment(index = 2, actual = 15, endedAt = 2000L),
                segment(index = 5, actual = 14, endedAt = 1000L),
            ),
            updatedAtEpochMillis = 5000L,
        )

        val adjustment = adjustments.single()
        assertEquals(RouteSegmentType.BUS_RIDE, adjustment.segmentType)
        assertEquals("753", adjustment.routeName)
        assertEquals("start stop", adjustment.startName)
        assertEquals("end stop", adjustment.endName)
        assertEquals(4, adjustment.averageDelayMinutes)
        assertEquals(14, adjustment.averageActualDurationMinutes)
        assertEquals(13, adjustment.minActualDurationMinutes)
        assertEquals(15, adjustment.maxActualDurationMinutes)
        assertEquals(3, adjustment.sampleCount)
        assertEquals(1.0, adjustment.confidence, 0.001)
    }

    @Test
    fun calculate_lowersConfidenceForSmallSamplesAndUserEditedSegments() {
        val adjustments = calculator.calculate(
            routineId = 1L,
            completedSegments = listOf(
                segment(index = 0, actual = 20, endedAt = 2000L, isUserEdited = true),
                segment(index = 1, actual = 18, endedAt = 1000L),
            ),
            updatedAtEpochMillis = 5000L,
        )

        val adjustment = adjustments.single()
        assertEquals(2, adjustment.sampleCount)
        assertTrue(adjustment.confidence < 0.7)
    }

    @Test
    fun calculate_excludesExtremeOutliers() {
        val adjustments = calculator.calculate(
            routineId = 1L,
            completedSegments = listOf(
                segment(index = 0, actual = 100, endedAt = 2000L),
                segment(index = 1, actual = 13, endedAt = 1000L),
            ),
            updatedAtEpochMillis = 5000L,
        )

        val adjustment = adjustments.single()
        assertEquals(3, adjustment.averageDelayMinutes)
        assertEquals(1, adjustment.sampleCount)
    }

    @Test
    fun calculate_keepsAdjustmentsSeparatedByRoutineId() {
        val adjustments = calculator.calculate(
            routineId = 1L,
            completedSegments = listOf(
                segment(index = 0, actual = 13, endedAt = 3000L, routineId = 1L),
                segment(index = 1, actual = 15, endedAt = 2000L, routineId = 1L),
                segment(index = 2, actual = 40, endedAt = 1000L, routineId = 2L),
            ),
            updatedAtEpochMillis = 5000L,
        )

        val adjustment = adjustments.single()
        assertEquals(1L, adjustment.routineId)
        assertEquals(4, adjustment.averageDelayMinutes)
        assertEquals(2, adjustment.sampleCount)
    }

    @Test
    fun calculate_keepsAdjustmentsSeparatedByRouteNameWithinSameRoutine() {
        val adjustments = calculator.calculate(
            routineId = 1L,
            completedSegments = listOf(
                segment(index = 0, actual = 13, endedAt = 3000L, routeName = "753"),
                segment(index = 1, actual = 20, endedAt = 2000L, routeName = "740"),
            ),
            updatedAtEpochMillis = 5000L,
        )

        assertEquals(2, adjustments.size)
        assertEquals(3, adjustments.single { it.routeName == "753" }.averageDelayMinutes)
        assertEquals(10, adjustments.single { it.routeName == "740" }.averageDelayMinutes)
    }

    private fun segment(
        index: Int,
        actual: Int,
        endedAt: Long,
        isUserEdited: Boolean = false,
        routineId: Long = 1L,
        routeName: String = "753",
    ): RouteSegment {
        return RouteSegment(
            routineId = routineId,
            segmentIndex = index,
            segmentType = RouteSegmentType.BUS_RIDE,
            trafficType = 2,
            routeName = routeName,
            startName = "Start Stop",
            endName = "End Stop",
            plannedDurationMinutes = 10,
            actualEndedAtEpochMillis = endedAt,
            actualDurationMinutes = actual,
            isUserEdited = isUserEdited,
            status = RouteSegmentStatus.COMPLETED,
        )
    }
}
