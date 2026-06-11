# MapMate Feature Status

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
| 현재 위치 출발지 선택 | 완료 | Android 위치 권한을 요청하고 휴대폰 현재 위치 좌표를 출발지로 설정할 수 있습니다. | `data/location/AndroidCurrentLocationProvider.kt`, `presentation/routine/RoutineRegistrationScreen.kt` |
| 반복 요일 선택 | 완료 | 월~일 반복 요일을 선택/해제할 수 있습니다. | `presentation/common/MapMateSelectors.kt` |
| 이동 수단 선택 | 완료 | `TRANSIT`, `WALK`, `CAR` 이동 수단을 선택할 수 있으며 설정 화면에서는 3개 카드가 동일 폭으로 배치됩니다. | `domain/model/TransportMode.kt`, `presentation/common/MapMateSelectors.kt` |
| 권장 출발 시각 계산 | 완료 | 실제 경로 API 또는 mock 예상 이동 시간과 보정 시간을 기준으로 권장 출발 시각을 계산합니다. | `presentation/routine/RoutineRegistrationViewModel.kt` |
| `DepartureTimeCalculator` | 완료 | 순수 Kotlin 계산기로 권장 출발 시각을 계산합니다. | `domain/calculator/DepartureTimeCalculator.kt` |
| 계산 로직 테스트 | 완료 | 09:00, 42분, 6분, 5분 입력 시 08:07을 검증합니다. | `app/src/test/kotlin/com/mapmate/domain/calculator/DepartureTimeCalculatorTest.kt` |
| Mock 장소 검색 provider | 완료 | `PlaceSearchProvider`의 mock 구현체가 있습니다. | `data/mock/MockPlaceSearchProvider.kt` |
| Mock 이동 시간 provider | 완료 | 이동 수단별 고정 예상 시간을 반환합니다. | `data/mock/MockRouteEstimateProvider.kt` |
| API fallback provider | 완료 | 실제 API 실패, 키 누락, 좌표 누락 시 기존 mock 결과로 복구합니다. | `data/remote/provider/FallbackPlaceSearchProvider.kt`, `data/remote/provider/FallbackRouteEstimateProvider.kt` |
| Room DB 저장 | 완료 | 입력값 검증 후 `routines` 테이블에 루틴을 실제 저장합니다. | `data/local`, `data/repository/RoomRoutineRepository.kt` |
| 저장된 루틴 목록 표시 | 완료 | Room `Flow`를 관찰해 저장된 루틴을 홈 대시보드와 루틴 목록 화면에 표시합니다. | `presentation/home`, `presentation/routine/RoutinesScreen.kt` |
| 루틴 수정/삭제 | 완료 | 루틴 목록 카드에서 기존 값을 루틴 등록 화면으로 불러와 수정하거나 Room DB에서 삭제할 수 있습니다. | `presentation/routine`, `data/local/RoutineDao.kt` |
| DataStore 설정 저장 | 완료 | 개인 보정 시간, 안전 여유 시간, 알림 설정값, 기본 이동수단을 Preferences DataStore에 저장하고 앱 시작 시 복원합니다. | `data/preferences/DataStoreSettingsRepository.kt`, `domain/repository/SettingsRepository.kt` |
| 실제 Kakao API | 부분 완료 | Kakao Local API 키워드 장소 검색을 연결했습니다. API 키가 없거나 실패하면 mock 후보를 사용합니다. | `data/remote/api/KakaoLocalApi.kt`, `data/remote/provider/KakaoPlaceSearchProvider.kt` |
| 실제 ODsay API | 부분 완료 | ODsay 대중교통 경로 검색 provider를 연결했습니다. API 키와 데모 출발 좌표가 있어야 실제 호출합니다. | `data/remote/api/OdsayApi.kt`, `data/remote/provider/OdsayRouteEstimateProvider.kt` |
| 실제 Google Routes API | 부분 완료 | Google Routes provider를 연결했습니다. 도보/자동차 경로와 대중교통 fallback에 사용할 수 있습니다. | `data/remote/api/GoogleRoutesApi.kt`, `data/remote/provider/GoogleRoutesEstimateProvider.kt` |
| 버스 실시간 도착정보 | 미구현 | 첫 탑승 정류장의 실제 버스 대기 시간을 보정하기 위한 ODsay 또는 서울특별시 버스도착정보 provider가 아직 없습니다. | 예정 |
| 버스 실시간 위치정보 | 미구현 | 도착정보 보조와 운행 지연 판단을 위한 ODsay 또는 서울특별시 버스위치정보 provider가 아직 없습니다. | 예정 |
| 지하철 실시간 도착정보 | 미구현 | 첫 탑승역 또는 환승역의 실제 지하철 도착 예정 시간을 보정하는 provider가 아직 없습니다. | 예정 |
| 지하철 열차 위치정보 | 미구현 | 지하철 도착정보 보조와 운행 상태 표시를 위한 열차 위치정보 provider가 아직 없습니다. | 예정 |
| 자동차 traffic-aware 경로 | 미구현 | 자동차 이동수단에서 Google Routes `TRAFFIC_AWARE` 정책을 적용하는 provider 정책은 아직 없습니다. | 예정 |
| 알림 | 부분 완료 | 알림 사용 여부 설정값은 DataStore에 저장합니다. `AlarmManager` 기반 실제 예약은 아직 없습니다. | `presentation/settings/SettingsScreen.kt`, 예정 |
| WorkManager 재조회 | 미구현 | 출발 전 이동 시간 재조회 작업은 아직 없습니다. | 예정 |
| 상세 예측 화면 | 완료 | 권장 출발 시각, 계산 근거, 경로 요약을 표시하고 이동 기록 화면으로 진입합니다. | `presentation/prediction` |
| 이동 기록 화면 UI | 완료 | 출발 예정, 탑승, 도착 단계의 기록 흐름을 제공하고 도착 완료 시 Room에 기록을 저장합니다. | `presentation/tracking/TrackingScreen.kt`, `presentation/tracking/TrackingViewModel.kt` |
| 기록 완료 화면 UI | 완료 | 도착 액션 후 같은 페이지 내부 카드가 아니라 별도 기록 완료 화면으로 전환합니다. | `presentation/tracking/TrackingScreen.kt`, `presentation/MapMateApp.kt` |
| 이동 기록 저장 | 완료 | 루틴명, 출발지/목적지, 이동수단, 추천 출발 시각, 도착 시각, 도착 오차를 `commute_records` 테이블에 저장합니다. | `data/local/CommuteRecordEntity.kt`, `data/repository/RoomCommuteRecordRepository.kt` |
| 개인 보정값 업데이트 | 완료 | 이동 기록의 도착 오차를 기준으로 개인 보정 시간을 최대 ±5분 범위에서 자동 조정합니다. | `domain/model/AppSettings.kt`, `data/preferences/DataStoreSettingsRepository.kt`, `presentation/tracking/TrackingViewModel.kt` |
| 기록 탭 | 완료 | 저장된 이동 기록 목록과 empty state를 표시합니다. | `presentation/history/RecordsScreen.kt`, `presentation/history/RecordsViewModel.kt` |
| 홈 화면 | 완료 | 앱 첫 화면에서 오늘 권장 출발 시각과 오늘의 루틴 요약을 확인할 수 있습니다. | `presentation/home` |
| 통계 화면 | 미구현 | 최근 기록/통계 화면은 아직 없습니다. | 예정 |
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

