# Implementation Update - 2026-06-16

This document summarizes the current branch changes and the exact implementation boundary after the recent app-behavior review.

## Branch Scope

Current branch:

```text
feature/boarding-aware-route-ranking
```

The branch focuses on first-bus boarding-aware ODsay candidate ranking, realtime departure-window verification, and a home countdown formatting fix.

Nearby-bus search outside ODsay candidate paths and dedicated alternative-route UI are intentionally not part of this branch and should be implemented separately.

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
- Realtime re-estimation from home is only allowed when the recommended departure is within `0..30` minutes, so stale/past departure events do not trigger realtime correction.
- Countdown text now formats long durations as `k시간 l분` instead of showing large raw minute values such as `1328분`.

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
- The app evaluates up to 5 ODsay path candidates.
- Within the realtime lookahead window, first-leg realtime arrival can affect candidate duration.
- Current correction and boarding feasibility ranking are focused on first bus arrival.
- `RouteCandidateEvaluator` scores candidates with adjusted total minutes, first-bus boarding slack, miss-risk penalty, tight-boarding penalty, transfer count, walking time, and realtime confidence.
- Boarding slack compares `recommendedDepartureTime + accessMinutes` against the realtime bus wait. If the recommended departure is already past, the evaluator uses `now + accessMinutes`.
- A candidate with negative slack is treated as likely missed, slack `0..2` is treated as tight, and slack `>= 3` is treated as boardable.
- A fresh `RouteRealtimeSnapshot` can be reused inside the realtime window if realtime lookup or matching fails.
- Candidate switching is guarded by a minimum-gain threshold so tiny improvements do not cause route churn.
- Realtime lookup and snapshot fallback are skipped when `scheduledDepartureEpochMillis` is missing, more than 30 minutes away, or already in the past.
- The selected candidate's `subPath` is the source of `RouteEstimate.segments`, so the displayed/recorded route segments stay aligned with the selected route.

Important files:

- `app/src/main/java/com/mapmate/data/remote/provider/OdsayRouteEstimateProvider.kt`
- `app/src/main/java/com/mapmate/data/remote/provider/RouteCandidateEvaluator.kt`
- `app/src/test/kotlin/com/mapmate/data/remote/provider/OdsayRouteEstimateProviderTest.kt`
- `app/src/test/kotlin/com/mapmate/data/remote/provider/RouteCandidateEvaluatorTest.kt`

Not implemented in this branch:

- Nearby bus route search outside ODsay candidates.
- Comparing all catchable bus alternatives around the user, independent of ODsay candidate paths.
- Applying realtime arrival estimates to every transfer leg.
- UI explaining alternatives such as "Bus A is too tight, Bus B is recommended."

Recommended next branch:

```text
feature/realtime-route-candidate-ranking-ui
```

Suggested future components:

- home/alarm explanation UI for recommended bus and alternatives
- first-subway realtime boarding evaluation
- transfer-leg realtime evaluation
- optional nearby-bus search outside ODsay candidates

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
