package com.mapmate.presentation.history

import com.mapmate.domain.model.CommuteRecord
import com.mapmate.domain.model.TransportMode
import kotlin.math.roundToInt

data class RecordsUiState(
    val records: List<CommuteRecord> = emptyList(),
    val selectedRoutineId: Long? = null,
    val isLoading: Boolean = true,
    val stats: RecordsStats = RecordsStats(),
) {
    val filteredRecords: List<CommuteRecord>
        get() = selectedRoutineId?.let { routineId ->
            records.filter { it.routineId == routineId }
        } ?: records

    val filteredStats: RecordsStats
        get() = RecordsStats.from(filteredRecords)

    val selectedScopeLabel: String
        get() = selectedRoutineId?.let { routineId ->
            records.firstOrNull { it.routineId == routineId }?.routineName
        } ?: "전체 루틴"

    val routineFilters: List<RecordsRoutineFilter>
        get() {
            val routineFilters = records
                .filter { it.routineId != null }
                .groupBy { it.routineId }
                .mapNotNull { (routineId, routineRecords) ->
                    val id = routineId ?: return@mapNotNull null
                    RecordsRoutineFilter(
                        routineId = id,
                        label = routineRecords.first().routineName,
                        recordCount = routineRecords.size,
                    )
                }
                .sortedBy { it.label }

            return listOf(
                RecordsRoutineFilter(
                    routineId = null,
                    label = "전체",
                    recordCount = records.size,
                ),
            ) + routineFilters
        }
}

data class RecordsRoutineFilter(
    val routineId: Long?,
    val label: String,
    val recordCount: Int,
)

data class RecordsStats(
    val totalRecords: Int = 0,
    val averageArrivalDeltaMinutes: Int = 0,
    val onTimeRatePercent: Int = 0,
    val lateRecords: Int = 0,
    val mostUsedTransportMode: TransportMode? = null,
    val recentAverageDeltaMinutes: Int = 0,
) {
    companion object {
        fun from(records: List<CommuteRecord>): RecordsStats {
            if (records.isEmpty()) return RecordsStats()

            val totalRecords = records.size
            val averageArrivalDeltaMinutes = records
                .map { it.arrivalDeltaMinutes }
                .average()
                .roundToInt()
            val onTimeRatePercent = records
                .count { it.arrivalDeltaMinutes <= 0 }
                .times(100.0)
                .div(totalRecords)
                .roundToInt()
            val lateRecords = records.count { it.arrivalDeltaMinutes > 0 }
            val mostUsedTransportMode = records
                .groupingBy { it.transportMode }
                .eachCount()
                .maxByOrNull { (_, count) -> count }
                ?.key
            val recentAverageDeltaMinutes = records
                .take(5)
                .map { it.arrivalDeltaMinutes }
                .average()
                .roundToInt()

            return RecordsStats(
                totalRecords = totalRecords,
                averageArrivalDeltaMinutes = averageArrivalDeltaMinutes,
                onTimeRatePercent = onTimeRatePercent,
                lateRecords = lateRecords,
                mostUsedTransportMode = mostUsedTransportMode,
                recentAverageDeltaMinutes = recentAverageDeltaMinutes,
            )
        }
    }
}
