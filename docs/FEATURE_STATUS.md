# MapMate Feature Status

## Segment time optimization status

| Feature | Status | Description | Main files |
| -- | -- | -- | -- |
| ODsay subPath segment parsing | Done | Converts ODsay public transit `subPath` entries into ordered `RouteSegment` values. Walk sections are classified as walk-to-transit, transfer walk, or walk-to-destination. | `data/remote/provider/OdsayRouteSegmentMapper.kt`, `domain/model/RouteSegment.kt` |
| Segment persistence | Done | Stores segment records and segment adjustment values in Room. Database version is now 6 with `route_segments` and `segment_time_adjustments`. | `data/local/RouteSegmentEntity.kt`, `data/local/SegmentTimeAdjustmentEntity.kt`, `data/local/MapMateDatabase.kt` |
| Segment-based tracking UI | Done | Tracking now focuses on one active segment at a time, shows current segment progress, and saves measured segment durations with the commute record. | `presentation/tracking/TrackingScreen.kt`, `presentation/tracking/TrackingViewModel.kt`, `presentation/tracking/TrackingUiState.kt` |
| Manual segment time editing | Done | Saved segment start/end times can be edited after commute completion or from the history screen. Edited values are marked with `isUserEdited = true`. | `presentation/segmentedit/RouteSegmentEditScreen.kt`, `presentation/segmentedit/RouteSegmentEditViewModel.kt` |
| Segment time personalization | Done | Recent completed segment records are grouped by routine, segment type, route name, start, and end. Average delay and confidence are calculated and applied to later route estimates. | `domain/calculator/SegmentTimeAdjustmentCalculator.kt`, `data/remote/provider/SegmentAdjustedRouteEstimateProvider.kt` |
| Time-of-day and weekday segment learning | Not started | Segment adjustments are not yet split by time band or weekday. | Future work |
| Automatic boarding/alighting detection | Not started | The app does not infer segment start/end from device location. | Future work |

현재 구현 상태를 기준으로 기능별 진행 상황을 정리합니다.

