package com.mapmate.presentation.common

import com.mapmate.domain.model.RouteBoardingAdvice
import com.mapmate.domain.model.RouteBoardingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteBoardingAdviceTextTest {
    @Test
    fun missRiskDoesNotPromiseThatLeavingNowMakesTheBusCatchable() {
        assertEquals("지금 출발해도 첫 버스를 놓칠 수 있어요.",
            advice(RouteBoardingStatus.MISS_RISK).safeDepartureMessage())
    }

    @Test
    fun tightDepartureNoticeUsesARecommendationInsteadOfBoardingCertainty() {
        val message = requireNotNull(advice(RouteBoardingStatus.TIGHT).safeDepartureMessage())
        assertTrue(message.contains("출발 권장"))
        assertTrue(message.contains("5분 앞당김"))
        assertFalse(message.contains("여유 있게 탑승 가능"))
    }

    @Test
    fun unavailableRealtimeOrZeroEarlyPullDoesNotShowAnEarlyDepartureNotice() {
        assertNull(advice(RouteBoardingStatus.REALTIME_UNAVAILABLE).copy(realtimeWaitMinutes = null)
            .safeDepartureMessage())
        assertNull(advice(RouteBoardingStatus.BOARDABLE).copy(earlyDepartureRequiredMinutes = 0)
            .safeDepartureMessage())
    }

    private fun advice(status: RouteBoardingStatus) = RouteBoardingAdvice(
        selectedCandidateIndex = 1, candidateCount = 5, routeName = "753", stationName = "station",
        accessMinutes = 3, realtimeWaitMinutes = 1, slackMinutes = -2, status = status,
        estimatedTotalMinutes = 25, safeDepartureEpochMillis = 1_790_908_800_000L,
        earlyDepartureRequiredMinutes = 5,
    )
}
