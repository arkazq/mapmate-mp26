package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.TagoBusArrivalApi
import com.mapmate.data.remote.api.TagoBusStationApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.TagoBusArrivalBody
import com.mapmate.data.remote.dto.TagoBusArrivalEnvelope
import com.mapmate.data.remote.dto.TagoBusArrivalItem
import com.mapmate.data.remote.dto.TagoBusArrivalItems
import com.mapmate.data.remote.dto.TagoBusArrivalResponse
import com.mapmate.data.remote.dto.TagoBusStationBody
import com.mapmate.data.remote.dto.TagoBusStationEnvelope
import com.mapmate.data.remote.dto.TagoBusStationItem
import com.mapmate.data.remote.dto.TagoBusStationItems
import com.mapmate.data.remote.dto.TagoBusStationResponse
import com.mapmate.domain.model.TransitArrivalQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagoBusArrivalProviderTest {
    @Test fun invalidArrivalDoesNotHideLaterValidBusesAndOtherRoutesAreExcluded() = runTest {
        val provider = TagoBusArrivalProvider(FakeStationApi(listOf(
            TagoBusStationItem(citycode = "25", nodeid = "node", nodenm = "Stop", gpslati = 37.0, gpslong = 127.0))),
            FakeArrivalApi(listOf(
                TagoBusArrivalItem(routeno = "753", arrtime = -1),
                TagoBusArrivalItem(routeno = "753", arrtime = null),
                TagoBusArrivalItem(routeno = "740", arrtime = 60),
                TagoBusArrivalItem(routeno = "753", arrtime = 300),
                TagoBusArrivalItem(routeno = "753", arrtime = 900))), configWithTagoKey)
        val estimate = provider.getArrivalEstimate(TransitArrivalQuery.Bus(stationName = "Stop", stationId = null,
            stationArsId = null, busRouteId = null, routeName = "753", stationLatitude = 37.0, stationLongitude = 127.0))
        assertEquals(5, estimate?.waitMinutes)
        assertEquals(listOf(5, 15), estimate?.waitCandidateMinutes)
    }

    @Test fun missingRouteOrInvalidCoordinatesDoNotQueryUnrelatedBusArrivals() = runTest {
        var stationCalls = 0
        val stationApi = object : TagoBusStationApi {
            override suspend fun getNearbyStations(serviceKey: String, latitude: Double, longitude: Double,
                pageNo: Int, numOfRows: Int, type: String): TagoBusStationResponse {
                stationCalls++
                error("must not query")
            }
        }
        val provider = TagoBusArrivalProvider(stationApi, FakeArrivalApi(emptyList()), configWithTagoKey)
        val query = TransitArrivalQuery.Bus(stationName = "Stop", stationId = null, stationArsId = null,
            busRouteId = null, routeName = "753", stationLatitude = 37.0, stationLongitude = 127.0)
        for (invalid in listOf(query.copy(routeName = null), query.copy(routeName = ""),
            query.copy(stationLatitude = Double.NaN), query.copy(stationLongitude = 181.0))) {
            assertNull(provider.getArrivalEstimate(invalid))
        }
        assertEquals(0, stationCalls)
    }

    @Test
    fun getArrivalEstimate_returnsRouteMatchedArrival() = runTest {
        val provider = TagoBusArrivalProvider(
            stationApi = FakeStationApi(
                stations = listOf(
                    TagoBusStationItem(
                        citycode = "25",
                        nodeid = "DJB8001793",
                        nodenm = "정류장",
                        gpslati = 37.0,
                        gpslong = 127.0,
                    ),
                ),
            ),
            arrivalApi = FakeArrivalApi(
                arrivals = listOf(
                    TagoBusArrivalItem(
                        nodeid = "DJB8001793",
                        nodenm = "정류장",
                        routeno = "740",
                        routetp = "간선",
                        arrprevstationcnt = 3,
                        arrtime = 480,
                    ),
                ),
            ),
            config = configWithTagoKey,
        )

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "정류장",
                stationId = null,
                stationArsId = null,
                busRouteId = null,
                routeName = "740",
                stationLatitude = 37.0,
                stationLongitude = 127.0,
            ),
        )

        assertEquals(8, estimate?.waitMinutes)
        assertEquals("TAGO Bus Arrival", estimate?.providerName)
        assertTrue(estimate?.reason.orEmpty().contains("남은 정류장 3개"))
    }

    @Test
    fun getArrivalEstimate_matchesOdsayRouteNameWithOperatorSuffix() = runTest {
        val provider = TagoBusArrivalProvider(
            stationApi = FakeStationApi(
                stations = listOf(
                    TagoBusStationItem(
                        citycode = "31010",
                        nodeid = "GGB202000208",
                        nodenm = "수원역4번출구.노보텔수원",
                        gpslati = 37.268096,
                        gpslong = 126.999572,
                    ),
                ),
            ),
            arrivalApi = FakeArrivalApi(
                arrivals = listOf(
                    TagoBusArrivalItem(
                        nodeid = "GGB202000208",
                        nodenm = "수원역4번출구.노보텔수원",
                        routeno = "11",
                        routetp = "일반",
                        arrprevstationcnt = 2,
                        arrtime = 360,
                    ),
                ),
            ),
            config = configWithTagoKey,
        )

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "수원역4번출구.노보텔수원",
                stationId = null,
                stationArsId = null,
                busRouteId = null,
                routeName = "11(남양여객)",
                stationLatitude = 37.268096,
                stationLongitude = 126.999572,
            ),
        )

        assertEquals(6, estimate?.waitMinutes)
        assertTrue(estimate?.summary.orEmpty().contains("11번 버스"))
    }

    @Test
    fun getArrivalEstimate_returnsNullWhenStationCoordinateIsMissing() = runTest {
        val provider = TagoBusArrivalProvider(
            stationApi = FakeStationApi(emptyList()),
            arrivalApi = FakeArrivalApi(emptyList()),
            config = configWithTagoKey,
        )

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "정류장",
                stationId = null,
                stationArsId = null,
                busRouteId = null,
                routeName = "740",
            ),
        )

        assertNull(estimate)
    }

    private class FakeStationApi(
        private val stations: List<TagoBusStationItem>,
    ) : TagoBusStationApi {
        override suspend fun getNearbyStations(
            serviceKey: String,
            latitude: Double,
            longitude: Double,
            pageNo: Int,
            numOfRows: Int,
            type: String,
        ): TagoBusStationResponse {
            return TagoBusStationResponse(
                response = TagoBusStationEnvelope(
                    body = TagoBusStationBody(
                        items = TagoBusStationItems(item = stations),
                    ),
                ),
            )
        }
    }

    private class FakeArrivalApi(
        private val arrivals: List<TagoBusArrivalItem>,
    ) : TagoBusArrivalApi {
        override suspend fun getArrivalsByStation(
            serviceKey: String,
            cityCode: String,
            nodeId: String,
            pageNo: Int,
            numOfRows: Int,
            type: String,
        ): TagoBusArrivalResponse {
            return TagoBusArrivalResponse(
                response = TagoBusArrivalEnvelope(
                    body = TagoBusArrivalBody(
                        items = TagoBusArrivalItems(item = arrivals),
                    ),
                ),
            )
        }
    }

    private companion object {
        val configWithTagoKey = RemoteApiConfig(
            kakaoRestApiKey = "",
            odsayApiKey = "",
            googleRoutesApiKey = "",
            seoulOpenApiKey = "",
            seoulBusServiceKey = "",
            tagoServiceKey = "tago-key",
        )
    }
}