이동 기록 화면에서 이동 시작 후 도착을 완료하면 `CommuteRecordRepository`를 통해 Room DB의 `commute_records` 테이블에 기록을 저장합니다. 기록 탭은 저장된 기록을 최신 도착 순서로 표시하고, 목표 도착 시각 대비 오차를 함께 보여줍니다. 기록 저장이 성공하면 `SettingsRepository`가 도착 오차를 DataStore 개인 보정값에 반영합니다. 한 번의 기록이 보정값을 과도하게 흔들지 않도록 자동 조정 폭은 최대 ±5분으로 제한합니다.

## 현재 API 동작

`local.properties`에 `KAKAO_REST_API_KEY`, `ODSAY_API_KEY`, `GOOGLE_ROUTES_API_KEY`, `MAPMATE_ORIGIN_LATITUDE`, `MAPMATE_ORIGIN_LONGITUDE`를 설정하면 실제 provider가 우선 동작합니다. 키가 없거나 API 호출이 실패하면 `FallbackPlaceSearchProvider`, `FallbackRouteEstimateProvider`가 기존 mock provider 결과를 반환하므로 발표용 MVP 흐름은 유지됩니다.

현재 경로 API는 루틴 등록 화면에서 선택한 출발지와 목적지 좌표를 사용합니다. 출발지는 장소 검색으로 선택하거나 `현재 위치 사용`으로 휴대폰 위치 좌표를 받아 설정할 수 있습니다.

ODsay 대중교통 길찾기의 예상 이동 시간은 기본 경로 시간으로 사용하지만, 현재 버스/지하철 지연이나 실제 정류장/역 도착 예정 시간을 완전히 보장하는 값으로 보지는 않습니다. 실시간성을 높이려면 첫 탑승 구간 기준 버스/지하철 실시간 도착정보 provider와 출발 전 WorkManager 재조회가 추가되어야 합니다. 구체적인 API 후보와 우선순위는 `docs/API_STRATEGY.md`에 정리되어 있습니다.
