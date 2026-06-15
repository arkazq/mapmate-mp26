package com.mapmate.data.remote.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCandidateEvaluatorTest {
    private val evaluator = RouteCandidateEvaluator()

    @Test
    fun select_prefersBoardableBusOverMissRiskCandidate() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    adjustedTotalMinutes = 35,
                    accessMinutes = 6,
                    realtimeWaitMinutes = 3,
                ),
                candidate(
                    pathIndex = 1,
                    adjustedTotalMinutes = 40,
                    accessMinutes = 5,
                    realtimeWaitMinutes = 9,
                    routeName = "740",
                ),
            ),
        )

        assertEquals(1, selection?.selected?.input?.pathIndex)
        assertEquals(BoardingStatus.BOARDABLE, selection?.selected?.boardingStatus)
        assertEquals(4, selection?.selected?.boardingSlackMinutes)
        assertTrue(selection?.reason.orEmpty().contains("740 slack=4 min"))
    }

    @Test
    fun select_marksZeroMinuteSlackAsTightBoarding() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    accessMinutes = 5,
                    realtimeWaitMinutes = 5,
                ),
            ),
        )

        assertEquals(BoardingStatus.TIGHT, selection?.selected?.boardingStatus)
        assertEquals(0, selection?.selected?.boardingSlackMinutes)
    }

    @Test
    fun select_marksTwoMinuteSlackAsTightBoarding() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    accessMinutes = 5,
                    realtimeWaitMinutes = 7,
                ),
            ),
        )

        assertEquals(BoardingStatus.TIGHT, selection?.selected?.boardingStatus)
        assertEquals(2, selection?.selected?.boardingSlackMinutes)
    }

    @Test
    fun select_marksThreeMinuteSlackAsBoardable() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    accessMinutes = 5,
                    realtimeWaitMinutes = 8,
                ),
            ),
        )

        assertEquals(BoardingStatus.BOARDABLE, selection?.selected?.boardingStatus)
        assertEquals(3, selection?.selected?.boardingSlackMinutes)
    }

    @Test
    fun select_keepsFirstCandidateWhenRealtimeIsUnavailableAndScoreGainIsSmall() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    adjustedTotalMinutes = 40,
                    realtimeWaitMinutes = null,
                ),
                candidate(
                    pathIndex = 1,
                    adjustedTotalMinutes = 38,
                    realtimeWaitMinutes = null,
                ),
            ),
        )

        assertEquals(0, selection?.selected?.input?.pathIndex)
        assertEquals(BoardingStatus.REALTIME_UNAVAILABLE, selection?.selected?.boardingStatus)
    }

    @Test
    fun select_usesNowWhenScheduledDepartureAlreadyPassed() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 5,
                    scheduledDepartureEpochMillis = -60_000L,
                    nowEpochMillis = 0L,
                ),
            ),
        )

        assertEquals(BoardingStatus.TIGHT, selection?.selected?.boardingStatus)
        assertEquals(1, selection?.selected?.boardingSlackMinutes)
    }

    private fun candidate(
        pathIndex: Int,
        adjustedTotalMinutes: Int = 40,
        transferCount: Int = 0,
        walkingMinutes: Int = 5,
        realtimeStatusRank: Int = 0,
        routeName: String = "753",
        accessMinutes: Int = 5,
        realtimeWaitMinutes: Int? = 8,
        scheduledDepartureEpochMillis: Long? = 0L,
        nowEpochMillis: Long = 0L,
    ): RouteCandidateEvaluationInput {
        return RouteCandidateEvaluationInput(
            pathIndex = pathIndex,
            adjustedTotalMinutes = adjustedTotalMinutes,
            transferCount = transferCount,
            walkingMinutes = walkingMinutes,
            realtimeStatusRank = realtimeStatusRank,
            firstBusBoarding = RouteCandidateBoardingInput(
                routeName = routeName,
                stationName = "Start stop",
                accessMinutes = accessMinutes,
                realtimeWaitMinutes = realtimeWaitMinutes,
                scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
                nowEpochMillis = nowEpochMillis,
            ),
        )
    }
}
