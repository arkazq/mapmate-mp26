package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulBusArrivalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.TransitArrivalQuery
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CancellationException

class SeoulBusRealtimeArrivalProviderTest {
    @Test fun endedWaitingAndUnknownZeroSecondArrivalsAreNotImmediateCandidates() = runTest {
        for (message in listOf("운행종료", "운행 종료", "출발대기", "도착 정보가 없습니다", "", "3번째 전")) {
            val api = FakeSeoulBusArrivalApi(stationUidXml = stationXml("""
                <traTime1>300</traTime1><arrmsg1>5분 후 도착</arrmsg1>
                <exps2>0</exps2><traTime2>0</traTime2><arrmsg2>$message</arrmsg2>
            """), routeAllXml = emptyResponseXml)
            val result = SeoulBusRealtimeArrivalProvider(api, configWithSeoulBusKey).getArrivalEstimate(stationQuery())
            assertEquals(message, listOf(5), result?.waitCandidateMinutes)
        }
    }

    @Test fun endedMessageOverridesStalePositiveSecondsAndRealSoonArrivalRemainsValid() = runTest {
        val api = FakeSeoulBusArrivalApi(stationXml("""
            <traTime1>300</traTime1><arrmsg1>운행종료</arrmsg1>
            <traTime2>0</traTime2><arrmsg2>곧 도착[1번째 전]</arrmsg2>
        """), emptyResponseXml)
        val result = SeoulBusRealtimeArrivalProvider(api, configWithSeoulBusKey).getArrivalEstimate(stationQuery())
        assertEquals(listOf(0), result?.waitCandidateMinutes)
    }

    @Test fun zeroExponentialFieldDoesNotHidePositiveTravelTimeOrInventDuplicateVehicles() = runTest {
        val api = FakeSeoulBusArrivalApi(stationXml("""
            <exps1>0</exps1><traTime1>300</traTime1><arrmsg1>4분 후 도착</arrmsg1>
            <exps2>0</exps2><traTime2>900</traTime2><arrmsg2>14분 후 도착</arrmsg2>
        """), emptyResponseXml)
        val result = SeoulBusRealtimeArrivalProvider(api, configWithSeoulBusKey).getArrivalEstimate(stationQuery())
        assertEquals(listOf(5, 15), result?.waitCandidateMinutes)
    }

    @Test fun malformedStationXmlStillUsesTheExistingRouteFallback() = runTest {
        val api = FakeSeoulBusArrivalApi("<broken>", stationXml("<traTime1>300</traTime1>"))
        val result = SeoulBusRealtimeArrivalProvider(api, configWithSeoulBusKey).getArrivalEstimate(stationQuery().copy(busRouteId = "route"))
        assertEquals(5, result?.waitMinutes)
        assertEquals(listOf("route"), api.routeAllCalls)
    }

    @Test fun mismatchedRouteFallbackDoesNotReportAnotherBusAtTheSameStation() = runTest {
        val wrongRoute = stationXml("<traTime1>300</traTime1>").replace("<rtNm>753</rtNm>", "<rtNm>740</rtNm>")
        val api = FakeSeoulBusArrivalApi(emptyResponseXml, wrongRoute)
        val result = SeoulBusRealtimeArrivalProvider(api, configWithSeoulBusKey).getArrivalEstimate(stationQuery().copy(busRouteId = "wrong-route"))
        assertNull(result)
    }

    private fun stationXml(fields: String) = """
        <ServiceResult><msgBody><itemList><arsId>20170</arsId><stNm>Stop</stNm><rtNm>753</rtNm>
        $fields</itemList></msgBody></ServiceResult>
    """.trimIndent()

    private fun stationQuery() = TransitArrivalQuery.Bus(stationName = "Stop", stationId = null,
        stationArsId = "20170", busRouteId = null, routeName = "753")

    @Test
    fun stationQueryCancellationDoesNotTriggerRouteFallback() = runTest {
        var fallbackCalls = 0
        val api = object : SeoulBusArrivalApi {
            override suspend fun getArrivalsByStationUid(serviceKey: String, stationArsId: String): ResponseBody =
                throw CancellationException("screen closed")
            override suspend fun getArrivalsByRouteAll(serviceKey: String, busRouteId: String): ResponseBody {
                fallbackCalls++
                return emptyResponseXml.toResponseBody()
            }
        }
        val provider = SeoulBusRealtimeArrivalProvider(api, configWithSeoulBusKey)
        val result = runCatching {
            provider.getArrivalEstimate(TransitArrivalQuery.Bus(
                stationName = "Stop", stationId = null, stationArsId = "20170",
                busRouteId = "route", routeName = "753",
            ))
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(0, fallbackCalls)
    }

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

    @Test
    fun getArrivalEstimate_keepsOnlyValidArrivalCandidatesWhenSecondArrivalIsUnavailable() = runTest {
        val api = FakeSeoulBusArrivalApi(
            stationUidXml = """
                <ServiceResult>
                    <msgBody>
                        <itemList>
                            <arsId>20170</arsId>
                            <stNm>Start stop</stNm>
                            <rtNm>753</rtNm>
                            <busRouteAbrv>753</busRouteAbrv>
                            <arrmsg1>5 minutes</arrmsg1>
                            <arrmsg2>운행종료</arrmsg2>
                            <traTime1>300</traTime1>
                        </itemList>
                    </msgBody>
                </ServiceResult>
            """.trimIndent(),
            routeAllXml = emptyResponseXml,
        )
        val provider = SeoulBusRealtimeArrivalProvider(api = api, config = configWithSeoulBusKey)

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "Start stop",
                stationId = null,
                stationArsId = "20170",
                busRouteId = null,
                routeName = "753",
            ),
        )

        assertEquals(5, estimate?.waitMinutes)
        assertEquals(listOf(5), estimate?.waitCandidateMinutes)
    }

    @Test
    fun getArrivalEstimate_includesFirstAndSecondArrivalCandidates() = runTest {
        val api = FakeSeoulBusArrivalApi(
            stationUidXml = """
                <ServiceResult>
                    <msgBody>
                        <itemList>
                            <arsId>20170</arsId>
                            <stNm>Start stop</stNm>
                            <rtNm>753</rtNm>
                            <busRouteAbrv>753</busRouteAbrv>
                            <traTime1>180</traTime1>
                            <traTime2>1740</traTime2>
                        </itemList>
                    </msgBody>
                </ServiceResult>
            """.trimIndent(),
            routeAllXml = emptyResponseXml,
        )
        val provider = SeoulBusRealtimeArrivalProvider(api = api, config = configWithSeoulBusKey)

        val estimate = provider.getArrivalEstimate(
            TransitArrivalQuery.Bus(
                stationName = "Start stop",
                stationId = null,
                stationArsId = "20170",
                busRouteId = null,
                routeName = "753",
            ),
        )

        assertEquals(3, estimate?.waitMinutes)
        assertEquals(listOf(3, 29), estimate?.waitCandidateMinutes)
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
