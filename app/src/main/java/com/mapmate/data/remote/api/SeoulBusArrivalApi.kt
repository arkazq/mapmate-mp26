package com.mapmate.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface SeoulBusArrivalApi {
    @Streaming
    @GET("api/rest/arrive/getArrInfoByRouteAll")
    suspend fun getArrivalsByRouteAll(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("busRouteId") busRouteId: String,
    ): ResponseBody

    @Streaming
    @GET("api/rest/stationinfo/getStationByUid")
    suspend fun getArrivalsByStationUid(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("arsId") stationArsId: String,
    ): ResponseBody
}
