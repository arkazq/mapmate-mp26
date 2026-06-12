package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.KakaoLocalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.data.remote.dto.KakaoReverseGeocodeDocument
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

        return api.reverseGeocode(
            authorization = "KakaoAK ${config.kakaoRestApiKey}",
            longitude = longitude,
            latitude = latitude,
        ).documents.firstNotNullOfOrNull(::selectAddress)
    }

    companion object {
        fun selectAddress(document: KakaoReverseGeocodeDocument): String? {
            return document.roadAddress?.addressName?.trim()?.takeIf { it.isNotBlank() }
                ?: document.address?.addressName?.trim()?.takeIf { it.isNotBlank() }
        }
    }
}
