package com.mapmate.domain.model

data class SegmentTimeAdjustment(
    val id: Long? = null,
    val routineId: Long,
    val segmentType: RouteSegmentType,
    val routeName: String? = null,
    val startName: String? = null,
    val endName: String? = null,
    val averageDelayMinutes: Int,
    val sampleCount: Int,
    val confidence: Double,
    val updatedAtEpochMillis: Long,
) {
    fun matches(segment: RouteSegment): Boolean {
        val key = segment.adjustmentKey()
        return routineId == key.routineId &&
            segmentType == key.segmentType &&
            routeName.normalizedKeyPart() == key.routeName &&
            startName.normalizedKeyPart() == key.startName &&
            endName.normalizedKeyPart() == key.endName
    }
}

private fun String?.normalizedKeyPart(): String? {
    return this
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)
}
