package com.mapmate.data.remote.api

import com.mapmate.data.remote.dto.KakaoPlaceSearchResponse
import com.mapmate.data.remote.dto.KakaoReverseGeocodeResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoLocalApi {
    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Header("Authorization") authorization: String,
        @Query("query") query: String,
        @Query("size") size: Int = 5,
    ): KakaoPlaceSearchResponse

    @GET("v2/local/geo/coord2address.json")
    suspend fun reverseGeocode(
        @Header("Authorization") authorization: String,
        @Query("x") longitude: Double,
        @Query("y") latitude: Double,
    ): KakaoReverseGeocodeResponse
}
