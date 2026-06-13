package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulSubwayRealtimeApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.SeoulSubwayArrivalItem
import com.mapmate.data.remote.dto.SeoulSubwayArrivalResponse
import com.mapmate.domain.model.TransitArrivalQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeoulSubwayRealtimeArrivalProviderTest {
    @Test
    fun getArrivalEstimate_returnsSoonestSubwayArrival() = runTest {
        val api = FakeSeoulSubwayRealtimeApi(
            response = SeoulSubwayArrivalResponse(
                realtimeArrivalList = listOf(
                    SeoulSubwayArrivalItem(
                        arrivalSeconds = "240",
                        arrivalMessage = "4분 후",
                        trainLineName = "2호선",
                    ),
                    SeoulSubwayArrivalItem(
                        arrivalSeconds = "120",
                        arrivalMessage = "2분 후",
                        trainLineName = "2호선",
                    ),
                ),
            ),
        )
        val provider = SeoulSubwayRealtimeArrivalProvider(
            api = api,
            config = remoteApiConfig,
        )

        val result = provider.getArrivalEstimate(
            TransitArrivalQuery.Subway(
                stationName = "강남역",
                lineName = "2호선",
                direction = null,
            ),
        )

        assertEquals(2, result?.waitMinutes)
        assertEquals("강남", api.lastStationName)
    }

    private class FakeSeoulSubwayRealtimeApi(
        private val response: SeoulSubwayArrivalResponse,
    ) : SeoulSubwayRealtimeApi {
        var lastStationName: String? = null
            private set

        override suspend fun getRealtimeStationArrival(
            apiKey: String,
            stationName: String,
        ): SeoulSubwayArrivalResponse {
            lastStationName = stationName
            return response
        }
    }

    private companion object {
        val remoteApiConfig = RemoteApiConfig(
            kakaoRestApiKey = "",
            odsayApiKey = "",
            googleRoutesApiKey = "",
            seoulOpenApiKey = "seoul-key",
            seoulBusServiceKey = "",
            tagoServiceKey = "",
        )
    }
}
