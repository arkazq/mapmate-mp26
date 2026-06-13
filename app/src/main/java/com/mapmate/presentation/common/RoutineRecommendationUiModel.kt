package com.mapmate.presentation.common

import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.format.DateTimeFormatter

data class RoutineRecommendationUiModel(
    val routine: Routine,
    val recommendedDepartureTimeText: String,
    val targetArrivalTimeText: String,
    val routeDurationMinutes: Int,
    val personalBufferMinutes: Int,
    val safetyMarginMinutes: Int,
    val routeSummary: String,
    val reason: String,
    val isFallbackEstimate: Boolean = false,
    val routeStatusMessage: String? = null,
)

fun Routine.toRecommendationUiModel(
    routeEstimate: RouteEstimate,
    departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
): RoutineRecommendationUiModel {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val recommendedDepartureTime = departureTimeCalculator.calculate(
        targetArrivalTime = targetArrivalTime,
        routeDurationMinutes = routeEstimate.estimatedMinutes,
        personalBufferMinutes = personalBufferMinutes,
        safetyMarginMinutes = safetyMarginMinutes,
    )

    return RoutineRecommendationUiModel(
        routine = this,
        recommendedDepartureTimeText = recommendedDepartureTime.format(formatter),
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
    )
}

fun Routine.toFallbackRecommendationUiModel(
    departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
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
    )
}
