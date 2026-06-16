# 구현 업데이트 - 2026-06-16

이 문서는 최근 앱 동작 점검 이후 반영된 구현 범위와 현재 브랜치의 변경 내용을 정리합니다.

## 브랜치 범위

현재 브랜치:

```text
feature/seoul-bus-station-arrival
```

현재 브랜치는 서울버스 실시간 도착정보를 정류장 ARS(`getStationByUid`) 기준으로 조회하고 노선번호로 매칭하는 개선을 다룹니다. 이전 브랜치에서 구현한 출발 전 상태 알림, 홈 화면의 다음 루틴 선택 기준 정리, 홈/알림 권장 출발 시각 동기화, TAGO 노선명 정규화, 첫 버스 탑승 가능성 기반 후보 랭킹과 홈/상세 예측 탑승 판단 UI를 유지하면서, ODsay `busID/routeID`가 서울버스 `busRouteId`와 맞지 않는 경로의 실시간 보정 누락을 보완했습니다.

주변 정류장의 버스를 ODsay 결과 밖에서 직접 탐색하거나, 모든 환승 구간의 실시간 도착정보를 평가하는 기능은 아직 이 브랜치 범위가 아닙니다.

## 출발 전 상태 알림

구현된 내용:

- 설정 화면에 `출발 전 상태 알림` 토글을 추가했습니다.
- 출발 알림이 켜져 있고 상태 알림 토글이 켜져 있으면, 권장 출발 시각 30분 이내에 일반 notification으로 권장 출발 시각을 표시합니다.
- T-30/T-15/T-5 재조회 결과로 권장 출발 시각이 바뀌면 같은 루틴의 상태 알림을 갱신합니다.
- 실제 출발 알림이 울릴 때는 해당 루틴의 상태 알림을 정리합니다.
- 토글을 끄거나 알림 권한이 없으면 상태 알림을 정리하고 기존 출발 알림/재조회 흐름은 유지합니다.

관련 파일:

- `app/src/main/java/com/mapmate/domain/alarm/PredepartureStatusNotificationPublisher.kt`
- `app/src/main/java/com/mapmate/data/alarm/AndroidPredepartureStatusNotificationPublisher.kt`
- `app/src/main/java/com/mapmate/data/alarm/DepartureAlarmCoordinator.kt`
- `app/src/main/java/com/mapmate/data/alarm/DepartureAlarmReceiver.kt`
- `app/src/main/java/com/mapmate/domain/model/AppSettings.kt`
- `app/src/main/java/com/mapmate/data/preferences/DataStoreSettingsRepository.kt`
- `app/src/main/java/com/mapmate/presentation/settings/SettingsScreen.kt`

## 출발 전 재조회와 알림

구현된 내용:

- 출발 전 재조회 작업은 권장 출발 시각보다 먼저 예약됩니다.
- 경로 길이에 따라 재조회 시점이 달라집니다.
  - 일반 경로: 출발 `60, 30, 15, 5`분 전
  - 60분 이상 경로: 출발 `90, 60, 30, 15, 5`분 전
  - 90분 이상 경로: 출발 `120, 90, 60, 30, 15, 5`분 전
- `DepartureRecheckWorker`는 실행 중인 자기 자신을 취소하지 않고 다음 알림을 다시 예약할 수 있습니다.
- `DepartureRecheckScheduler.schedule()`은 `replaceExisting` 값을 받아 기존 재조회 작업 정리 여부를 구분합니다.
- 알림 수신 후 다음 반복 알림을 계산할 때 방금 울린 출발 이벤트는 제외합니다.
- 출발까지 5분 이내로 재계산되면 사용자에게 출발 준비 알림이 즉시 표시될 수 있습니다.
- 출발 전 상태 알림은 출발 알림과 별도 ID 범위를 사용하며, 실제 출발 알림 전환 시 중복으로 남지 않도록 정리합니다.

관련 파일:

