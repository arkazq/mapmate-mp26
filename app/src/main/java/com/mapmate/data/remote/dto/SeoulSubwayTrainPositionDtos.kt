package com.mapmate.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeoulSubwayTrainPositionResponse(
    val realtimePositionList: List<SeoulSubwayTrainPositionItem> = emptyList(),
)

@Serializable
data class SeoulSubwayTrainPositionItem(
    val statnNm: String? = null,
    val trainNo: String? = null,
    val trainSttus: String? = null,
    val updnLine: String? = null,
    val directAt: String? = null,
)
