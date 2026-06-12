package com.mapmate.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OdsayRouteResponse(
    val result: OdsayRouteResult? = null,
    val error: OdsayError? = null,
)

@Serializable
data class OdsayRouteResult(
    val path: List<OdsayPath> = emptyList(),
)

@Serializable
data class OdsayPath(
    val info: OdsayPathInfo? = null,
    val subPath: List<OdsaySubPath> = emptyList(),
)

@Serializable
data class OdsayPathInfo(
    val totalTime: Int? = null,
    val payment: Int? = null,
    val firstStartStation: String? = null,
    val lastEndStation: String? = null,
    val busTransitCount: Int? = null,
    val subwayTransitCount: Int? = null,
    val totalWalk: Int? = null,
)

@Serializable
data class OdsayError(
    val code: String? = null,
    val msg: String? = null,
)

@Serializable
data class OdsaySubPath(
    val trafficType: Int? = null,
    val sectionTime: Int? = null,
    val startName: String? = null,
    val endName: String? = null,
    @SerialName("startID")
    val startId: JsonElement? = null,
    @SerialName("endID")
    val endId: JsonElement? = null,
    @SerialName("startArsID")
    val startArsId: JsonElement? = null,
    @SerialName("endArsID")
    val endArsId: JsonElement? = null,
    val lane: List<OdsayLane> = emptyList(),
)

@Serializable
data class OdsayLane(
    val name: String? = null,
    val busNo: String? = null,
    @SerialName("busID")
    val busId: JsonElement? = null,
    @SerialName("routeID")
    val routeId: JsonElement? = null,
    val routeNm: String? = null,
    val subwayCode: Int? = null,
)
