package com.mapmate.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KakaoReverseGeocodingResponse(
    val documents: List<KakaoReverseGeocodingDocument> = emptyList(),
)

@Serializable
data class KakaoReverseGeocodingDocument(
    @SerialName("road_address")
    val roadAddress: KakaoRoadAddress? = null,
    val address: KakaoLandAddress? = null,
)

@Serializable
data class KakaoRoadAddress(
    @SerialName("address_name")
    val addressName: String = "",
)

@Serializable
data class KakaoLandAddress(
    @SerialName("address_name")
    val addressName: String = "",
)
