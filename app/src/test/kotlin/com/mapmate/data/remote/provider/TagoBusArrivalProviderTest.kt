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
