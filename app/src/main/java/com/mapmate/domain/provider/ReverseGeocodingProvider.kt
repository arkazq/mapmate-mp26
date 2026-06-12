package com.mapmate.domain.provider

interface ReverseGeocodingProvider {
    suspend fun getAddress(
        latitude: Double,
        longitude: Double,
    ): String?
}
