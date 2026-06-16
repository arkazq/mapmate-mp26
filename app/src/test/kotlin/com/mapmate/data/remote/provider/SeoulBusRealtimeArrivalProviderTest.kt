package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulBusArrivalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.TransitArrivalQuery
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulBusRealtimeArrivalProviderTest {
    @Test
    fun getArrivalEstimate_usesStationArsIdAndRouteNameBeforeRouteId() = runTest {
        val api = FakeSeoulBusArrivalApi(
            stationUidXml = """
                <ServiceResult>
                    <msgBody>
                        <itemList>
                            <arsId>20170</arsId>
                            <stNm>숭실대중문앞</stNm>
                            <rtNm>740</rtNm>
                            <arrmsg1>2분 후 도착</arrmsg1>
                            <traTime1>120</traTime1>
                        </itemList>
                        <itemList>
                            <arsId>20170</arsId>
                            <stNm>숭실대중문앞</stNm>
                            <rtNm>753</rtNm>
                            <busRouteAbrv>753</busRouteAbrv>
                            <arrmsg1>4분 후 도착</arrmsg1>
                            <traTime1>240</traTime1>
                        </itemList>
                    </msgBody>
                </ServiceResult>
            """.trimIndent(),
            routeAllXml = emptyResponseXml,
        )
        val provider = SeoulBusRealtimeArrivalProvider(api = api, config = configWithSeoulBusKey)

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "숭실대중문앞",
                stationId = null,
                stationArsId = "20170",
                busRouteId = "odsay-route-id",
                routeName = "753",
            ),
        )

        assertEquals(4, estimate?.waitMinutes)
        assertEquals("Seoul Bus Realtime", estimate?.providerName)
        assertTrue(estimate?.summary.orEmpty().contains("753 버스"))
        assertTrue(estimate?.reason.orEmpty().contains("ARS 20170"))
        assertEquals(listOf("20170"), api.stationUidCalls)
        assertEquals(emptyList<String>(), api.routeAllCalls)
    }

    @Test
    fun getArrivalEstimate_matchesStationArrivalByShortRouteName() = runTest {
        val api = FakeSeoulBusArrivalApi(
            stationUidXml = """
                <ServiceResult>
                    <msgBody>
                        <itemList>
                            <arsId>20170</arsId>
                            <stNm>숭실대중문앞</stNm>
                            <busRouteAbrv>753</busRouteAbrv>
                            <arrmsg1>곧 도착</arrmsg1>
                        </itemList>
                    </msgBody>
                </ServiceResult>
            """.trimIndent(),
            routeAllXml = emptyResponseXml,
        )
        val provider = SeoulBusRealtimeArrivalProvider(api = api, config = configWithSeoulBusKey)

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "숭실대중문앞",
                stationId = null,
                stationArsId = "20170",
                busRouteId = null,
                routeName = "753번",
            ),
        )

        assertEquals(0, estimate?.waitMinutes)
        assertTrue(estimate?.summary.orEmpty().contains("753 버스"))
        assertEquals(emptyList<String>(), api.routeAllCalls)
    }

    @Test
    fun getArrivalEstimate_fallsBackToRouteAllWhenStationRouteMatchFails() = runTest {
        val api = FakeSeoulBusArrivalApi(
            stationUidXml = """
                <ServiceResult>
                    <msgBody>
                        <itemList>
                            <arsId>20170</arsId>
                            <stNm>숭실대중문앞</stNm>
                            <rtNm>740</rtNm>
                            <traTime1>120</traTime1>
                        </itemList>
                    </msgBody>
                </ServiceResult>
            """.trimIndent(),
            routeAllXml = """
                <ServiceResult>
                    <msgBody>
                        <itemList>
                            <stId>123456</stId>
                            <arsId>20170</arsId>
                            <stNm>숭실대중문앞</stNm>
                            <busRouteId>100100118</busRouteId>
                            <rtNm>753</rtNm>
                            <arrmsg1>5분 후 도착</arrmsg1>
                            <exps1>300</exps1>
                        </itemList>
                    </msgBody>
                </ServiceResult>
            """.trimIndent(),
        )
        val provider = SeoulBusRealtimeArrivalProvider(api = api, config = configWithSeoulBusKey)

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "숭실대중문앞",
                stationId = null,
                stationArsId = "20170",
                busRouteId = "100100118",
                routeName = "753",
            ),
        )

        assertEquals(5, estimate?.waitMinutes)
        assertTrue(estimate?.reason.orEmpty().contains("busRouteId 100100118"))
        assertEquals(listOf("20170"), api.stationUidCalls)
        assertEquals(listOf("100100118"), api.routeAllCalls)
    }

    private class FakeSeoulBusArrivalApi(
        private val stationUidXml: String,
        private val routeAllXml: String,
    ) : SeoulBusArrivalApi {
        val stationUidCalls = mutableListOf<String>()
        val routeAllCalls = mutableListOf<String>()

        override suspend fun getArrivalsByRouteAll(
            serviceKey: String,
            busRouteId: String,
        ): ResponseBody {
            routeAllCalls += busRouteId
            return routeAllXml.toResponseBody()
        }

        override suspend fun getArrivalsByStationUid(
            serviceKey: String,
            stationArsId: String,
        ): ResponseBody {
            stationUidCalls += stationArsId
            return stationUidXml.toResponseBody()
        }
    }

    private companion object {
        const val emptyResponseXml = """
            <ServiceResult>
                <msgBody />
            </ServiceResult>
        """

        val configWithSeoulBusKey = RemoteApiConfig(
            kakaoRestApiKey = "",
            odsayApiKey = "",
            googleRoutesApiKey = "",
            seoulOpenApiKey = "",
            seoulBusServiceKey = "seoul-bus-key",
            tagoServiceKey = "",
        )
    }
}
