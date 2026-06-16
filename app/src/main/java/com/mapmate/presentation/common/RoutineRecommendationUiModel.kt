package com.mapmate.presentation.common

import com.mapmate.domain.calculator.DepartureTimeCalculator
import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteEstimate
import com.mapmate.domain.model.RouteSegment
import com.mapmate.domain.model.Routine
import com.mapmate.domain.model.TransportMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

data class RoutineRecommendationUiModel(
    val routine: Routine,
    val recommendedDepartureTimeText: String,
    val calculatedDepartureTimeText: String = recommendedDepartureTimeText,
    val recommendedDepartureTime: LocalTime,
    val calculatedDepartureTime: LocalTime = recommendedDepartureTime,
    val recommendedDepartureAtEpochMillis: Long? = null,
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
    val boardingAdvice: RouteBoardingAdvice? = null,
) {
    val recommendedDepartureDisplayText: String
        get() = if (isImmediateDepartureRecommended) "지금 출발" else recommendedDepartureTimeText

    val departureStatusMessage: String?
        get() = if (isImmediateDepartureRecommended) {
            "계산상 출발 시각 $calculatedDepartureTimeText 이 이미 지나 지금 출발하는 것으로 표시했습니다."
        } else {
            null
        }

    fun minutesUntilDeparture(nowEpochMillis: Long): Int? {
        val departureAt = recommendedDepartureAtEpochMillis ?: return null
        val remainingMillis = departureAt - nowEpochMillis
        if (remainingMillis <= 0L) return 0
        return ceil(remainingMillis / MILLIS_PER_MINUTE.toDouble()).toInt()
    }

    fun departureCountdownText(nowEpochMillis: Long): String {
        val remainingMinutes = minutesUntilDeparture(nowEpochMillis)
        return when {
            isImmediateDepartureRecommended || remainingMinutes == 0 -> "지금 출발하는 것이 좋아요"
            remainingMinutes != null -> "출발까지 ${remainingMinutes.toHoursAndMinutesText()} 남았어요"
            else -> "출발 준비 시간을 계산하고 있어요"
        }
    }

    fun departureProgress(nowEpochMillis: Long): Float {
        val remainingMinutes = minutesUntilDeparture(nowEpochMillis) ?: return 0f
        return (1f - (remainingMinutes.toFloat() / COUNTDOWN_PROGRESS_WINDOW_MINUTES))
            .coerceIn(0f, 1f)
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        const val COUNTDOWN_PROGRESS_WINDOW_MINUTES = 60f

        fun Int.toHoursAndMinutesText(): String {
            val hours = this / 60
            val minutes = this % 60
            return if (hours > 0) {
                "${hours}시간 ${minutes}분"
            } else {
                "${minutes}분"
            }
        }
    }
}

fun Routine.toRecommendationUiModel(
    routeEstimate: RouteEstimate,
    departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    now: LocalTime = LocalTime.now(),
    recommendedDepartureAtEpochMillis: Long? = null,
    displayedDepartureTime: LocalTime? = null,
    isImmediateDepartureOverride: Boolean = false,
): RoutineRecommendationUiModel {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val departureRecommendation = departureTimeCalculator.calculateWithNowClamp(
        targetArrivalTime = targetArrivalTime,
        routeDurationMinutes = routeEstimate.estimatedMinutes,
        personalBufferMinutes = personalBufferMinutes,
        safetyMarginMinutes = safetyMarginMinutes,
        now = now,
    )
    val recommendedDepartureTime = displayedDepartureTime ?: departureRecommendation.recommendedDepartureTime

    return RoutineRecommendationUiModel(
        routine = this,
        recommendedDepartureTimeText = recommendedDepartureTime.format(formatter),
        calculatedDepartureTimeText = departureRecommendation.calculatedDepartureTime.format(formatter),
        recommendedDepartureTime = recommendedDepartureTime,
        calculatedDepartureTime = departureRecommendation.calculatedDepartureTime,
        recommendedDepartureAtEpochMillis = recommendedDepartureAtEpochMillis,
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
        isImmediateDepartureRecommended = isImmediateDepartureOverride ||
            departureRecommendation.isImmediateDepartureRecommended,
        routeSegments = routeEstimate.segments,
        boardingAdvice = routeEstimate.boardingAdvice,
    )
}

fun Routine.toFallbackRecommendationUiModel(
    departureTimeCalculator: DepartureTimeCalculator = DepartureTimeCalculator(),
    now: LocalTime = LocalTime.now(),
    recommendedDepartureAtEpochMillis: Long? = null,
    displayedDepartureTime: LocalTime? = null,
    isImmediateDepartureOverride: Boolean = false,
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
        recommendedDepartureAtEpochMillis = recommendedDepartureAtEpochMillis,
        displayedDepartureTime = displayedDepartureTime,
        isImmediateDepartureOverride = isImmediateDepartureOverride,
    )
}
