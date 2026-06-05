package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.provider.PlaceSearchProvider

class FallbackPlaceSearchProvider(
    private val primary: PlaceSearchProvider,
    private val fallback: PlaceSearchProvider,
) : PlaceSearchProvider {
    override suspend fun search(query: String): List<Destination> {
        val primaryResult = runCatching {
            primary.search(query)
        }.getOrDefault(emptyList())

        return primaryResult.ifEmpty {
            fallback.search(query)
        }
    }
}
