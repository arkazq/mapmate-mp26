package com.mapmate.data.remote.provider

import com.mapmate.data.remote.api.KakaoLocalApi
import com.mapmate.data.remote.config.RemoteApiConfig
import com.mapmate.domain.model.Destination
import com.mapmate.domain.provider.PlaceSearchProvider

class KakaoPlaceSearchProvider(
    private val api: KakaoLocalApi,
    private val config: RemoteApiConfig,
) : PlaceSearchProvider {
    override suspend fun search(query: String): List<Destination> {
        val normalizedQuery = query.trim()
        check(config.hasKakaoKey) { "Kakao REST API key is missing." }
        check(normalizedQuery.isNotBlank()) { "Kakao place search query is blank." }

        return api.searchKeyword(
            authorization = "KakaoAK ${config.kakaoRestApiKey}",
            query = normalizedQuery,
        ).documents.mapNotNull { document ->
            val latitude = document.y.toDoubleOrNull()
            val longitude = document.x.toDoubleOrNull()
            val name = document.placeName.trim()
            val address = document.roadAddressName.ifBlank { document.addressName }.trim()

            if (name.isBlank() || address.isBlank()) {
                null
            } else {
                Destination(
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude,
                )
            }
        }
    }
}
