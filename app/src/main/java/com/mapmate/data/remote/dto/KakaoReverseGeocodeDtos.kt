package com.mapmate.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KakaoReverseGeocodeResponse(
    val documents: List<KakaoReverseGeocodeDocument> = emptyList(),
)

@Serializable
data class KakaoReverseGeocodeDocument(
    @SerialName("road_address")
    val roadAddress: KakaoReverseGeocodeAddress? = null,
    val address: KakaoReverseGeocodeAddress? = null,
)

@Serializable
data class KakaoReverseGeocodeAddress(
    @SerialName("address_name")
    val addressName: String = "",
)
