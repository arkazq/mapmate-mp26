package com.mapmate.domain.model

data class RouteSegment(
    val id: Long? = null,
    val commuteRecordId: Long? = null,
    val routineId: Long? = null,
    val segmentIndex: Int,
    val segmentType: RouteSegmentType,
    val trafficType: Int? = null,
    val routeName: String? = null,
    val startName: String? = null,
    val endName: String? = null,
    val plannedDurationMinutes: Int,
    val actualStartedAtEpochMillis: Long? = null,
    val actualEndedAtEpochMillis: Long? = null,
    val actualDurationMinutes: Int? = null,
    val isUserEdited: Boolean = false,
    val status: RouteSegmentStatus = RouteSegmentStatus.NOT_STARTED,
) {
    fun adjustmentKey(): SegmentAdjustmentKey {
        return SegmentAdjustmentKey(
            routineId = routineId,
            segmentType = segmentType,
            routeName = routeName.normalizedKeyPart(),
            startName = startName.normalizedKeyPart(),
            endName = endName.normalizedKeyPart(),
        )
    }
}

data class SegmentAdjustmentKey(
    val routineId: Long?,
    val segmentType: RouteSegmentType,
    val routeName: String?,
    val startName: String?,
    val endName: String?,
)

enum class RouteSegmentType {
    WALK_TO_TRANSIT,
    BUS_RIDE,
    SUBWAY_RIDE,
    TRANSFER_WALK,
    WALK_TO_DESTINATION,
    UNKNOWN,
}

enum class RouteSegmentStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    SKIPPED,
}

private fun String?.normalizedKeyPart(): String? {
    return this
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)
}
