package com.mapmate.domain.model

sealed interface TransitArrivalQuery {
    val stationName: String?

    data class Bus(
        override val stationName: String?,
        val stationId: String?,
        val stationArsId: String?,
        val busRouteId: String?,
        val routeName: String?,
        val stationLatitude: Double? = null,
        val stationLongitude: Double? = null,
    ) : TransitArrivalQuery

    data class Subway(
        override val stationName: String?,
        val lineName: String?,
        val direction: String?,
    ) : TransitArrivalQuery
}
