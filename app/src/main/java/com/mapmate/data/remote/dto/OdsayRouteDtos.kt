package com.mapmate.data.remote.dto

import kotlinx.serialization.Serializable

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
