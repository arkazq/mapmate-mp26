package com.mapmate.data.remote.api

import com.mapmate.data.remote.dto.GoogleRoutesRequest
import com.mapmate.data.remote.dto.GoogleRoutesResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GoogleRoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String,
        @Body request: GoogleRoutesRequest,
    ): GoogleRoutesResponse
}
