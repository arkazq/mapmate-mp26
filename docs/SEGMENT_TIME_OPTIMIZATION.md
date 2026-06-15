# 구간별 소요시간 개인 맞춤 최적화

이 문서는 MapMate의 구간별 이동 시간 측정 및 개인화 보정 구조를 정리합니다.

## 목표

기존 이동 시간 보정은 전체 경로 시간을 하나의 값으로 다루는 방식이었습니다. 현재 구조는 ODsay 경로의 `subPath`를 도보, 버스, 지하철, 환승 이동, 목적지 도보 같은 세부 구간으로 나누고, 사용자가 실제로 측정한 구간별 소요시간을 다음 추천 시간에 반영합니다.

핵심 목표는 다음과 같습니다.

- 전체 이동시간이 아니라 구간별 오차를 학습합니다.
- 사용자의 반복 루틴별로 개인화된 이동 시간 보정을 적용합니다.
- 출발 준비 지연 성격의 `personalBufferMinutes`와 실제 이동 구간 오차 보정을 분리합니다.
- 구간 측정 UI와 수동 수정 UI를 통해 실제 데이터를 쌓을 수 있게 합니다.

## 데이터 모델

### RouteSegment

`RouteSegment`는 하나의 이동 구간을 나타냅니다.

주요 필드:

- `commuteRecordId`
- `routineId`
- `segmentIndex`
- `segmentType`
- `trafficType`
- `routeName`
- `startName`
- `endName`
- `plannedDurationMinutes`
- `actualStartedAtEpochMillis`
- `actualEndedAtEpochMillis`
- `actualDurationMinutes`
- `isUserEdited`
- `status`

지원하는 `RouteSegmentType`:

- `WALK_TO_TRANSIT`
- `BUS_RIDE`
- `SUBWAY_RIDE`
- `TRANSFER_WALK`
- `WALK_TO_DESTINATION`
- `UNKNOWN`

지원하는 `RouteSegmentStatus`:

- `NOT_STARTED`
- `IN_PROGRESS`
- `COMPLETED`
- `SKIPPED`

### SegmentTimeAdjustment

`SegmentTimeAdjustment`는 반복 기록에서 계산한 구간별 평균 지연값입니다.

보정 키는 `segmentIndex`를 사용하지 않습니다. 같은 루틴에서도 ODsay가 다른 경로 순서를 줄 수 있기 때문입니다.

현재 보정 키:

```text
routineId + segmentType + routeName + startName + endName
```

주요 필드:

- `routineId`
- `segmentType`
- `routeName`
- `startName`
- `endName`
- `averageDelayMinutes`
- `sampleCount`
- `confidence`
- `updatedAtEpochMillis`

## ODsay subPath 파싱

`OdsayRouteSegmentMapper`가 ODsay `subPath`를 순회해서 `RouteSegment` 목록을 생성합니다.

매핑 기준:

- `trafficType == 3`: 도보
- `trafficType == 2`: 버스
- `trafficType == 1`: 지하철

도보 구간은 위치에 따라 다르게 분류합니다.

- 첫 도보: `WALK_TO_TRANSIT`
- 중간 도보: `TRANSFER_WALK`
- 마지막 도보: `WALK_TO_DESTINATION`

ODsay 파싱에 실패하거나 segment를 만들 수 없으면 `RouteEstimate.segments`는 `emptyList()`로 유지하고 기존 단일 이동 시간 방식으로 동작합니다.

## 이동 측정 UI

현재 이동 기록 측정 화면은 구간 전체 리스트를 기본 노출하지 않고, 현재 측정해야 하는 구간 하나만 표시합니다.

현재 구간 결정 순서:

1. `IN_PROGRESS` 구간이 있으면 해당 구간
2. 없으면 첫 번째 `NOT_STARTED` 구간
3. 모든 구간이 `COMPLETED` 또는 `SKIPPED`이면 완료 단계

구간별 버튼 문구:

- 도보, 환승 이동, unknown: `시작` / `완료`
- 버스, 지하철: `탑승` / `하차`

진행 중인 구간은 `actualStartedAtEpochMillis` 기준으로 elapsed time을 표시합니다.

모든 구간이 완료되면 `모든 구간 측정 완료` 화면을 보여주고, 사용자가 `도착 완료`를 누를 때 전체 `CommuteRecord`와 구간 기록을 저장합니다.

