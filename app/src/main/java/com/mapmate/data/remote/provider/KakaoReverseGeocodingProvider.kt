package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.KakaoLocalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.provider.ReverseGeocodingProvider

class KakaoReverseGeocodingProvider(
    private val api: KakaoLocalApi,
    private val config: RemoteApiConfig,
) : ReverseGeocodingProvider {
    override suspend fun getAddress(
        latitude: Double,
        longitude: Double,
    ): String? {
        check(config.hasKakaoKey) { "Kakao REST API key is missing." }

        return api.coordToAddress(
            authorization = "KakaoAK ${config.kakaoRestApiKey}",
            longitude = longitude,
            latitude = latitude,
        ).documents.firstNotNullOfOrNull { document ->
            document.roadAddress?.addressName?.takeIf(String::isNotBlank)
                ?: document.address?.addressName?.takeIf(String::isNotBlank)
        }
    }
}
