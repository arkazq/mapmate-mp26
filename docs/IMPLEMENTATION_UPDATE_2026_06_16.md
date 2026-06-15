# Implementation Update - 2026-06-16

This document summarizes the current branch changes and the exact implementation boundary after the recent app-behavior review.

## Branch Scope

Current branch:

```text
fix/verify-realtime-route-behavior
```

The branch focuses on fixing departure recheck behavior, synchronizing home UI state, preventing repeated departure alarms, improving records analysis scope, and extending segment-based travel-time learning.

The full nearby-bus selection engine is intentionally not part of this branch and should be implemented separately.

## Departure Recheck And Alarm Fixes

Implemented:

- Departure recheck jobs are scheduled before the recommended departure time.
- Recheck offsets are selected from route duration:
  - shorter routes: `60, 30, 15, 5` minutes before departure
  - longer routes: earlier offsets are added as needed
- `DepartureRecheckWorker` can reschedule the next alarm without cancelling itself.
- `DepartureRecheckScheduler.schedule()` accepts `replaceExisting`.
- `DepartureAlarmCoordinator` passes `replaceExisting = previousSchedule == null` when rescheduling from a recheck.
- After a departure notification fires, the fired schedule is excluded when planning the next repeated alarm.
- The alarm receiver carries `targetArrivalAtEpochMillis` so the fired schedule can be reconstructed more accurately.

Important files:

- `app/src/main/java/com/mapmate/data/alarm/AndroidDepartureRecheckScheduler.kt`
- `app/src/main/java/com/mapmate/domain/alarm/DepartureRecheckScheduler.kt`
- `app/src/main/java/com/mapmate/data/alarm/DepartureAlarmCoordinator.kt`
- `app/src/main/java/com/mapmate/domain/alarm/DepartureAlarmPlanner.kt`
- `app/src/main/java/com/mapmate/data/alarm/DepartureAlarmReceiver.kt`
- `app/src/main/java/com/mapmate/data/alarm/AndroidDepartureAlarmScheduler.kt`

## Home UI Synchronization

Implemented:

- Home recommendations are recalculated with current time.
- The dashboard uses a minute ticker so countdown text can update.
- `recommendedDepartureTime`, `recommendedDepartureAtEpochMillis`, `minutesUntilDeparture`, `departureCountdownText`, and `departureProgress` are derived values.
- Home UI no longer displays a fixed stale countdown value.
- When the current recommendation is close enough to departure, route re-estimation can use `scheduledDepartureEpochMillis` so realtime adjustment can be considered.

Important files:

- `app/src/main/java/com/mapmate/presentation/home/HomeViewModel.kt`
- `app/src/main/java/com/mapmate/presentation/home/HomeScreen.kt`
- `app/src/main/java/com/mapmate/presentation/home/HomeUiState.kt`
- `app/src/main/java/com/mapmate/presentation/common/RoutineRecommendationUiModel.kt`

## Records Analysis Scope

Implemented:

- Records analysis can be scoped to all routines or a selected routine.
- `RecordsUiState` exposes filtered records, filtered stats, routine filters, and the selected scope label.
- `RecordsViewModel` keeps the selected routine filter valid as records change.
- `RecordsScreen` shows routine filter chips and calculates stats from the filtered record set.

Important files:

- `app/src/main/java/com/mapmate/presentation/history/RecordsUiState.kt`
- `app/src/main/java/com/mapmate/presentation/history/RecordsViewModel.kt`
- `app/src/main/java/com/mapmate/presentation/history/RecordsScreen.kt`

## Segment Tracking And Optimization

Implemented:

- `RouteSegmentType` now includes:
  - `WAIT_FOR_BUS`
  - `WAIT_FOR_SUBWAY`
- ODsay bus/subway subpaths are expanded into planned wait segments followed by ride segments.
- The current planned wait baseline is 5 minutes.
- Tracking UI can measure wait time as a separate segment.
- Completing a bus/subway wait segment automatically starts the next ride segment when route name and start station match.
- Segment edit UI can display wait segments.

Important files:

- `app/src/main/java/com/mapmate/domain/model/RouteSegment.kt`
- `app/src/main/java/com/mapmate/data/remote/provider/OdsayRouteSegmentMapper.kt`
- `app/src/main/java/com/mapmate/presentation/tracking/TrackingViewModel.kt`
- `app/src/main/java/com/mapmate/presentation/tracking/TrackingScreen.kt`
- `app/src/main/java/com/mapmate/presentation/segmentedit/RouteSegmentEditScreen.kt`

## Segment Adjustment Storage

Implemented:

- Segment optimization key remains:

```text
routineId + segmentType + routeName + startName + endName
```

- This keeps the same routine separated by bus/subway route name.
- `SegmentTimeAdjustment` now stores:
  - `averageDelayMinutes`
  - `averageActualDurationMinutes`
  - `minActualDurationMinutes`
  - `maxActualDurationMinutes`
  - `sampleCount`
  - `confidence`
  - `updatedAtEpochMillis`
- Room database version is now `7`.
- `MIGRATION_6_7` adds the new actual-duration summary columns.

Important files:

- `app/src/main/java/com/mapmate/domain/model/SegmentTimeAdjustment.kt`
- `app/src/main/java/com/mapmate/domain/calculator/SegmentTimeAdjustmentCalculator.kt`
- `app/src/main/java/com/mapmate/data/local/SegmentTimeAdjustmentEntity.kt`
- `app/src/main/java/com/mapmate/data/local/SegmentTimeAdjustmentMapper.kt`
- `app/src/main/java/com/mapmate/data/local/MapMateDatabase.kt`

## Current Route Recheck Behavior

Implemented:

- ODsay provides public-transit candidate paths.
- The app evaluates up to 3 ODsay path candidates.
- Within the realtime lookahead window, first-leg realtime arrival can affect candidate duration.
- Current correction is focused on first bus arrival.
- A fresh `RouteRealtimeSnapshot` can be reused inside the realtime window if realtime lookup or matching fails.
- Candidate switching is guarded by a minimum-gain threshold so tiny improvements do not cause route churn.

Not implemented in this branch:

- Explicit "which bus should I take" decision engine.
- Nearby bus route search outside ODsay candidates.
- Comparing all catchable bus alternatives by access time, arrival time, and miss risk.
- Applying realtime arrival estimates to every transfer leg.
- UI explaining alternatives such as "Bus A is too tight, Bus B is recommended."

Recommended next branch:

```text
feature/boarding-candidate-evaluator
```

Suggested future components:

- `RouteCandidateEvaluator`
- `TransitBoardingCandidate`
- `BoardingFeasibility`
- score model using total time, access time, realtime arrival, miss risk, transfer count, walking time, and confidence
- home/alarm explanation UI for recommended bus and alternatives

## Verification

The branch was verified with:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
git diff --check
```

Known non-failure output:

- Git reports CRLF warnings for some working-copy files.
- No whitespace errors were reported by `git diff --check`.
