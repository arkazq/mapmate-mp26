package com.mapmate.presentation.common

import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class RoutineRecommendationUiModel(
    val routine: Routine,
    val recommendedDepartureTimeText: String,
    val calculatedDepartureTimeText: String = recommendedDepartureTimeText,
    val targetArrivalTimeText: String,
    val routeDurationMinutes: Int,
    val personalBufferMinutes: Int,
    val safetyMarginMinutes: Int,
    val routeSummary: String,
    val reason: String,
    val isFallbackEstimate: Boolean = false,
    val routeStatusMessage: String? = null,
    val isImmediateDepartureRecommended: Boolean = false,
    val routeSegments: List<RouteSegment> = emptyList(),
) {
    val recommendedDepartureDisplayText: String
        get() = if (isImmediateDepartureRecommended) "지금 출발" else recommendedDepartureTimeText

    val departureStatusMessage: String?
        get() = if (isImmediateDepartureRecommended) {
            "계산상 출발 시각 $calculatedDepartureTimeText 이 이미 지나 지금 출발하는 것으로 표시했습니다."
        } else {
            null
        }
}

fun Routine.toRecommendationUiModel(
    routeEstimate: RouteEstimate,
    departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    now: LocalTime = LocalTime.now(),
): RoutineRecommendationUiModel {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val departureRecommendation = departureTimeCalculator.calculateWithNowClamp(
        targetArrivalTime = targetArrivalTime,
        routeDurationMinutes = routeEstimate.estimatedMinutes,
        personalBufferMinutes = personalBufferMinutes,
        safetyMarginMinutes = safetyMarginMinutes,
        now = now,
    )

    return RoutineRecommendationUiModel(
        routine = this,
        recommendedDepartureTimeText = departureRecommendation.recommendedDepartureTime.format(formatter),
        calculatedDepartureTimeText = departureRecommendation.calculatedDepartureTime.format(formatter),
        targetArrivalTimeText = targetArrivalTime.format(formatter),
        routeDurationMinutes = routeEstimate.estimatedMinutes,
        personalBufferMinutes = personalBufferMinutes,
        safetyMarginMinutes = safetyMarginMinutes,
        routeSummary = routeEstimate.summary,
        reason = routeEstimate.reason.ifBlank {
            "기본 예상 이동 시간과 보정 ${personalBufferMinutes}분 반영"
        },
        isFallbackEstimate = routeEstimate.isFallbackEstimate,
        routeStatusMessage = routeEstimate.statusMessage,
        isImmediateDepartureRecommended = departureRecommendation.isImmediateDepartureRecommended,
        routeSegments = routeEstimate.segments,
    )
}

fun Routine.toFallbackRecommendationUiModel(
    departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    now: LocalTime = LocalTime.now(),
): RoutineRecommendationUiModel {
    val estimatedMinutes = when (transportMode) {
        TransportMode.TRANSIT -> 42
        TransportMode.WALK -> 25
        TransportMode.CAR -> 30
    }
    val routeEstimate = RouteEstimate(
        estimatedMinutes = estimatedMinutes,
        summary = "${origin.name}에서 ${destination.name}까지 ${transportMode.toKoreanLabel()} 기준 ${estimatedMinutes}분 예상",
        providerName = "MapMateFallback",
        reason = "기본 예상 이동 시간과 보정 ${personalBufferMinutes}분 반영",
        isFallbackEstimate = true,
        statusMessage = "경로 계산에 실패해 기본 예상 시간을 사용했습니다.",
    )

    return toRecommendationUiModel(
        routeEstimate = routeEstimate,
        departureTimeCalculator = departureTimeCalculator,
        now = now,
    )
}
