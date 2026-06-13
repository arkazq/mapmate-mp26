package com.mapmate.data.remote.api

import com.mapmate.data.remote.dto.TagoBusArrivalResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TagoBusArrivalApi {
    @GET("getSttnAcctoArvlPrearngeInfoList")
    suspend fun getArrivalsByStation(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("cityCode") cityCode: String,
        @Query("nodeId") nodeId: String,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 20,
        @Query("_type") type: String = "json",
    ): TagoBusArrivalResponse
}
