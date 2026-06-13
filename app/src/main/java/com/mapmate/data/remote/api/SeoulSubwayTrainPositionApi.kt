package com.mapmate.data.remote.api

import com.mapmate.data.remote.dto.SeoulSubwayTrainPositionResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface SeoulSubwayTrainPositionApi {
    @GET("api/subway/{apiKey}/json/realtimePosition/0/20/{lineName}")
    suspend fun getTrainPositions(
        @Path("apiKey") apiKey: String,
        @Path("lineName") lineName: String,
    ): SeoulSubwayTrainPositionResponse
}
