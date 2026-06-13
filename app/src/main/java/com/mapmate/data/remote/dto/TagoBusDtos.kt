package com.mapmate.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TagoBusStationResponse(
    val response: TagoBusStationEnvelope? = null,
)

@Serializable
data class TagoBusStationEnvelope(
    val header: TagoResponseHeader? = null,
    val body: TagoBusStationBody? = null,
)

@Serializable
data class TagoBusStationBody(
    val items: TagoBusStationItems? = null,
)

@Serializable
data class TagoBusStationItems(
    val item: List<TagoBusStationItem> = emptyList(),
)

@Serializable
data class TagoBusStationItem(
    val citycode: String? = null,
    val nodeid: String? = null,
    val nodenm: String? = null,
    val gpslati: Double? = null,
    val gpslong: Double? = null,
)

@Serializable
data class TagoBusArrivalResponse(
    val response: TagoBusArrivalEnvelope? = null,
)

@Serializable
data class TagoBusArrivalEnvelope(
    val header: TagoResponseHeader? = null,
    val body: TagoBusArrivalBody? = null,
)

@Serializable
data class TagoBusArrivalBody(
    val items: TagoBusArrivalItems? = null,
)

@Serializable
data class TagoBusArrivalItems(
    val item: List<TagoBusArrivalItem> = emptyList(),
)

@Serializable
data class TagoBusArrivalItem(
    val nodeid: String? = null,
    val nodenm: String? = null,
    val routeid: String? = null,
    val routeno: String? = null,
    val routetp: String? = null,
    val arrprevstationcnt: Int? = null,
    val vehicletp: String? = null,
    val arrtime: Int? = null,
)

@Serializable
data class TagoResponseHeader(
    val resultCode: String? = null,
    val resultMsg: String? = null,
)