| 기능 | 상태 | 설명 | 관련 위치 |
| -- | -- | -- | ----- |
| Android 초기 프로젝트 | 완료 | Android Studio 생성 기반 프로젝트가 구성되어 있습니다. | `app`, `gradle`, `settings.gradle.kts` |
| 홈/루틴/기록/설정 하단 내비게이션 | 완료 | Material 3 하단 내비게이션으로 홈, 루틴, 기록, 설정 화면을 전환합니다. | `presentation/MapMateApp.kt`, `presentation/common/MapMateScaffold.kt` |
| 홈 대시보드 UI | 완료 | 오늘 권장 출발 시각, 출발까지 남은 시간, 추천 근거, 이동 시간/보정/안전 여유 요약, 오늘의 루틴을 표시합니다. | `presentation/home/HomeScreen.kt` |
| 루틴 목록 UI | 완료 | 저장된 루틴을 카드 목록으로 표시하고 수정/삭제/상세 예측 진입을 제공합니다. | `presentation/routine/RoutinesScreen.kt`, `presentation/routine/RoutinesViewModel.kt` |
| 루틴 등록 UI | 완료 | 루틴 이름, 장소, 도착 시각, 요일, 이동 수단, 보정 시간을 단계형 UI로 입력합니다. | `presentation/routine/RoutineRegistrationScreen.kt` |
| 목적지 후보 표시 | 완료 | Kakao Local API 키가 있으면 실제 장소 검색 결과를 표시하고, 실패하거나 키가 없으면 mock 후보를 표시합니다. | `data/remote/provider/KakaoPlaceSearchProvider.kt`, `data/mock/MockPlaceSearchProvider.kt` |
| 목적지 선택 | 완료 | 목적지 후보를 선택하면 `selectedDestination`에 반영됩니다. | `presentation/routine/RoutineRegistrationViewModel.kt` |
| 출발지 검색 선택 | 완료 | Kakao Local API 또는 mock 장소 후보에서 출발지를 검색하고 선택할 수 있습니다. | `presentation/routine/RoutineRegistrationScreen.kt`, `presentation/routine/RoutineRegistrationViewModel.kt` |
| 현재 위치 출발지 선택 | 완료 | Android 위치 권한을 요청하고 휴대폰 현재 위치 좌표를 출발지로 설정합니다. Kakao 키가 있으면 좌표를 주소로 변환해 출발지 주소로 표시하고, 실패하면 기존 fallback 주소를 유지합니다. | `data/location/AndroidCurrentLocationProvider.kt`, `data/remote/provider/KakaoReverseGeocodingProvider.kt`, `presentation/routine/RoutineRegistrationScreen.kt` |
| 반복 요일 선택 | 완료 | 월~일 반복 요일을 선택/해제할 수 있습니다. | `presentation/common/MapMateSelectors.kt` |
| 이동 수단 선택 | 완료 | `TRANSIT`, `WALK`, `CAR` 이동 수단을 선택할 수 있으며 설정 화면에서는 3개 카드가 동일 폭으로 배치됩니다. | `domain/model/TransportMode.kt`, `presentation/common/MapMateSelectors.kt` |
| 권장 출발 시각 계산 | 완료 | 실제 경로 API 또는 mock 예상 이동 시간과 보정 시간을 기준으로 권장 출발 시각을 계산합니다. | `presentation/routine/RoutineRegistrationViewModel.kt` |
| `DepartureTimeCalculator` | 완료 | 순수 Kotlin 계산기로 권장 출발 시각을 계산하고, 계산 결과가 이미 지난 시간이면서 목표 도착 시각이 아직 남아 있으면 `지금 출발` 상태로 보정합니다. | `domain/calculator/DepartureTimeCalculator.kt` |
| 계산 로직 테스트 | 완료 | 기본 권장 출발 시각, 즉시 출발 보정, 도착 목표가 이미 지난 경우를 검증합니다. | `app/src/test/kotlin/com/mapmate/domain/calculator/DepartureTimeCalculatorTest.kt`, `app/src/test/kotlin/com/mapmate/presentation/common/RoutineRecommendationUiModelTest.kt` |
| Mock 장소 검색 provider | 완료 | `PlaceSearchProvider`의 mock 구현체가 있습니다. | `data/mock/MockPlaceSearchProvider.kt` |
| Mock 이동 시간 provider | 완료 | 이동 수단별 고정 예상 시간을 반환합니다. | `data/mock/MockRouteEstimateProvider.kt` |
| API fallback provider | 완료 | 실제 API 실패, 키 누락, 좌표 누락 시 기존 mock 결과로 복구하고, 경로 fallback은 사용자용 상태 메시지로 사유를 요약합니다. | `data/remote/provider/FallbackPlaceSearchProvider.kt`, `data/remote/provider/FallbackRouteEstimateProvider.kt`, `presentation/common/MapMateCards.kt` |
| Room DB 저장 | 완료 | 입력값 검증 후 `routines` 테이블에 루틴을 실제 저장합니다. | `data/local`, `data/repository/RoomRoutineRepository.kt` |
| 저장된 루틴 목록 표시 | 완료 | Room `Flow`를 관찰해 저장된 루틴을 홈 대시보드와 루틴 목록 화면에 표시합니다. | `presentation/home`, `presentation/routine/RoutinesScreen.kt` |
| 루틴 수정/삭제 | 완료 | 루틴 목록 카드에서 기존 값을 루틴 등록 화면으로 불러와 수정하거나 Room DB에서 삭제할 수 있습니다. | `presentation/routine`, `data/local/RoutineDao.kt` |
| DataStore 설정 저장 | 완료 | 개인 보정 시간, 안전 여유 시간, 알림 설정값, 기본 이동수단을 Preferences DataStore에 저장하고 앱 시작 시 복원합니다. | `data/preferences/DataStoreSettingsRepository.kt`, `domain/repository/SettingsRepository.kt` |
| 실제 Kakao API | 완료 | Kakao Local API 키워드 장소 검색과 좌표→주소 변환을 연결했습니다. 키가 없거나 호출이 실패하면 장소 검색은 mock 후보, 현재 위치 주소는 fallback 문구를 사용합니다. | `data/remote/api/KakaoLocalApi.kt`, `data/remote/provider/KakaoPlaceSearchProvider.kt`, `data/remote/provider/KakaoReverseGeocodingProvider.kt` |
| 실제 ODsay API | 부분 완료 | ODsay 대중교통 경로 검색 provider를 연결했습니다. API 키와 데모 출발 좌표가 있어야 실제 호출합니다. | `data/remote/api/OdsayApi.kt`, `data/remote/provider/OdsayRouteEstimateProvider.kt` |
| 실제 Google Routes API | 부분 완료 | Google Routes provider를 연결했습니다. 도보/자동차 경로와 대중교통 fallback에 사용할 수 있으며, 자동차 모드는 `TRAFFIC_AWARE` routing preference를 사용합니다. | `data/remote/api/GoogleRoutesApi.kt`, `data/remote/provider/GoogleRoutesEstimateProvider.kt` |
| 버스 실시간 도착정보 | 완료 | ODsay 첫 버스 탑승 구간에서 정류소/노선 ID를 추출하고 서울특별시 버스도착정보조회 서비스로 첫 대기 시간을 조회해 기본 대기 기준보다 길 때 경로 시간을 보정합니다. 키가 없거나 매칭에 실패하면 기존 ODsay/Mock fallback을 유지합니다. | `data/remote/provider/SeoulBusRealtimeArrivalProvider.kt`, `data/remote/provider/SeoulBusArrivalXmlParser.kt` |
| TAGO 버스정류소정보 | 부분 완료 | ODsay 첫 버스 탑승 정류장 좌표가 있으면 TAGO 근접 정류소 조회로 `cityCode`, `nodeId` 후보를 찾습니다. 실제 지역별 매칭 예외는 API 키 기반 검증이 더 필요합니다. | `data/remote/api/TagoBusStationApi.kt`, `data/remote/provider/TagoBusArrivalProvider.kt` |
| TAGO 버스도착정보 | 부분 완료 | TAGO 정류소 후보의 도착 목록에서 ODsay 노선번호와 맞는 `arrtime`을 찾아 첫 버스 대기 시간을 보정합니다. 키/좌표/매칭 실패 시 기존 provider/fallback을 유지합니다. | `data/remote/api/TagoBusArrivalApi.kt`, `data/remote/provider/TagoBusArrivalProvider.kt` |
| 버스 실시간 위치정보 | 부분 완료 | 서울 버스 위치정보를 조회해 운행 중 차량 수와 정류장 접근 차량 수를 ODsay reason의 운행 상태 보조 설명으로 반영합니다. | `data/remote/api/SeoulBusPositionApi.kt`, `data/remote/provider/SeoulBusOperationStatusProvider.kt` |
| 지하철 실시간 도착정보 | 완료 | ODsay 첫 지하철 탑승역을 서울 지하철 실시간 도착정보 API로 조회하고 가장 빠른 도착 예정 시간을 사용해 기본 대기 기준보다 긴 지연분을 보정합니다. 키가 없거나 조회에 실패하면 기존 ODsay/Mock fallback을 유지합니다. | `data/remote/api/SeoulSubwayRealtimeApi.kt`, `data/remote/provider/SeoulSubwayRealtimeArrivalProvider.kt` |
| 지하철 열차 위치정보 | 부분 완료 | 서울 지하철 실시간 열차 위치정보를 조회해 같은 호선의 운행 상태와 현재 역 주변 열차 수를 ODsay reason의 보조 설명으로 반영합니다. | `data/remote/api/SeoulSubwayTrainPositionApi.kt`, `data/remote/provider/SeoulSubwayOperationStatusProvider.kt` |
| 자동차 traffic-aware 경로 | 완료 | 자동차 이동수단에서 Google Routes `TRAFFIC_AWARE` 정책을 적용합니다. | `data/remote/provider/GoogleRoutesEstimateProvider.kt` |
| 알림 | 완료 | 알림 사용 여부는 DataStore에 저장하며, 켜져 있으면 저장된 루틴과 경로 예상 시간을 기준으로 가장 가까운 다음 출발 알림을 `AlarmManager`에 예약합니다. Android 13 이상에서는 알림 권한을 요청합니다. | `data/alarm`, `presentation/settings/SettingsScreen.kt`, `MainActivity.kt` |
| WorkManager 재조회 | 완료 | 다음 출발 알림 30분 전에 unique one-time work를 예약해 경로 예상 시간을 다시 조회하고 알림/재조회 예약을 갱신합니다. | `data/alarm/AndroidDepartureRecheckScheduler.kt`, `data/alarm/DepartureRecheckWorker.kt` |
| 실시간 보정 스냅샷 | 완료 | ODsay 첫 탑승 실시간 보정 성공 결과를 `RouteRealtimeSnapshot`으로 Room DB에 저장하고, 실시간 도착정보 실패/매칭 실패 시 20분 이내의 마지막 성공 보정값만 재사용합니다. | `domain/model/RouteRealtimeSnapshot.kt`, `data/local/RouteRealtimeSnapshotDao.kt`, `data/repository/RoomRouteRealtimeSnapshotRepository.kt`, `data/remote/provider/OdsayRouteEstimateProvider.kt` |
| 전체 경로 예상 cache | 완료 | 실제 ODsay/Google 경로 provider 성공값을 6시간 Room cache로 저장하고, provider 실패 시 mock fallback 전에 마지막 성공 경로 예상값을 우선 재사용합니다. | `data/remote/provider/CachingRouteEstimateProvider.kt`, `data/local/RouteEstimateCacheDao.kt`, `data/repository/RoomRouteEstimateCacheRepository.kt` |
| 지금 출발 권장 처리 | 완료 | 보정된 권장 출발 시각이 이미 지났고 목표 도착 시각이 아직 남아 있으면 루틴 등록/홈/루틴 목록/상세 예측/이동 기록 화면에 `지금 출발`로 표시하고, 출발 알림도 즉시 예약합니다. | `domain/calculator/DepartureTimeCalculator.kt`, `domain/alarm/DepartureAlarmPlanner.kt`, `presentation/common/RoutineRecommendationUiModel.kt` |
| 상세 예측 화면 | 완료 | 권장 출발 시각, 계산 근거, 경로 요약을 표시하고 이동 기록 화면으로 진입합니다. | `presentation/prediction` |
| 이동 기록 화면 UI | 완료 | 출발 예정, 탑승, 도착 단계의 기록 흐름을 제공하고 도착 완료 시 Room에 기록을 저장합니다. | `presentation/tracking/TrackingScreen.kt`, `presentation/tracking/TrackingViewModel.kt` |
| 기록 완료 화면 UI | 완료 | 도착 액션 후 같은 페이지 내부 카드가 아니라 별도 기록 완료 화면으로 전환합니다. | `presentation/tracking/TrackingScreen.kt`, `presentation/MapMateApp.kt` |
| 이동 기록 저장 | 완료 | 루틴명, 출발지/목적지, 이동수단, 추천 출발 시각, 도착 시각, 도착 오차를 `commute_records` 테이블에 저장합니다. | `data/local/CommuteRecordEntity.kt`, `data/repository/RoomCommuteRecordRepository.kt` |
| 개인 보정값 업데이트 | 완료 | 이동 기록의 도착 오차를 기준으로 개인 보정 시간을 최대 ±5분 범위에서 자동 조정합니다. | `domain/model/AppSettings.kt`, `data/preferences/DataStoreSettingsRepository.kt`, `presentation/tracking/TrackingViewModel.kt` |
| 기록 탭 | 완료 | 저장된 이동 기록 목록, empty state, 전체 기록 수, 평균 도착 오차, 정시/빠른 도착률, 최근 5회 평균을 표시합니다. | `presentation/history/RecordsScreen.kt`, `presentation/history/RecordsViewModel.kt` |
| 홈 화면 | 완료 | 앱 첫 화면에서 오늘 권장 출발 시각과 오늘의 루틴 요약을 확인할 수 있습니다. | `presentation/home` |
| 통계 화면 | 완료 | 별도 탭을 추가하지 않고 기록 탭 상단에 최근 기록 기반 통계 요약을 제공합니다. | `presentation/history/RecordsScreen.kt`, `presentation/history/RecordsUiState.kt` |
| 설정 화면 | 완료 | 보정값, 기본 이동수단, 알림 사용 여부를 변경할 수 있습니다. | `presentation/settings` |

