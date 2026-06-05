package com.mapmate.domain.provider

import com.mapmate.domain.model.Destination

interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): Destination
}
