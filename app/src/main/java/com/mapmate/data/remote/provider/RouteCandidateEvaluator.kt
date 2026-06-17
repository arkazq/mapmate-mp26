package com.mapmate.data.remote.provider

internal class RouteCandidateEvaluator(
    private val minRouteSwitchGainMinutes: Int = DEFAULT_MIN_ROUTE_SWITCH_GAIN_MINUTES,
) {
    fun select(candidates: List<RouteCandidateEvaluationInput>): RouteCandidateSelection? {
        if (candidates.isEmpty()) return null

        val evaluations = candidates.map { it.evaluate() }
        val firstCandidate = evaluations.minByOrNull { it.input.pathIndex } ?: return null
        val bestCandidate = evaluations.minWithOrNull(
            compareBy<RouteCandidateEvaluation> { it.score }
                .thenBy { it.input.realtimeStatusRank }
                .thenBy { it.input.transferCount }
                .thenBy { it.input.walkingMinutes }
                .thenBy { it.input.pathIndex },
        ) ?: firstCandidate
        val scoreGain = firstCandidate.score - bestCandidate.score
        val selected = if (
            bestCandidate.input.pathIndex != firstCandidate.input.pathIndex &&
            scoreGain < minRouteSwitchGainMinutes
        ) {
            firstCandidate
        } else {
            bestCandidate
        }

        return RouteCandidateSelection(
            selected = selected,
            evaluations = evaluations,
            reason = buildSelectionReason(
                selected = selected,
                firstCandidate = firstCandidate,
                bestCandidate = bestCandidate,
                scoreGain = scoreGain,
            ),
        )
    }

    private fun RouteCandidateEvaluationInput.evaluate(): RouteCandidateEvaluation {
        val boarding = firstBusBoarding
        val boardingSlackMinutes = boarding
            ?.takeIf { it.realtimeWaitMinutes != null }
            ?.boardingSlackMinutes()
        val boardingSafeDepartureEpochMillis = boarding?.boardingSafeDepartureEpochMillis()
        val finalDepartureEpochMillis = finalDepartureEpochMillis(boardingSafeDepartureEpochMillis)
        val earlyDepartureRequiredMinutes = earlyDepartureRequiredMinutes(finalDepartureEpochMillis)
        val visibleSafeDepartureEpochMillis = finalDepartureEpochMillis
            ?.takeIf { boardingSafeDepartureEpochMillis != null }
            ?.takeIf { (earlyDepartureRequiredMinutes ?: 0) > 0 }
        val targetArrivalRiskPenalty = targetArrivalRiskPenalty(finalDepartureEpochMillis)
        val earlyDeparturePenalty = earlyDeparturePenalty(earlyDepartureRequiredMinutes)
        val boardingStatus = boardingStatus(boardingSlackMinutes = boardingSlackMinutes, boarding = boarding)
        val transferPenalty = transferCount * TRANSFER_PENALTY_MINUTES
        val walkingPenalty = walkingMinutes / WALKING_PENALTY_DIVISOR
        val realtimeConfidenceBonus = if (boarding?.realtimeWaitMinutes != null) {
            REALTIME_CONFIDENCE_BONUS_MINUTES
        } else {
            0
        }
        val score = adjustedTotalMinutes +
            boardingStatus.penaltyMinutes +
            transferPenalty +
            walkingPenalty +
            targetArrivalRiskPenalty +
            earlyDeparturePenalty -
            realtimeConfidenceBonus

        return RouteCandidateEvaluation(
            input = this,
            score = score,
            boardingSlackMinutes = boardingSlackMinutes,
            boardingStatus = boardingStatus,
            safeDepartureEpochMillis = visibleSafeDepartureEpochMillis,
            earlyDepartureRequiredMinutes = earlyDepartureRequiredMinutes,
            mayMissTargetArrival = targetArrivalRiskPenalty > 0,
            reason = buildEvaluationReason(
                boarding = boarding,
                boardingSlackMinutes = boardingSlackMinutes,
                boardingStatus = boardingStatus,
                transferPenalty = transferPenalty,
                walkingPenalty = walkingPenalty,
                targetArrivalRiskPenalty = targetArrivalRiskPenalty,
                earlyDeparturePenalty = earlyDeparturePenalty,
                earlyDepartureRequiredMinutes = earlyDepartureRequiredMinutes,
                realtimeConfidenceBonus = realtimeConfidenceBonus,
                score = score,
            ),
        )
    }

    private fun RouteCandidateBoardingInput.boardingSlackMinutes(): Int {
        val departureBaseEpochMillis = maxOf(
            scheduledDepartureEpochMillis ?: nowEpochMillis,
            nowEpochMillis,
        )
        val passengerArrivalEpochMillis = departureBaseEpochMillis +
            accessMinutes * MILLIS_PER_MINUTE
        val busArrivalEpochMillis = nowEpochMillis +
            requireNotNull(realtimeWaitMinutes) * MILLIS_PER_MINUTE

        return ((busArrivalEpochMillis - passengerArrivalEpochMillis) / MILLIS_PER_MINUTE).toInt()
    }

    private fun RouteCandidateBoardingInput.boardingSafeDepartureEpochMillis(): Long? {
        val waitMinutes = realtimeWaitMinutes ?: return null
        return nowEpochMillis +
            waitMinutes * MILLIS_PER_MINUTE -
            accessMinutes * MILLIS_PER_MINUTE -
            MIN_BOARDING_SLACK_MINUTES * MILLIS_PER_MINUTE
    }

    private fun RouteCandidateEvaluationInput.finalDepartureEpochMillis(
        boardingSafeDepartureEpochMillis: Long?,
    ): Long? {
        val targetBasedDeparture = scheduledDepartureEpochMillis ?: return boardingSafeDepartureEpochMillis
        val calculatedFinalDeparture = boardingSafeDepartureEpochMillis
            ?.let { minOf(targetBasedDeparture, it) }
            ?: targetBasedDeparture
        return maxOf(calculatedFinalDeparture, nowEpochMillis)
    }

    private fun RouteCandidateEvaluationInput.earlyDepartureRequiredMinutes(
        finalDepartureEpochMillis: Long?,
    ): Int? {
        val scheduledDeparture = scheduledDepartureEpochMillis ?: return null
        val finalDeparture = finalDepartureEpochMillis ?: return null
        val earlyMillis = scheduledDeparture - finalDeparture
        if (earlyMillis <= 0L) return 0
        return (earlyMillis / MILLIS_PER_MINUTE).toInt()
    }

    private fun RouteCandidateEvaluationInput.targetArrivalRiskPenalty(
        finalDepartureEpochMillis: Long?,
    ): Int {
        val targetArrival = targetArrivalEpochMillis ?: return 0
        val departure = finalDepartureEpochMillis ?: return 0
        val arrival = departure + adjustedTotalMinutes * MILLIS_PER_MINUTE
        val lateMillis = arrival - targetArrival
        return if (lateMillis > 0) {
            val lateMinutes = ((lateMillis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE).toInt()
            TARGET_ARRIVAL_MISS_PENALTY_MINUTES + lateMinutes
        } else {
            0
        }
    }

    private fun earlyDeparturePenalty(earlyDepartureRequiredMinutes: Int?): Int {
        val earlyMinutes = earlyDepartureRequiredMinutes ?: return 0
        if (earlyMinutes <= 0) return 0
        return if (earlyMinutes > MAX_EARLY_PULL_MINUTES) {
            EARLY_DEPARTURE_OVER_LIMIT_PENALTY_MINUTES + (earlyMinutes - MAX_EARLY_PULL_MINUTES) / 2
        } else {
            earlyMinutes / EARLY_DEPARTURE_PENALTY_DIVISOR
        }
    }

    private fun boardingStatus(
        boardingSlackMinutes: Int?,
        boarding: RouteCandidateBoardingInput?,
    ): BoardingStatus {
        if (boarding == null) return BoardingStatus.NO_FIRST_BUS
        if (boardingSlackMinutes == null) return BoardingStatus.REALTIME_UNAVAILABLE

        return when {
            boardingSlackMinutes < 0 -> BoardingStatus.MISS_RISK
            boardingSlackMinutes <= TIGHT_BOARDING_MAX_SLACK_MINUTES -> BoardingStatus.TIGHT
            else -> BoardingStatus.BOARDABLE
        }
    }

    private fun buildSelectionReason(
        selected: RouteCandidateEvaluation,
        firstCandidate: RouteCandidateEvaluation,
        bestCandidate: RouteCandidateEvaluation,
        scoreGain: Int,
    ): String {
        val stabilityReason = if (
            bestCandidate.input.pathIndex != firstCandidate.input.pathIndex &&
            selected.input.pathIndex == firstCandidate.input.pathIndex
        ) {
            "Kept first candidate because score gain ${scoreGain} min is below ${minRouteSwitchGainMinutes} min."
        } else {
            null
        }

        return listOfNotNull(
            "Boarding-aware candidate score selected ${selected.input.pathIndex + 1}; score=${selected.score}; ${selected.reason}",
            stabilityReason,
        ).joinToString(" ")
    }

    private fun buildEvaluationReason(
        boarding: RouteCandidateBoardingInput?,
        boardingSlackMinutes: Int?,
        boardingStatus: BoardingStatus,
        transferPenalty: Int,
        walkingPenalty: Int,
        targetArrivalRiskPenalty: Int,
        earlyDeparturePenalty: Int,
        earlyDepartureRequiredMinutes: Int?,
        realtimeConfidenceBonus: Int,
        score: Int,
    ): String {
        val boardingReason = when {
            boarding == null -> "no first bus boarding candidate"
            boardingSlackMinutes == null -> "${boarding.routeName.orEmpty().ifBlank { "bus" }} realtime unavailable"
            else -> "${boarding.routeName.orEmpty().ifBlank { "bus" }} slack=${boardingSlackMinutes} min at ${boarding.stationName.orEmpty().ifBlank { "first stop" }}"
        }

        return "boarding=${boardingStatus.name}; $boardingReason; " +
            "boardingPenalty=${boardingStatus.penaltyMinutes}; " +
            "targetArrivalPenalty=$targetArrivalRiskPenalty; " +
            "earlyDeparture=${earlyDepartureRequiredMinutes ?: 0} min; " +
            "earlyDeparturePenalty=$earlyDeparturePenalty; " +
            "transferPenalty=$transferPenalty; walkingPenalty=$walkingPenalty; " +
            "realtimeBonus=$realtimeConfidenceBonus; score=$score."
    }

    private companion object {
        const val DEFAULT_MIN_ROUTE_SWITCH_GAIN_MINUTES = 3
        const val MIN_BOARDING_SLACK_MINUTES = 3
        const val MAX_EARLY_PULL_MINUTES = 10
        const val TARGET_ARRIVAL_MISS_PENALTY_MINUTES = 1_000
        const val EARLY_DEPARTURE_OVER_LIMIT_PENALTY_MINUTES = 6
        const val EARLY_DEPARTURE_PENALTY_DIVISOR = 5
        const val TRANSFER_PENALTY_MINUTES = 3
        const val WALKING_PENALTY_DIVISOR = 10
        const val REALTIME_CONFIDENCE_BONUS_MINUTES = 2
        const val TIGHT_BOARDING_MAX_SLACK_MINUTES = 2
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

internal data class RouteCandidateEvaluationInput(
    val pathIndex: Int,
    val adjustedTotalMinutes: Int,
    val transferCount: Int,
    val walkingMinutes: Int,
    val realtimeStatusRank: Int,
    val scheduledDepartureEpochMillis: Long? = null,
    val targetArrivalEpochMillis: Long? = null,
    val nowEpochMillis: Long = 0L,
    val firstBusBoarding: RouteCandidateBoardingInput? = null,
)

internal data class RouteCandidateBoardingInput(
    val routeName: String?,
    val stationName: String?,
    val accessMinutes: Int,
    val realtimeWaitMinutes: Int?,
    val scheduledDepartureEpochMillis: Long?,
    val nowEpochMillis: Long,
)

internal data class RouteCandidateSelection(
    val selected: RouteCandidateEvaluation,
    val evaluations: List<RouteCandidateEvaluation>,
    val reason: String,
)

internal data class RouteCandidateEvaluation(
    val input: RouteCandidateEvaluationInput,
    val score: Int,
    val boardingSlackMinutes: Int?,
    val boardingStatus: BoardingStatus,
    val safeDepartureEpochMillis: Long?,
    val earlyDepartureRequiredMinutes: Int?,
    val mayMissTargetArrival: Boolean,
    val reason: String,
)

internal enum class BoardingStatus(
    val penaltyMinutes: Int,
) {
    BOARDABLE(penaltyMinutes = 0),
    TIGHT(penaltyMinutes = 4),
    MISS_RISK(penaltyMinutes = 20),
    REALTIME_UNAVAILABLE(penaltyMinutes = 0),
    NO_FIRST_BUS(penaltyMinutes = 0),
}