- `app/src/main/java/com/mapmate/data/alarm/AndroidDepartureRecheckScheduler.kt`
- `app/src/main/java/com/mapmate/domain/alarm/DepartureRecheckScheduler.kt`
- `app/src/main/java/com/mapmate/data/alarm/DepartureAlarmCoordinator.kt`
- `app/src/main/java/com/mapmate/domain/alarm/DepartureAlarmPlanner.kt`
- `app/src/main/java/com/mapmate/domain/alarm/DepartureAdjustmentPolicy.kt`
- `app/src/main/java/com/mapmate/data/alarm/DepartureAlarmReceiver.kt`
- `app/src/main/java/com/mapmate/data/alarm/AndroidDepartureAlarmScheduler.kt`

## 홈 화면 동기화

구현된 내용:

- 홈 추천 정보는 현재 시각을 기준으로 다시 계산됩니다.
- 홈 대시보드의 메인 추천은 저장 순서가 아니라 현 시간 기준 가장 가까운 다음 출발 루틴을 선택합니다.
- 홈 화면은 메인 추천 아래에 저장된 모든 루틴 목록을 함께 표시합니다.
- 홈 메인 추천의 `이동 기록 시작` 대상도 가장 가까운 다음 출발 루틴을 따릅니다.
- 홈 대시보드는 1분 단위 ticker를 사용해 남은 출발 시간과 진행률을 갱신합니다.
- `recommendedDepartureTime`, `recommendedDepartureAtEpochMillis`, `minutesUntilDeparture`, `departureCountdownText`, `departureProgress`는 고정 문자열이 아니라 추천 상태에서 계산됩니다.
- 홈/상세 예측 화면의 표시 권장 출발 시각은 출발 알림의 `DepartureAdjustmentPolicy`와 같은 기준을 적용해 알림 시각과 화면 시각이 어긋나지 않도록 맞춥니다.
- 홈 화면의 실시간 재조회는 권장 출발 시각이 현재 기준 `0..30`분 안에 있을 때만 수행합니다.
- 남은 출발 시간이 60분 이상이면 `1328분` 같은 원시 분 값 대신 `22시간 8분` 형식으로 표시합니다.

관련 파일:

- `app/src/main/java/com/mapmate/presentation/home/HomeViewModel.kt`
- `app/src/main/java/com/mapmate/presentation/home/HomeScreen.kt`
- `app/src/main/java/com/mapmate/presentation/home/HomeUiState.kt`
- `app/src/main/java/com/mapmate/presentation/common/RoutineRecommendationUiModel.kt`

## TAGO 노선명 정규화

구현된 내용:

- TAGO 도착정보 매칭 시 ODsay 노선명에 붙는 괄호/대괄호 업체명 표기를 제거합니다.
- 예를 들어 ODsay `11(남양여객)`은 TAGO `11`과 같은 노선으로 비교됩니다.
- 기존 공백 제거와 `번` 제거 정책은 유지합니다.
- 정류장 좌표, 노선명, 도착정보가 맞지 않으면 기존처럼 null을 반환해 서울 provider/fresh snapshot/ODsay 기본값 fallback을 유지합니다.

관련 파일:

- `app/src/main/java/com/mapmate/data/remote/provider/TagoBusArrivalProvider.kt`
- `app/src/test/kotlin/com/mapmate/data/remote/provider/TagoBusArrivalProviderTest.kt`

## 서울버스 정류장 ARS 기준 매칭

배경:

- `SEOUL_BUS_SERVICE_KEY`와 서울특별시 버스도착정보조회 endpoint는 정상 응답을 확인했습니다.
- 다만 일부 ODsay 경로에서 `busID/routeID`를 서울버스 `busRouteId`로 사용하면 `결과가 없습니다`가 반환될 수 있습니다.

구현된 내용:

- `startArsID`가 있으면 서울버스 정류장 도착목록 endpoint(`getStationByUid`)를 먼저 조회합니다.
- 도착목록에서 `rtNm` 또는 `busRouteAbrv`를 ODsay `busNo`와 매칭하며, 괄호/대괄호 업체명·공백·`번` 제거 정규화를 적용합니다.
- 같은 노선이 여러 건이면 가장 빠른 도착 항목을 선택합니다.
- 정류장 기준 조회/매칭이 실패하면 기존 `busRouteId` 기반 조회로 fallback합니다.
- 도착시간 필드는 endpoint별 차이를 흡수하기 위해 `exps1`/`exps2`와 `traTime1`/`traTime2`를 함께 처리합니다.