## 수동 수정 UI

이동 완료 화면과 기록 화면에서 저장된 segment가 있는 기록에 한해 `구간별 시간 수정` 진입점을 표시합니다.

수정 화면에서 가능한 작업:

- 구간별 시작 시각 수정
- 구간별 종료 시각 수정
- 실제 소요시간 재계산
- `SKIPPED` 구간을 시간 입력 후 `COMPLETED`로 전환

저장 규칙:

- 종료 시각이 시작 시각보다 빠르면 저장하지 않습니다.
- 수정된 구간은 `isUserEdited = true`로 저장합니다.
- 수정된 구간은 `status = COMPLETED`로 저장합니다.
- 입력하지 않은 `SKIPPED` 구간은 `SKIPPED` 상태를 유지합니다.

## 개인화 보정 계산

`SegmentTimeAdjustmentCalculator`가 최근 완료 segment 기록을 기준으로 보정값을 계산합니다.

계산식:

```text
delay = actualDurationMinutes - plannedDurationMinutes
```

현재 정책:

- 같은 보정 키끼리 묶어서 평균 delay를 계산합니다.
- 최근 샘플 최대 5개를 사용합니다.
- 최근 기록일수록 더 높은 가중치를 둡니다.
- 수동 수정 기록은 반영하되 낮은 가중치와 낮은 confidence를 적용합니다.
- 지연값이 너무 큰 이상치는 제외합니다. 현재 기준은 +/- 60분입니다.
- sample count가 3개 미만이면 confidence가 낮습니다.

## 추천 시간 반영

`SegmentAdjustedRouteEstimateProvider`가 기존 `RouteEstimateProvider`를 감싸고 segment 보정값을 적용합니다.

반영 흐름:

1. ODsay/Google/Mock provider가 `RouteEstimate`를 반환합니다.
2. `RouteEstimate.segments`가 있고 `routineId`가 있으면 저장된 `SegmentTimeAdjustment`를 조회합니다.
3. 현재 estimate의 segment와 adjustment key가 일치하면 보정값을 적용합니다.
4. confidence가 낮은 보정값은 적용하지 않습니다.
5. 적용된 delay를 `estimatedMinutes`에 더합니다.

`personalBufferMinutes`는 계속 유지됩니다. 이 값은 출발 준비 지연 또는 사용자 습관성 출발 지연에 가깝고, segment 보정은 실제 이동 구간의 소요시간 오차를 보정합니다.

## 대체 처리 정책

- `RouteEstimate.segments`가 없으면 기존 단일 이동 기록 UI와 단일 이동 시간 계산을 사용합니다.
- segment 기록이 없으면 segment 보정은 적용하지 않습니다.
- confidence가 낮으면 보정값을 적용하지 않거나 약하게 적용합니다.
- 기록 저장 시 미완료 segment는 기존 정책으로 정리합니다.
  - `NOT_STARTED` -> `SKIPPED`
  - `IN_PROGRESS` -> 현재 시각으로 `COMPLETED`

## 현재 한계

- 시간대별/요일별 segment 보정은 아직 없습니다.
- 환승 대기시간은 planned segment로 만들지 않습니다.
- 구간 key는 문자열 정규화만 수행하므로 정류장/역 이름 표기 차이에 민감할 수 있습니다.
- 자동 위치 기반 탑승/하차 감지는 없습니다.
- 지하철 지역별 실시간 provider 확장은 별도 작업입니다.

## 검증

현재 관련 검증:

- `OdsayRouteSegmentMapperTest`
- `SegmentTimeAdjustmentCalculatorTest`
- `SegmentAdjustedRouteEstimateProviderTest`
- `PersonalBufferOptimizerTest`
- `.\gradlew.bat testDebugUnitTest`
- `.\gradlew.bat build`
# 현재 구간 최적화 참고

최신 구간 측정과 최적화 변경 내용은 `docs/IMPLEMENTATION_UPDATE_2026_06_16.md`를 확인합니다. 현재 버스/지하철 대기 구간은 별도로 측정하며, 구간 보정값은 평균/최소/최대 실제 소요시간을 저장합니다. 최적화 키는 루틴과 노선이 달라지면 별도로 학습되도록 유지합니다.
