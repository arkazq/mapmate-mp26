package com.mapmate.presentation.routine

import com.mapmate.domain.model.AppSettings
import com.mapmate.domain.model.Destination
import com.mapmate.domain.model.RepeatDay
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.TransportMode
import com.mapmate.presentation.common.toKoreanLabel

data class RoutineRegistrationUiState(
    val editingRoutineId: Long? = null,
    val routineName: String = "",
    val originQuery: String = "",
    val selectedOrigin: Destination? = null,
    val originCandidates: List<Destination> = emptyList(),
    val destinationQuery: String = "",
    val selectedDestination: Destination? = null,
    val destinationCandidates: List<Destination> = emptyList(),
    val targetArrivalTimeText: String = "09:00",
    val selectedRepeatDays: Set<RepeatDay> = setOf(
        RepeatDay.MONDAY,
        RepeatDay.TUESDAY,
        RepeatDay.WEDNESDAY,
        RepeatDay.THURSDAY,
        RepeatDay.FRIDAY,
    ),
    val selectedTransportMode: TransportMode = AppSettings.DEFAULT_TRANSPORT_MODE,
    val personalBufferMinutes: String = AppSettings.DEFAULT_PERSONAL_BUFFER_MINUTES.toString(),
    val safetyMarginMinutes: String = AppSettings.DEFAULT_SAFETY_MARGIN_MINUTES.toString(),
    val routeEstimate: RouteEstimate? = null,
    val recommendedDepartureTimeText: String = "",
    val calculatedDepartureTimeText: String = "",
    val isImmediateDepartureRecommended: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isGettingCurrentLocation: Boolean = false,
    val isCalculating: Boolean = false,
    val isSaving: Boolean = false,
    val isSaveEnabled: Boolean = false,
    val isSaveCompleted: Boolean = false,
) {
    val isEditing: Boolean
        get() = editingRoutineId != null

    val hasCalculationResult: Boolean
        get() = routeEstimate != null && recommendedDepartureTimeText.isNotBlank()

    val recommendedDepartureDisplayText: String
        get() = if (isImmediateDepartureRecommended) "지금 출발" else recommendedDepartureTimeText

    val departureStatusMessage: String?
        get() = if (isImmediateDepartureRecommended) {
            "계산상 출발 시각 $calculatedDepartureTimeText 이 이미 지나 지금 출발하는 것으로 표시했습니다."
        } else {
            null
        }

    val calculationSummaryText: String?
        get() = routeEstimate?.toUserFacingSummary(
            transportMode = selectedTransportMode,
            personalBufferMinutes = personalBufferMinutes,
            safetyMarginMinutes = safetyMarginMinutes,
        )
}

private fun RouteEstimate.toUserFacingSummary(
    transportMode: TransportMode,
    personalBufferMinutes: String,
    safetyMarginMinutes: String,
): String {
    val sourceText = when {
        isFallbackEstimate || providerName.contains("Mock", ignoreCase = true) -> {
            "${transportMode.toKoreanLabel()} 기본 예상 시간"
        }
        providerName.contains("ODsay", ignoreCase = true) -> {
            "ODsay 대중교통 경로"
        }
        providerName.contains("Google", ignoreCase = true) -> {
            "Google Routes 경로"
        }
        else -> {
            "${transportMode.toKoreanLabel()} 경로"
        }
    }
    val realtimeText = if (hasRealtimeAdjustment) {
        " 실시간 도착정보를 반영했습니다."
    } else {
        ""
    }
    val bufferText = personalBufferMinutes.takeIf(String::isNotBlank) ?: "0"
    val marginText = safetyMarginMinutes.takeIf(String::isNotBlank) ?: "0"

    return "$sourceText 기준으로 이동 시간 ${estimatedMinutes}분을 계산했습니다.$realtimeText " +
        "개인 보정 ${bufferText}분과 안전 여유 ${marginText}분을 함께 반영했습니다."
}
