package com.mapmate.data.remote.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun select_calculatesSafeDepartureWhenBusRequiresEarlierDeparture() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    adjustedTotalMinutes = 26,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 13,
                    scheduledDepartureEpochMillis = minutes(21),
                    targetArrivalEpochMillis = minutes(55),
                    nowEpochMillis = 0L,
                ),
            ),
        )

        assertEquals(minutes(6), selection?.selected?.safeDepartureEpochMillis)
        assertEquals(15, selection?.selected?.earlyDepartureRequiredMinutes)
        assertEquals(3, selection?.selected?.boardingSlackMinutes)
        assertEquals(BoardingStatus.BOARDABLE, selection?.selected?.boardingStatus)
        assertEquals(false, selection?.selected?.mayMissTargetArrival)
    }

    @Test
    fun select_prefersCandidateThatCanStillMeetTargetArrival() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    adjustedTotalMinutes = 60,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 13,
                    scheduledDepartureEpochMillis = minutes(21),
                    targetArrivalEpochMillis = minutes(55),
                    nowEpochMillis = 0L,
                ),
                candidate(
                    pathIndex = 1,
                    adjustedTotalMinutes = 45,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 14,
                    scheduledDepartureEpochMillis = minutes(21),
                    targetArrivalEpochMillis = minutes(55),
                    nowEpochMillis = 0L,
                ),
            ),
        )

        assertEquals(1, selection?.selected?.input?.pathIndex)
        assertEquals(false, selection?.selected?.mayMissTargetArrival)
    }

    @Test
    fun select_prioritizesTargetArrivalOverSmallerEarlyDeparture() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    adjustedTotalMinutes = 35,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 13,
                    scheduledDepartureEpochMillis = minutes(21),
                    targetArrivalEpochMillis = minutes(50),
                    nowEpochMillis = 0L,
                ),
                candidate(
                    pathIndex = 1,
                    adjustedTotalMinutes = 50,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 26,
                    scheduledDepartureEpochMillis = minutes(21),
                    targetArrivalEpochMillis = minutes(50),
                    nowEpochMillis = 0L,
                ),
            ),
        )

        assertEquals(0, selection?.selected?.input?.pathIndex)
        assertEquals(15, selection?.selected?.earlyDepartureRequiredMinutes)
        assertEquals(false, selection?.selected?.mayMissTargetArrival)
        val lessEarlyButLateCandidate = selection?.evaluations
            ?.firstOrNull { it.input.pathIndex == 1 }
        assertEquals(2, lessEarlyButLateCandidate?.earlyDepartureRequiredMinutes)
        assertEquals(true, lessEarlyButLateCandidate?.mayMissTargetArrival)
    }

    @Test
    fun select_fallsBackToTargetBasedDepartureWhenRealtimeIsUnavailableAndTargetArrivalIsReachable() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    realtimeWaitMinutes = null,
                    scheduledDepartureEpochMillis = minutes(21),
                    targetArrivalEpochMillis = minutes(70),
                ),
            ),
        )

        assertNull(selection?.selected?.safeDepartureEpochMillis)
        assertEquals(0, selection?.selected?.earlyDepartureRequiredMinutes)
        assertEquals(false, selection?.selected?.mayMissTargetArrival)
        assertEquals(BoardingStatus.REALTIME_UNAVAILABLE, selection?.selected?.boardingStatus)
    }

    @Test
    fun select_clampsSafeDepartureToNowWhenCalculatedDepartureAlreadyPassed() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 5,
                    scheduledDepartureEpochMillis = minutes(21),
                    targetArrivalEpochMillis = minutes(55),
                    nowEpochMillis = minutes(10),
                ),
            ),
        )

        assertEquals(minutes(10), selection?.selected?.safeDepartureEpochMillis)
        assertEquals(11, selection?.selected?.earlyDepartureRequiredMinutes)
        assertEquals(false, selection?.selected?.mayMissTargetArrival)
    }

    @Test
    fun select_doesNotShowNegativeEarlyDepartureWhenScheduledDepartureAlreadyPassed() {
        val selection = evaluator.select(
            listOf(
                candidate(
                    pathIndex = 0,
                    accessMinutes = 4,
                    realtimeWaitMinutes = 5,
                    scheduledDepartureEpochMillis = -minutes(1),
                    targetArrivalEpochMillis = minutes(55),
                    nowEpochMillis = 0L,
                ),
            ),
        )

        assertNull(selection?.selected?.safeDepartureEpochMillis)
        assertEquals(0, selection?.selected?.earlyDepartureRequiredMinutes)
        assertEquals(false, selection?.selected?.mayMissTargetArrival)
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
        targetArrivalEpochMillis: Long? = null,
        nowEpochMillis: Long = 0L,
    ): RouteCandidateEvaluationInput {
        return RouteCandidateEvaluationInput(
            pathIndex = pathIndex,
            adjustedTotalMinutes = adjustedTotalMinutes,
            transferCount = transferCount,
            walkingMinutes = walkingMinutes,
            realtimeStatusRank = realtimeStatusRank,
            scheduledDepartureEpochMillis = scheduledDepartureEpochMillis,
            targetArrivalEpochMillis = targetArrivalEpochMillis,
            nowEpochMillis = nowEpochMillis,
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

    private fun minutes(value: Int): Long = value * 60_000L
}
