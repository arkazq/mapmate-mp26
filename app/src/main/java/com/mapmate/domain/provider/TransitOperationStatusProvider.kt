package com.mapmate.domain.provider

import com.mapmate.domain.model.TransitOperationStatus
import com.mapmate.domain.model.TransitArrivalQuery

interface TransitOperationStatusProvider {
    suspend fun getOperationStatus(query: TransitArrivalQuery): TransitOperationStatus?
}
