package com.mapmate.domain.provider

import com.mapmate.domain.model.TransitArrivalEstimate
import com.mapmate.domain.model.TransitArrivalQuery

interface TransitArrivalProvider {
    suspend fun getArrivalEstimate(query: TransitArrivalQuery): TransitArrivalEstimate?
}
