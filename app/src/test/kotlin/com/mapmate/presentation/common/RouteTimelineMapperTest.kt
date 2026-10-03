package com.mapmate.presentation.common

import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.RouteSegmentType
import com.mapmate.testing.sampleRoutine
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RouteTimelineMapperTest {
    @Test
    fun actualSegmentsAreDisplayedInOrderWithNamesAndPlannedDurations() {
        val recommendation = sampleRoutine().toRecommendationUiModel(
            RouteEstimate(20, "test", "Test", "test", segments = listOf(
                RouteSegment(segmentIndex = 1, segmentType = RouteSegmentType.BUS_RIDE,
                    routeName = "753", startName = "서울역", endName = "숭실대입구", plannedDurationMinutes = 18),
                RouteSegment(segmentIndex = 0, segmentType = RouteSegmentType.WALK_TO_TRANSIT,
                    startName = "집", endName = "서울역", plannedDurationMinutes = 2),
            )), now = LocalTime.of(8, 0),
        )
        val timeline = recommendation.routeTimelineItems()
        assertEquals(4, timeline.size)
        assertEquals("정류장·역까지 도보", timeline[1].title)
        assertEquals("753번 버스", timeline[2].title)
        assertEquals("서울역 → 숭실대입구", timeline[2].description)
        assertEquals("18분", timeline[2].timeText)
        assertEquals("도착 목표", timeline.last().description)
    }

    @Test
    fun missingSegmentDataDoesNotFabricateTransfersOrStops() {
        val recommendation = sampleRoutine().toFallbackRecommendationUiModel(now = LocalTime.of(8, 0))
        val timeline = recommendation.routeTimelineItems()
        assertEquals(3, timeline.size)
        assertFalse(timeline.any { it.title == "환승" })
        assertEquals("기본 예상 시간", timeline[1].description)
    }
}
