package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.KakaoLocalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.KakaoPlaceDocument
import com.mapmate.data.remote.dto.KakaoPlaceSearchResponse
import com.mapmate.data.remote.dto.KakaoReverseGeocodingResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class KakaoPlaceSearchProviderTest {
    @Test fun invalidCoordinatesAreNotOfferedAsSelectableSearchResults() = runTest {
        val api = object : KakaoLocalApi {
            override suspend fun searchKeyword(authorization: String, query: String, size: Int) = KakaoPlaceSearchResponse(
                listOf("37.5", "", "NaN", "90.1", "Infinity").map { latitude ->
                    KakaoPlaceDocument(placeName = "Place $latitude", addressName = "Address", x = "127.0", y = latitude)
                },
            )
            override suspend fun coordToAddress(authorization: String, longitude: Double, latitude: Double, inputCoord: String) =
                KakaoReverseGeocodingResponse()
        }
        val config = RemoteApiConfig("test", "", "", "", "", "")
        assertEquals(listOf("Place 37.5"), KakaoPlaceSearchProvider(api, config).search("Place").map { it.name })
    }
}