관련 파일:

- `app/src/main/java/com/mapmate/data/remote/api/SeoulBusArrivalApi.kt`
- `app/src/main/java/com/mapmate/data/remote/provider/SeoulBusRealtimeArrivalProvider.kt`
- `app/src/main/java/com/mapmate/data/remote/provider/SeoulBusArrivalXmlParser.kt`
- `app/src/test/kotlin/com/mapmate/data/remote/provider/SeoulBusRealtimeArrivalProviderTest.kt`
- `app/src/test/kotlin/com/mapmate/data/remote/provider/SeoulBusArrivalXmlParserTest.kt`

## 첫 버스 탑승 가능성 기반 후보 랭킹

구현된 내용:

- ODsay 대중교통 후보 `path`를 최대 5개까지 평가합니다.
- 출발 예정 시각 30분 이내일 때 첫 버스 구간의 실시간 도착정보를 조회합니다.
- `RouteCandidateEvaluator`는 다음 값을 기준으로 후보 점수를 계산합니다.
  - 보정된 총 이동 시간
  - 첫 버스 정류장까지의 접근 시간
  - 실시간 버스 도착 대기 시간
  - 탑승 여유 시간
  - 놓칠 위험 또는 빠듯한 탑승 페널티
  - 환승 수
  - 도보 시간
  - 실시간 정보 신뢰도
- 탑승 여유가 0분 미만이면 놓칠 가능성이 높은 후보로 봅니다.
- 탑승 여유가 0~2분이면 빠듯한 후보로 봅니다.
- 탑승 여유가 3분 이상이면 탑승 가능한 후보로 봅니다.
- 후보를 바꿔도 이득이 3분 미만이면 기존 ODsay 1순위 경로를 유지합니다.
- 선택된 후보의 `subPath`에서 만든 `RouteSegment`만 `RouteEstimate.segments`에 넣습니다.
- 실시간 조회는 `scheduledDepartureEpochMillis`가 없거나, 출발까지 30분을 초과하거나, 이미 지난 출발 이벤트이면 수행하지 않습니다.

관련 파일:

- `app/src/main/java/com/mapmate/data/remote/provider/OdsayRouteEstimateProvider.kt`
- `app/src/main/java/com/mapmate/data/remote/provider/RouteCandidateEvaluator.kt`
- `app/src/test/kotlin/com/mapmate/data/remote/provider/OdsayRouteEstimateProviderTest.kt`
- `app/src/test/kotlin/com/mapmate/data/remote/provider/RouteCandidateEvaluatorTest.kt`

## 후보 랭킹 UI

구현된 내용:

- `RouteEstimate`에 `RouteBoardingAdvice`를 추가해 첫 버스 탑승 판단 결과를 구조화했습니다.
- 홈 화면은 기존 추천 근거 카드 자리에 탑승 판단 요약을 표시합니다.
  - 추천 버스
  - 정류장 이름
  - 정류장까지 걸리는 시간
  - 버스 도착까지 남은 시간
  - 탑승 여유 또는 부족 시간
- 상세 예측 화면은 `탑승 판단` 섹션을 추가해 추천 후보와 대안 후보 1~2개를 표시합니다.
- 상세 예측 화면도 홈과 같은 30분 실시간 정책을 사용해 `scheduledDepartureEpochMillis`를 전달합니다.
- 루틴 등록의 계산 결과 카드에서는 API 응답의 내부 reason 문자열을 그대로 보여주지 않고 사용자용 요약 문장으로 가공합니다.

관련 파일:

- `app/src/main/java/com/mapmate/domain/model/RouteEstimate.kt`
- `app/src/main/java/com/mapmate/presentation/common/RouteBoardingAdviceCards.kt`
- `app/src/main/java/com/mapmate/presentation/common/RoutineRecommendationUiModel.kt`
- `app/src/main/java/com/mapmate/presentation/home/HomeScreen.kt`
- `app/src/main/java/com/mapmate/presentation/prediction/PredictionDetailScreen.kt`
- `app/src/main/java/com/mapmate/presentation/prediction/PredictionDetailViewModel.kt`
- `app/src/main/java/com/mapmate/presentation/routine/RoutineRegistrationScreen.kt`
- `app/src/main/java/com/mapmate/presentation/routine/RoutineRegistrationUiState.kt`

