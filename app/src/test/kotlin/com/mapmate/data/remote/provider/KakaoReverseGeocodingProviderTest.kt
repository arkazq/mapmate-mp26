package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.KakaoLocalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.KakaoLandAddress
import com.mapmate.data.remote.dto.KakaoPlaceSearchResponse
import com.mapmate.data.remote.dto.KakaoReverseGeocodingDocument
import com.mapmate.data.remote.dto.KakaoReverseGeocodingResponse
import com.mapmate.data.remote.dto.KakaoRoadAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KakaoReverseGeocodingProviderTest {
    @Test
    fun getAddress_prefersRoadAddressAndPassesLongitudeLatitude() = runTest {
        val api = FakeKakaoLocalApi(
            response = KakaoReverseGeocodingResponse(
                documents = listOf(
                    KakaoReverseGeocodingDocument(
                        roadAddress = KakaoRoadAddress("서울특별시 동작구 상도로 369"),
                        address = KakaoLandAddress("서울특별시 동작구 상도동 511"),
                    ),
                ),
            ),
        )
        val provider = KakaoReverseGeocodingProvider(api, configWithKakaoKey)

        val result = provider.getAddress(
            latitude = 37.4963,
            longitude = 126.9574,
        )

        assertEquals("서울특별시 동작구 상도로 369", result)
        assertEquals("KakaoAK kakao-key", api.lastAuthorization)
        assertEquals(126.9574, api.lastLongitude!!, 0.0001)
        assertEquals(37.4963, api.lastLatitude!!, 0.0001)
        assertEquals("WGS84", api.lastInputCoord)
    }

    @Test
    fun getAddress_usesLandAddressWhenRoadAddressIsMissing() = runTest {
        val provider = KakaoReverseGeocodingProvider(
            api = FakeKakaoLocalApi(
                response = KakaoReverseGeocodingResponse(
                    documents = listOf(
                        KakaoReverseGeocodingDocument(
                            roadAddress = null,
                            address = KakaoLandAddress("서울특별시 동작구 상도동 511"),
                        ),
                    ),
                ),
            ),
            config = configWithKakaoKey,
        )

        val result = provider.getAddress(
            latitude = 37.4963,
            longitude = 126.9574,
        )

        assertEquals("서울특별시 동작구 상도동 511", result)
    }

    @Test
    fun getAddress_returnsNullWhenNoAddressIsAvailable() = runTest {
        val provider = KakaoReverseGeocodingProvider(
            api = FakeKakaoLocalApi(
                response = KakaoReverseGeocodingResponse(
                    documents = listOf(
                        KakaoReverseGeocodingDocument(
                            roadAddress = KakaoRoadAddress(""),
                            address = KakaoLandAddress(""),
                        ),
                    ),
                ),
            ),
            config = configWithKakaoKey,
        )

        val result = provider.getAddress(
            latitude = 37.4963,
            longitude = 126.9574,
        )

        assertNull(result)
    }

    @Test
    fun getAddress_requiresKakaoKey() = runTest {
        val provider = KakaoReverseGeocodingProvider(
            api = FakeKakaoLocalApi(KakaoReverseGeocodingResponse()),
            config = configWithMissingKakaoKey,
        )

        val exception = runCatching {
            provider.getAddress(
                latitude = 37.4963,
                longitude = 126.9574,
            )
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException)
    }

    private class FakeKakaoLocalApi(
        private val response: KakaoReverseGeocodingResponse,
    ) : KakaoLocalApi {
        var lastAuthorization: String? = null
            private set
        var lastLongitude: Double? = null
            private set
        var lastLatitude: Double? = null
            private set
        var lastInputCoord: String? = null
            private set

        override suspend fun searchKeyword(
            authorization: String,
            query: String,
            size: Int,
        ): KakaoPlaceSearchResponse {
            return KakaoPlaceSearchResponse()
        }

        override suspend fun coordToAddress(
            authorization: String,
            longitude: Double,
            latitude: Double,
            inputCoord: String,
        ): KakaoReverseGeocodingResponse {
            lastAuthorization = authorization
            lastLongitude = longitude
            lastLatitude = latitude
            lastInputCoord = inputCoord
            return response
        }
    }

    private companion object {
        val configWithKakaoKey = RemoteApiConfig(
            kakaoRestApiKey = "kakao-key",
            odsayApiKey = "",
            googleRoutesApiKey = "",
            seoulOpenApiKey = "",
            seoulBusServiceKey = "",
            tagoServiceKey = "",
        )

        val configWithMissingKakaoKey = RemoteApiConfig(
            kakaoRestApiKey = "",
            odsayApiKey = "",
            googleRoutesApiKey = "",
            seoulOpenApiKey = "",
            seoulBusServiceKey = "",
            tagoServiceKey = "",
        )
    }
}
