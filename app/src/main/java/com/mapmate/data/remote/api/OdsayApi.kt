package com.mapmate.data.remote.api

import com.mapmate.data.remote.dto.OdsayRouteResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OdsayApi {
    @GET("v1/api/searchPubTransPathT")
    suspend fun searchPublicTransitPath(
        @Query("SX") startLongitude: Double,
        @Query("SY") startLatitude: Double,
        @Query("EX") endLongitude: Double,
        @Query("EY") endLatitude: Double,
        @Query("apiKey") apiKey: String,
    ): OdsayRouteResponse
}
