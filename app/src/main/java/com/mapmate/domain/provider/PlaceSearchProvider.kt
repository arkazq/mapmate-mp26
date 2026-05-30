package com.mapmate.domain.provider

import com.mapmate.domain.model.Destination

interface PlaceSearchProvider {
    suspend fun search(query: String): List<Destination>
}
