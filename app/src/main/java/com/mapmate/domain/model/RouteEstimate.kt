package com.mapmate.domain.model

data class RouteEstimate(
    val estimatedMinutes: Int,
    val summary: String,
    val providerName: String,
    val reason: String,
    val isFallbackEstimate: Boolean = false,
    val statusMessage: String? = null,
)
