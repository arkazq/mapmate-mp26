package com.mapmate.data.remote.provider

import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.provider.TransitArrivalProvider

class CompositeTransitArrivalProvider(
    private val providers: List<TransitArrivalProvider>,
) : TransitArrivalProvider {
    override suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate? {
        providers.forEach { provider ->
            val result = runCatching {
                provider.getArrivalEstimate(query)
            }.getOrNull()

            if (result != null) return result
        }

        return null
    }
}
