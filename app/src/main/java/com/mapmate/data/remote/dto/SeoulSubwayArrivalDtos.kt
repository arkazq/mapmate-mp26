package com.mapmate.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeoulSubwayArrivalResponse(
    @SerialName("realtimeArrivalList")
    val realtimeArrivalList: List<SeoulSubwayArrivalItem> = emptyList(),
    @SerialName("errorMessage")
    val errorMessage: SeoulSubwayError? = null,
)

@Serializable
data class SeoulSubwayArrivalItem(
    @SerialName("barvlDt")
    val arrivalSeconds: String? = null,
    @SerialName("arvlMsg2")
    val arrivalMessage: String? = null,
    @SerialName("trainLineNm")
    val trainLineName: String? = null,
    @SerialName("updnLine")
    val direction: String? = null,
    @SerialName("bstatnNm")
    val terminalStationName: String? = null,
)

@Serializable
data class SeoulSubwayError(
    val status: Int? = null,
    val code: String? = null,
    val message: String? = null,
)
