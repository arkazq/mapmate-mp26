package com.mapmate.data.remote.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

interface SeoulBusArrivalApi {
    @GET("api/rest/arrive/getArrInfoByRouteAll")
    suspend fun getArrivalsByRouteAll(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("busRouteId") busRouteId: String,
    ): ResponseBody

    @GET("api/rest/stationinfo/getStationByUid")
    suspend fun getArrivalsByStationUid(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("arsId") stationArsId: String,
    ): ResponseBody
}
