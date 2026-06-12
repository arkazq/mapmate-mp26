package com.mapmate.data.remote.api

import com.mapmate.data.remote.dto.SeoulSubwayArrivalResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface SeoulSubwayRealtimeApi {
    @GET("api/subway/{apiKey}/json/realtimeStationArrival/0/5/{stationName}")
    suspend fun getRealtimeStationArrival(
        @Path("apiKey") apiKey: String,
        @Path("stationName") stationName: String,
    ): SeoulSubwayArrivalResponse
}
