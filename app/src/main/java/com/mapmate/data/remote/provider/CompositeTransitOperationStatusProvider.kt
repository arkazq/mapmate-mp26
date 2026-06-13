package com.mapmate.data.remote.provider

import com.mapmate.domain.model.TransitArrivalQuery
import com.mapmate.domain.model.TransitOperationStatus
import com.mapmate.domain.provider.TransitOperationStatusProvider

class CompositeTransitOperationStatusProvider(
    private val providers: List<TransitOperationStatusProvider>,
) : TransitOperationStatusProvider {
    override suspend fun getOperationStatus(query: TransitArrivalQuery): TransitOperationStatus? {
        providers.forEach { provider ->
            val result = runCatching {
                provider.getOperationStatus(query)
            }.getOrNull()

            if (result != null) return result
        }

        return null
    }
}
