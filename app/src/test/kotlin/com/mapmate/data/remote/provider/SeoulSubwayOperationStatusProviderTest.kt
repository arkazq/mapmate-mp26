package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.SeoulSubwayTrainPositionApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.SeoulSubwayTrainPositionItem
import com.mapmate.data.remote.dto.SeoulSubwayTrainPositionResponse
import com.mapmate.domain.model.TransitArrivalQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeoulSubwayOperationStatusProviderTest {
    @Test
    fun getOperationStatus_summarizesTrainPositions() = runTest {
        val api = FakeSubwayTrainPositionApi(
            response = SeoulSubwayTrainPositionResponse(
                realtimePositionList = listOf(
                    SeoulSubwayTrainPositionItem(
                        statnNm = "숭실대입구",
                        trainNo = "2001",
                        trainSttus = "0",
                        updnLine = "상행",
                    ),
                    SeoulSubwayTrainPositionItem(
                        statnNm = "서울대입구",
                        trainNo = "2002",
                        trainSttus = "0",
                        updnLine = "상행",
                    ),
                ),
            ),
        )
        val provider = SeoulSubwayOperationStatusProvider(api, configWithSeoulKey)

        val status = provider.getOperationStatus(
            TransitArrivalQuery.Subway(
                stationName = "숭실대입구",
                lineName = "2호선",
                direction = "상행",
            ),
        )

        assertEquals("Seoul Subway Position", status?.providerName)
        assertTrue(status?.summary.orEmpty().contains("2호선"))
        assertTrue(status?.reason.orEmpty().contains("현재 역 주변 열차 1건"))
    }

    private class FakeSubwayTrainPositionApi(
        private val response: SeoulSubwayTrainPositionResponse,
    ) : SeoulSubwayTrainPositionApi {
        override suspend fun getTrainPositions(
            apiKey: String,
            lineName: String,
        ): SeoulSubwayTrainPositionResponse {
            return response
        }
    }

    private companion object {
        val configWithSeoulKey = RemoteApiConfig(
            kakaoRestApiKey = "",
            odsayApiKey = "",
            googleRoutesApiKey = "",
            seoulOpenApiKey = "seoul-key",
            seoulBusServiceKey = "",
            tagoServiceKey = "",
        )
    }
}