## 기록 분석 범위

구현된 내용:

- 기록 분석은 전체 루틴 또는 선택한 루틴 기준으로 볼 수 있습니다.
- `RecordsUiState`는 필터링된 기록, 필터링된 통계, 루틴 필터, 선택된 범위 이름을 제공합니다.
- `RecordsViewModel`은 기록 변경 시 선택된 루틴 필터가 유효한지 유지합니다.
- `RecordsScreen`은 루틴 필터 chip과 필터링된 통계를 표시합니다.

관련 파일:

- `app/src/main/java/com/mapmate/presentation/history/RecordsUiState.kt`
- `app/src/main/java/com/mapmate/presentation/history/RecordsViewModel.kt`
- `app/src/main/java/com/mapmate/presentation/history/RecordsScreen.kt`

## 구간 측정과 구간별 최적화

구현된 내용:

- `RouteSegmentType`은 `WAIT_FOR_BUS`, `WAIT_FOR_SUBWAY`를 포함합니다.
- ODsay 버스/지하철 subPath는 계획된 대기 구간과 탑승 구간으로 나뉩니다.
- 현재 계획 대기 기준은 5분입니다.
- 이동 기록 화면은 대기 시간을 별도 구간으로 측정할 수 있습니다.
- 버스/지하철 대기 구간을 완료하면 같은 노선과 시작 정류장의 다음 탑승 구간이 자동으로 시작됩니다.
- 구간 수정 화면은 대기 구간도 표시합니다.

관련 파일:

- `app/src/main/java/com/mapmate/domain/model/RouteSegment.kt`
- `app/src/main/java/com/mapmate/data/remote/provider/OdsayRouteSegmentMapper.kt`
- `app/src/main/java/com/mapmate/presentation/tracking/TrackingViewModel.kt`
- `app/src/main/java/com/mapmate/presentation/tracking/TrackingScreen.kt`
- `app/src/main/java/com/mapmate/presentation/segmentedit/RouteSegmentEditScreen.kt`

## 구간 보정값 저장

구현된 내용:

- 구간 최적화 키는 다음 조합을 유지합니다.

```text
routineId + segmentType + routeName + startName + endName
```

- 같은 루틴이라도 버스/지하철 노선명이 다르면 별도 구간으로 학습합니다.
- `SegmentTimeAdjustment`는 평균 지연, 평균 실제 소요시간, 최소 실제 소요시간, 최대 실제 소요시간, 표본 수, 신뢰도, 갱신 시각을 저장합니다.
- Room DB 버전은 7입니다.
- `MIGRATION_6_7`은 실제 소요시간 요약 컬럼을 추가합니다.

관련 파일:

- `app/src/main/java/com/mapmate/domain/model/SegmentTimeAdjustment.kt`
- `app/src/main/java/com/mapmate/domain/calculator/SegmentTimeAdjustmentCalculator.kt`
- `app/src/main/java/com/mapmate/data/local/SegmentTimeAdjustmentEntity.kt`
- `app/src/main/java/com/mapmate/data/local/SegmentTimeAdjustmentMapper.kt`
- `app/src/main/java/com/mapmate/data/local/MapMateDatabase.kt`

## 아직 구현하지 않은 범위

- ODsay 후보 밖의 주변 버스 직접 탐색
- 사용자가 탈 수 있는 모든 주변 버스 대안 비교
- 지하철 첫 탑승 실시간 후보 랭킹
- 모든 환승 구간 실시간 도착정보 반영
- 알림 화면에서 추천 버스와 대안 후보를 상세하게 설명하는 UI

## 검증

현재 브랜치는 아래 명령으로 검증했습니다.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
git diff --check
```

참고:

- `git diff --check`에서 공백 오류는 없었습니다.
- 일부 파일은 Git이 CRLF 변환 경고를 출력할 수 있습니다.