## 현재 앱 진입점

현재 `MainActivity`는 `MapMateApp`을 표시하고, `MapMateApp`이 홈/루틴/기록/설정 하단 내비게이션과 상세 화면 상태를 관리합니다.

```text
MainActivity
→ MapMateApp
├→ HomeRoute
├→ RoutinesRoute
├→ RecordsScreen
├→ SettingsRoute
├→ RoutineRegistrationRoute
├→ PredictionDetailRoute
├→ TrackingRoute
└→ TrackingCompletionScreen
```

## 현재 저장 동작

현재 `루틴 저장` 버튼은 입력값 검증 후 Room DB의 `routines` 테이블에 루틴을 저장합니다. 저장 성공 후 홈 화면으로 이동합니다. 저장된 루틴은 Room `Flow`를 통해 홈 대시보드와 루틴 목록 화면에 바로 표시됩니다. 루틴 목록의 `수정` 버튼은 선택한 루틴을 루틴 등록 화면에 채우고, `삭제` 버튼은 해당 루틴을 Room DB에서 제거합니다.

개인 보정 시간과 안전 여유 시간은 입력값이 0~60분 범위로 유효할 때 Preferences DataStore에 저장됩니다. 기본 이동수단과 알림 사용 여부도 같은 DataStore에 저장됩니다. 앱을 다시 실행하면 `SettingsRepository`를 통해 마지막 설정을 읽어 루틴 등록 화면과 설정 화면의 기본값으로 반영합니다.

