package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulBusPositionApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.TransitArrivalQuery
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulBusOperationStatusProviderTest {
    @Test
    fun getOperationStatus_summarizesBusPositions() = runTest {
        val provider = SeoulBusOperationStatusProvider(
            api = FakeSeoulBusPositionApi(
                xml = """
                    <ServiceResult>
                        <msgBody>
                            <itemList>
                                <plainNo>서울70사1234</plainNo>
                                <isArrive>1</isArrive>
                            </itemList>
                            <itemList>
                                <plainNo>서울70사5678</plainNo>
                                <isArrive>0</isArrive>
                            </itemList>
                        </msgBody>
                    </ServiceResult>
                """.trimIndent(),
            ),
            config = configWithSeoulBusKey,
        )

        val status = provider.getOperationStatus(
            TransitArrivalQuery.Bus(
                stationName = "숭실대입구역",
                stationId = "123",
                stationArsId = "456",
                busRouteId = "987",
                routeName = "740",
            ),
        )

        assertEquals("Seoul Bus Position", status?.providerName)
        assertEquals("노선 운행 차량 2대 확인", status?.summary)
        assertTrue(status?.reason.orEmpty().contains("정류장 접근 차량 1대"))
    }

    private class FakeSeoulBusPositionApi(
        private val xml: String,
    ) : SeoulBusPositionApi {
        override suspend fun getBusPositionsByRoute(
            serviceKey: String,
            busRouteId: String,
        ) = xml.toResponseBody()
    }

    private companion object {
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
