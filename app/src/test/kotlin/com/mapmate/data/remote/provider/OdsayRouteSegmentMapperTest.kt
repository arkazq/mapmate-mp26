package com.mapmate.data.remote.provider

import com.mapmate.data.remote.dto.OdsayLane
import com.mapmate.data.remote.dto.OdsayPath
import com.mapmate.data.remote.dto.OdsaySubPath
import com.mapmate.domain.model.RouteSegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class OdsayRouteSegmentMapperTest {
    @Test
    fun toRouteSegments_mapsSingleBusRouteInOrder() {
        val path = OdsayPath(
            subPath = listOf(
                OdsaySubPath(
                    trafficType = 3,
                    sectionTime = 4,
                    startName = "Home",
                    endName = "Start stop",
                ),
                OdsaySubPath(
                    trafficType = 2,
                    sectionTime = 18,
                    startName = "Start stop",
                    endName = "End stop",
                    lane = listOf(OdsayLane(busNo = "753")),
                ),
                OdsaySubPath(
                    trafficType = 3,
                    sectionTime = 6,
                    startName = "End stop",
                    endName = "Office",
                ),
            ),
        )

        val segments = path.toRouteSegments(routineId = 10L)

        assertEquals(4, segments.size)
        assertEquals(RouteSegmentType.WALK_TO_TRANSIT, segments[0].segmentType)
        assertEquals(RouteSegmentType.WAIT_FOR_BUS, segments[1].segmentType)
        assertEquals("753", segments[1].routeName)
        assertEquals(5, segments[1].plannedDurationMinutes)
        assertEquals(RouteSegmentType.BUS_RIDE, segments[2].segmentType)
        assertEquals("753", segments[2].routeName)
        assertEquals(RouteSegmentType.WALK_TO_DESTINATION, segments[3].segmentType)
        assertEquals(listOf(0, 1, 2, 3), segments.map { it.segmentIndex })
    }

    @Test
    fun toRouteSegments_mapsTransferRouteWithPlannedWaitSegments() {
        val path = OdsayPath(
            subPath = listOf(
                OdsaySubPath(trafficType = 3, sectionTime = 3, startName = "Home", endName = "Bus stop"),
                OdsaySubPath(
                    trafficType = 2,
                    sectionTime = 12,
                    startName = "Bus stop",
                    endName = "Station",
                    lane = listOf(OdsayLane(busNo = "740")),
                ),
                OdsaySubPath(trafficType = 3, sectionTime = 5, startName = "Station", endName = "Line 7"),
                OdsaySubPath(
                    trafficType = 1,
                    sectionTime = 16,
                    startName = "Line 7",
                    endName = "Transfer station",
                    lane = listOf(OdsayLane(name = "7호선")),
                ),
                OdsaySubPath(trafficType = 3, sectionTime = 4, startName = "Transfer station", endName = "Line 3"),
                OdsaySubPath(
                    trafficType = 1,
                    sectionTime = 9,
                    startName = "Line 3",
                    endName = "Final station",
                    lane = listOf(OdsayLane(name = "3호선")),
                ),
                OdsaySubPath(trafficType = 3, sectionTime = 7, startName = "Final station", endName = "Office"),
            ),
        )

        val segments = path.toRouteSegments(routineId = 10L)

        assertEquals(
            listOf(
                RouteSegmentType.WALK_TO_TRANSIT,
                RouteSegmentType.WAIT_FOR_BUS,
                RouteSegmentType.BUS_RIDE,
                RouteSegmentType.TRANSFER_WALK,
                RouteSegmentType.WAIT_FOR_SUBWAY,
                RouteSegmentType.SUBWAY_RIDE,
                RouteSegmentType.TRANSFER_WALK,
                RouteSegmentType.WAIT_FOR_SUBWAY,
                RouteSegmentType.SUBWAY_RIDE,
                RouteSegmentType.WALK_TO_DESTINATION,
            ),
            segments.map { it.segmentType },
        )
    }
}
