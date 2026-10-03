package com.mapmate.data.remote.provider

import com.mapmate.domain.model.Destination
import com.mapmate.domain.provider.PlaceSearchProvider
import com.mapmate.domain.util.runCatchingCancellable

class FallbackPlaceSearchProvider(
    private val primary: PlaceSearchProvider,
    private val fallback: PlaceSearchProvider,
) : PlaceSearchProvider {
    override suspend fun search(query: String): List<Destination> {
        val primaryResult = runCatchingCancellable {
            primary.search(query)
        }
        primaryResult.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        val fallbackResult = fallback.search(query)
        if (fallbackResult.isNotEmpty()) return fallbackResult
        primaryResult.exceptionOrNull()?.let { throw it }
        return emptyList()
    }
}