알림 설정이 켜져 있으면 앱 실행 중 `SettingsRepository.settings`와 `RoutineRepository.observeRoutines()`를 관찰해 다음 출발 알림을 자동 재예약합니다. 예약 시 실제 경로 provider를 우선 사용하고, API 키가 없거나 호출이 실패하면 기존 mock fallback 이동 시간으로 권장 출발 시각을 계산합니다. 알림은 현재 가장 가까운 1개만 유지하며, 알림 수신 후 다음 반복 요일 알림을 다시 예약합니다.

다음 출발 알림이 30분보다 더 남아 있으면 WorkManager one-time work를 함께 예약합니다. 재조회 작업은 네트워크 연결 조건에서 실행되며, 실행 시 현재 루틴과 설정을 다시 읽고 `RouteEstimateProvider`를 다시 호출한 뒤 가장 가까운 출발 알림과 다음 재조회 작업을 갱신합니다. 출발까지 1분 이내이거나 이미 30분 재조회 구간 안에 들어온 경우에는 즉시 반복 예약을 만들지 않도록 기존 unique work를 취소합니다.

이동 기록 화면에서 이동 시작 후 도착을 완료하면 `CommuteRecordRepository`를 통해 Room DB의 `commute_records` 테이블에 기록을 저장합니다. 기록 탭은 저장된 기록을 최신 도착 순서로 표시하고, 목표 도착 시각 대비 오차를 함께 보여줍니다. 기록 저장이 성공하면 `SettingsRepository`가 도착 오차를 DataStore 개인 보정값에 반영합니다. 한 번의 기록이 보정값을 과도하게 흔들지 않도록 자동 조정 폭은 최대 ±5분으로 제한합니다.

