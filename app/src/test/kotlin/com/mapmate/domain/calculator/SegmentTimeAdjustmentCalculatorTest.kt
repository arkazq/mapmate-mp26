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

    private fun segment(
        index: Int,
        actual: Int,
        endedAt: Long,
        isUserEdited: Boolean = false,
    ): RouteSegment {
        return RouteSegment(
            routineId = 1L,
            segmentIndex = index,
            segmentType = RouteSegmentType.BUS_RIDE,
            trafficType = 2,
            routeName = "753",
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
