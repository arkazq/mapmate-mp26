package com.mapmate.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GoogleRoutesRequest(
    val origin: GoogleWaypoint,
    val destination: GoogleWaypoint,
    val travelMode: String,
    val routingPreference: String? = null,
    val languageCode: String = "ko-KR",
    val units: String = "METRIC",
)

@Serializable
data class GoogleWaypoint(
    val location: GoogleLocation,
)

@Serializable
data class GoogleLocation(
    val latLng: GoogleLatLng,
)

@Serializable
data class GoogleLatLng(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class GoogleRoutesResponse(
    val routes: List<GoogleRoute> = emptyList(),
)

@Serializable
data class GoogleRoute(
    val duration: String? = null,
    val description: String? = null,
    val localizedValues: GoogleLocalizedValues? = null,
)

@Serializable
data class GoogleLocalizedValues(
    val duration: GoogleLocalizedText? = null,
)

@Serializable
data class GoogleLocalizedText(
    val text: String? = null,
)
