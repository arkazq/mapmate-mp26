package com.mapmate.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KakaoPlaceSearchResponse(
    val documents: List<KakaoPlaceDocument> = emptyList(),
)

@Serializable
data class KakaoPlaceDocument(
    @SerialName("place_name")
    val placeName: String = "",
    @SerialName("address_name")
    val addressName: String = "",
    @SerialName("road_address_name")
    val roadAddressName: String = "",
    val x: String = "",
    val y: String = "",
)
