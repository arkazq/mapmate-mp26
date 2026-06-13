package com.mapmate.data.remote.api

import com.mapmate.data.remote.dto.TagoBusStationResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface TagoBusStationApi {
    @GET("getCrdntPrxmtSttnList")
    suspend fun getNearbyStations(
        @Query(value = "serviceKey", encoded = true) serviceKey: String,
        @Query("gpsLati") latitude: Double,
        @Query("gpsLong") longitude: Double,
        @Query("pageNo") pageNo: Int = 1,
        @Query("numOfRows") numOfRows: Int = 10,
        @Query("_type") type: String = "json",
    ): TagoBusStationResponse
}