## 현재 API 동작

`local.properties`에 `KAKAO_REST_API_KEY`, `ODSAY_API_KEY`, `GOOGLE_ROUTES_API_KEY`, `SEOUL_OPEN_API_KEY`, `SEOUL_BUS_SERVICE_KEY`, `TAGO_SERVICE_KEY`를 설정하면 실제 provider가 우선 동작합니다. 키가 없거나 API 호출이 실패하면 `FallbackPlaceSearchProvider`, `FallbackRouteEstimateProvider`가 기존 mock provider 결과를 반환하므로 발표용 MVP 흐름은 유지됩니다. 경로 fallback이 발생하면 루틴 등록 결과, 홈, 루틴 목록, 상세 예측, 이동 기록 화면에 기본 예상 시간을 사용했다는 상태 메시지를 표시합니다. 현재 위치 좌표의 주소 변환은 Kakao 키가 있을 때만 시도하고 실패 시 기존 현재 위치 fallback 주소를 사용합니다.

현재 경로 API는 루틴 등록 화면에서 선택한 출발지와 목적지 좌표를 사용합니다. 출발지는 장소 검색으로 선택하거나 `현재 위치 사용`으로 휴대폰 위치 좌표를 받아 설정할 수 있으며, Kakao 키가 있으면 현재 위치 좌표를 주소로 변환해 표시합니다.

ODsay 대중교통 길찾기의 예상 이동 시간은 기본 경로 시간으로 사용하고, 첫 탑승 구간의 버스/지하철 실시간 도착정보가 조회되면 대기 지연분만 보수적으로 추가 보정합니다. 버스는 서울 버스 도착정보를 먼저 사용하고, 좌표가 있는 경우 TAGO 정류소/도착정보 fallback을 시도합니다. 버스/지하철 위치정보는 운행 상태 보조 설명으로 reason에 반영합니다. 실시간 보정 성공값은 `RouteRealtimeSnapshot`으로 저장되며, 이후 실시간 도착정보가 실패하거나 매칭되지 않으면 20분 이내의 마지막 성공 보정값만 재사용합니다. 전체 경로 API 실패 시에는 6시간 이내의 `RouteEstimateCache`를 mock fallback 전에 재사용합니다. 출발 전 WorkManager 재조회도 같은 `RouteEstimateProvider`를 다시 호출하므로, 키와 좌표/노선 매칭이 맞으면 출발 30분 전 알림에도 실시간 도착정보 보정 또는 fresh snapshot/cache fallback이 반영됩니다.
