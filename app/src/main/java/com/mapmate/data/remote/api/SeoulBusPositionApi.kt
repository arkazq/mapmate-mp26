package com.mapmate.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface SeoulBusPositionApi {
    @Streaming
    @GET("api/rest/buspos/getBusPosByRtid")
    suspend fun getBusPositionsByRoute(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("busRouteId") busRouteId: String,
    ): ResponseBody
}
