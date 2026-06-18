package com.mapmate.domain.model

data class TransitArrivalEstimate(
    val waitMinutes: Int,
    val summary: String,
    val providerName: String,
    val reason: String,
    val waitCandidateMinutes: List<Int> = listOf(waitMinutes),
)
