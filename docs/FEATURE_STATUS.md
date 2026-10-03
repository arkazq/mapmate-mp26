# MapMate 기능 상태

최신 점검: 2026-10-02. 아래의 구현 상태는 실기기/운영 API의 정상 동작 보장을 뜻하지 않습니다. 검증 범위와 한계는 [품질·사용성 점검 기록](QUALITY_REVIEW_2026_10_02.md)을 확인합니다.

## 사용성·복구 보강

| 기능 | 상태 | 설명 |
| -- | -- | -- |
| 화면 스택과 뒤로가기 | 구현·에뮬레이터 검증 | Navigation Compose, 화면별 ViewModel, 완료 후 홈 복귀, 수정/등록 초안 보존과 폐기 확인 |
| 공통 추천 경로 | 구현·단위 검증 | 홈/상세/루틴/등록/측정과 실제 알림이 공유 계산을 사용하고 백그라운드 재조회 변경을 반영 |
| 측정 중 재시작 | 구현·프로세스 종료 검증 | DataStore 세션에서 시작 당시 경로·시각·구간 상태 복원, 홈 이어하기 표시 |
| 완료 후 재시작 | 구현·프로세스 종료 검증 | Room 완료 이벤트를 제외하고 다음 출발 표시, 같은 이벤트 중복 기록 방지 |
| 원자적 구간 수정 | 구현·Room/UI 검증 | 날짜/시각 선택, 즉시 소요시간 표시, 변경한 구간만 수정 출처 표시, 잘못된 입력 전체 롤백 |
| 설정 저장/권한 상태 | 구현·에뮬레이터 검증 | 숫자 초안의 명시적 저장, 읽기 실패 재시도, 시스템 차단과 정확한 알람 권한 분리 |
| 큰 글꼴 | 구현·화면 확인 | 360dp 폭과 1.5/2배 글꼴에서 지표/이동수단 세로 배치와 입력형 시간 선택 |
| 실시간 파싱/통신 방어 | 구현·오프라인 검증 | 서울 1/2번째 메시지-초 짝 유지, 운행 종료 제외, TAGO 비정상 ETA 제외/HTTPS, XML 외부 선언 거부 |

## 구간별 소요시간 최적화 상태

| 기능 | 상태 | 설명 | 관련 파일 |
| -- | -- | -- | -- |
| ODsay subPath 구간 파싱 | 완료 | ODsay 대중교통 `subPath` 항목을 순서가 있는 `RouteSegment` 값으로 변환합니다. 도보 구간은 정류장/역까지 도보, 환승 도보, 목적지까지 도보로 분류합니다. | `data/remote/provider/OdsayRouteSegmentMapper.kt`, `domain/model/RouteSegment.kt` |
| 구간 저장 | 완료 | 구간 기록과 구간별 보정값을 Room에 저장합니다. DB 버전은 8이며 `route_segments`, `segment_time_adjustments` 테이블을 사용합니다. | `data/local/RouteSegmentEntity.kt`, `data/local/SegmentTimeAdjustmentEntity.kt`, `data/local/MapMateDatabase.kt` |
| 구간 기반 측정 UI | 완료 | 이동 기록 화면은 현재 활성 구간 하나에 집중하고, 구간 진행 상태와 실제 측정 시간을 이동 기록과 함께 저장합니다. | `presentation/tracking/TrackingScreen.kt`, `presentation/tracking/TrackingViewModel.kt`, `presentation/tracking/TrackingUiState.kt` |
| 구간 시간 수동 수정 | 완료 | 이동 완료 후 또는 기록 화면에서 저장된 구간의 시작/종료 시각을 수정할 수 있습니다. 수정된 값은 `isUserEdited = true`로 표시됩니다. | `presentation/segmentedit/RouteSegmentEditScreen.kt`, `presentation/segmentedit/RouteSegmentEditViewModel.kt` |
| 구간 소요시간 개인화 | 완료 | 최근 완료된 구간 기록을 `routineId`, `segmentType`, `routeName`, `startName`, `endName` 기준으로 묶고 평균 지연과 신뢰도를 계산해 이후 경로 예측에 반영합니다. | `domain/calculator/SegmentTimeAdjustmentCalculator.kt`, `data/remote/provider/SegmentAdjustedRouteEstimateProvider.kt` |
| 시간대/요일별 구간 학습 | 미구현 | 구간 보정값은 아직 시간대나 요일별로 분리하지 않습니다. | 향후 작업 |
| 자동 탑승/하차 감지 | 미구현 | 앱이 기기 위치를 기반으로 구간 시작/종료를 자동 추론하지는 않습니다. | 향후 작업 |
| 모바일 보안 기본 점검 | 완료 | 사용자 입력 제한, 앱 백업 차단, 데이터 추출 제외, release 축소/난독화, cleartext 범위 제한, 민감 파일 Git 추적 여부를 점검했습니다. 운영 전 API 키 제한, 백엔드 프록시, release 서명 정책은 후속 항목입니다. | `docs/MOBILE_SECURITY_CHECKLIST.md` |

현재 구현 상태를 기준으로 기능별 진행 상황을 정리합니다.

| 기능 | 상태 | 설명 | 관련 위치 |
| -- | -- | -- | ----- |
| Android 초기 프로젝트 | 완료 | Android Studio 생성 기반 프로젝트가 구성되어 있습니다. | `app`, `gradle`, `settings.gradle.kts` |
| 홈/루틴/기록/설정 하단 내비게이션 | 완료 | Material 3 하단 내비게이션으로 홈, 루틴, 기록, 설정 화면을 전환합니다. | `presentation/MapMateApp.kt`, `presentation/common/MapMateScaffold.kt` |
| 홈 대시보드 UI | 완료 | 현 시간 기준 가장 가까운 다음 출발 루틴을 메인 추천으로 표시하고, 출발까지 남은 시간, 추천 근거, 이동 시간/보정/안전 여유 요약, 저장된 전체 루틴 목록을 표시합니다. | `presentation/home/HomeScreen.kt`, `presentation/home/HomeViewModel.kt` |
| 루틴 목록 UI | 완료 | 저장된 루틴을 카드 목록으로 표시하고 수정/삭제/상세 예측 진입을 제공합니다. | `presentation/routine/RoutinesScreen.kt`, `presentation/routine/RoutinesViewModel.kt` |
| 루틴 등록 UI | 완료 | 루틴 이름, 장소, 도착 시각, 요일, 이동 수단, 보정 시간을 단계형 UI로 입력합니다. | `presentation/routine/RoutineRegistrationScreen.kt` |
| 루틴 등록 입력 보안 | 완료 | 도착 목표 시각은 TimePicker로 선택하고, 루틴명/장소 검색어는 제어문자 제거와 길이 제한을 적용하며, 보정 시간은 ASCII 숫자만 허용합니다. | `presentation/routine/RoutineRegistrationScreen.kt`, `presentation/routine/RoutineRegistrationViewModel.kt` |
| 목적지 후보 표시 | 완료 | Kakao Local API 키가 있으면 실제 장소 검색 결과를 표시하고, 실패하거나 키가 없으면 mock 후보를 표시합니다. | `data/remote/provider/KakaoPlaceSearchProvider.kt`, `data/mock/MockPlaceSearchProvider.kt` |
| 목적지 선택 | 완료 | 목적지 후보를 선택하면 `selectedDestination`에 반영됩니다. | `presentation/routine/RoutineRegistrationViewModel.kt` |
| 출발지 검색 선택 | 완료 | Kakao Local API 또는 mock 장소 후보에서 출발지를 검색하고 선택할 수 있습니다. | `presentation/routine/RoutineRegistrationScreen.kt`, `presentation/routine/RoutineRegistrationViewModel.kt` |
| 현재 위치 출발지 선택 | 완료 | Android 위치 권한을 요청하고 휴대폰 현재 위치 좌표를 출발지로 설정합니다. Kakao 키가 있으면 좌표를 주소로 변환해 출발지 주소로 표시하고, 실패하면 기존 fallback 주소를 유지합니다. | `data/location/AndroidCurrentLocationProvider.kt`, `data/remote/provider/KakaoReverseGeocodingProvider.kt`, `presentation/routine/RoutineRegistrationScreen.kt` |
| 반복 요일 선택 | 완료 | 월~일 반복 요일을 선택/해제할 수 있습니다. | `presentation/common/MapMateSelectors.kt` |
| 이동 수단 선택 | 완료 | `TRANSIT`, `WALK`, `CAR`를 선택합니다. 보통 크기에서는 동일 폭, 큰 글꼴/좁은 화면에서는 세로 배치하며 선택 상태를 접근성 서비스에 제공합니다. | `domain/model/TransportMode.kt`, `presentation/common/MapMateSelectors.kt` |
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
| ODsay 후보 경로 랭킹 | 완료 | ODsay `path` 후보를 최대 5개 평가하고, 출발 30분 이내에는 첫 버스 실시간 도착정보와 정류장 접근 시간을 비교해 실제 탑승 가능성이 낮은 후보에 페널티를 줍니다. 목표 도착 시각을 놓치는 후보에는 큰 페널티를 주고, 첫 버스를 타기 위해 더 일찍 나가야 하면 안전 탑승 출발 시각을 계산합니다. 후보 전환 이득이 3분 미만이면 기존 1순위 경로를 유지합니다. | `data/remote/provider/OdsayRouteEstimateProvider.kt`, `data/remote/provider/RouteCandidateEvaluator.kt`, `app/src/test/kotlin/com/mapmate/data/remote/provider/OdsayRouteEstimateProviderTest.kt`, `app/src/test/kotlin/com/mapmate/data/remote/provider/RouteCandidateEvaluatorTest.kt` |
| 후보 랭킹 UI | 완료 | 홈 화면은 추천 버스와 탑승 여유를 요약 카드로 보여주고, 안전 탑승 때문에 출발 시각을 앞당겨야 하면 해당 시각과 앞당겨진 분 수를 표시합니다. 상세 예측 화면은 `탑승 판단` 카드에서 추천 후보와 대안 후보를 표시합니다. | `presentation/common/RouteBoardingAdviceCards.kt`, `presentation/home/HomeScreen.kt`, `presentation/prediction/PredictionDetailScreen.kt`, `domain/model/RouteEstimate.kt` |
| 계산 결과 사용자 문구 | 완료 | 루틴 등록의 권장 출발 시각 계산 결과는 API 내부 reason 문자열 대신 이동 시간, 개인 보정, 안전 여유, 실시간 반영 여부를 사용자용 문장으로 표시합니다. | `presentation/routine/RoutineRegistrationUiState.kt`, `presentation/routine/RoutineRegistrationScreen.kt` |
| 실제 Google Routes API | 부분 완료 | Google Routes provider를 연결했습니다. 도보/자동차 경로와 대중교통 fallback에 사용할 수 있으며, 자동차 모드는 `TRAFFIC_AWARE` routing preference를 사용합니다. | `data/remote/api/GoogleRoutesApi.kt`, `data/remote/provider/GoogleRoutesEstimateProvider.kt` |
| 버스 실시간 도착정보 | 부분 완료 | 출발 예정 시각 30분 이내에 ODsay 첫 버스 탑승 구간에서 정류소/노선 정보를 추출하고 서울특별시 버스도착정보조회 서비스로 첫 대기 시간을 조회해 기본 대기 기준보다 길 때 경로 시간을 보정합니다. 서울버스는 ODsay `startArsID`가 있으면 `getStationByUid` 정류장 도착목록에서 `rtNm`/`busRouteAbrv`를 노선번호와 매칭하고, 실패하면 기존 `busRouteId` 기준 조회로 fallback합니다. | `data/remote/provider/SeoulBusRealtimeArrivalProvider.kt`, `data/remote/provider/SeoulBusArrivalXmlParser.kt` |
| TAGO 버스정류소정보 | 부분 완료 | ODsay 첫 버스 탑승 정류장 좌표가 있으면 TAGO 근접 정류소 조회로 `cityCode`, `nodeId` 후보를 찾습니다. 경기 주요 좌표에서 정류소/도착정보 응답을 확인했으며, 지역별 정류장 표기 예외는 계속 보강합니다. | `data/remote/api/TagoBusStationApi.kt`, `data/remote/provider/TagoBusArrivalProvider.kt` |
| TAGO 버스도착정보 | 부분 완료 | TAGO 정류소 후보의 도착 목록에서 ODsay 노선번호와 맞는 `arrtime`을 찾아 첫 버스 대기 시간을 보정합니다. `11(남양여객)`처럼 ODsay 노선명에 붙는 괄호/대괄호 업체명은 제거해 TAGO `11`과 매칭합니다. 키/좌표/매칭 실패 시 기존 provider/fallback을 유지합니다. | `data/remote/api/TagoBusArrivalApi.kt`, `data/remote/provider/TagoBusArrivalProvider.kt` |
| 버스 실시간 위치정보 | 부분 완료 | 서울 버스 위치정보를 조회해 운행 중 차량 수와 정류장 접근 차량 수를 ODsay reason의 운행 상태 보조 설명으로 반영합니다. | `data/remote/api/SeoulBusPositionApi.kt`, `data/remote/provider/SeoulBusOperationStatusProvider.kt` |
| 지하철 실시간 도착정보 | 부분 완료 | 서울 지하철 실시간 도착정보 provider는 구현되어 있습니다. 현재 ODsay 후보 경로 랭킹의 시간 보정은 첫 버스 구간에 우선 적용하며, 지하철 도착정보를 후보 랭킹에 직접 반영하는 연결은 향후 작업입니다. | `data/remote/api/SeoulSubwayRealtimeApi.kt`, `data/remote/provider/SeoulSubwayRealtimeArrivalProvider.kt` |
| 지하철 열차 위치정보 | 부분 완료 | 서울 지하철 실시간 열차 위치정보를 조회해 같은 호선의 운행 상태와 현재 역 주변 열차 수를 ODsay reason의 보조 설명으로 반영합니다. | `data/remote/api/SeoulSubwayTrainPositionApi.kt`, `data/remote/provider/SeoulSubwayOperationStatusProvider.kt` |
| 자동차 traffic-aware 경로 | 완료 | 자동차 이동수단에서 Google Routes `TRAFFIC_AWARE` 정책을 적용합니다. | `data/remote/provider/GoogleRoutesEstimateProvider.kt` |
| 알림 | 완료 | 알림 사용 여부는 DataStore에 저장하며, 켜져 있으면 저장된 루틴과 경로 예상 시간을 기준으로 가장 가까운 다음 출발 알림을 `AlarmManager`에 예약합니다. Android 13 이상에서는 알림 권한을 요청합니다. | `data/alarm`, `presentation/settings/SettingsScreen.kt`, `MainActivity.kt` |
| 출발 전 상태 알림 | 완료 | 출발 30분 이내에는 설정 토글에 따라 권장 출발 시각을 일반 알림으로 표시하고, T-30/T-15/T-10/T-5 재조회 결과로 시각이 바뀌면 같은 알림 ID로 갱신합니다. 실제 출발 알림이 울리면 상태 알림을 정리합니다. | `data/alarm/AndroidPredepartureStatusNotificationPublisher.kt`, `data/alarm/DepartureAlarmCoordinator.kt`, `presentation/settings/SettingsScreen.kt` |
| WorkManager 재조회 | 완료 | 다음 출발 알림 전 `T-60/T-30/T-15/T-10/T-5` 기준으로 unique one-time work를 예약하고, 긴 이동에는 `T-90/T-120` 재조회도 추가합니다. 재조회 worker는 기존 출발 예정 시각과 목표 도착 epoch millis를 route provider에 전달해 30분 정책 안에서 실시간 보정과 안전 탑승 출발 시각이 반영되도록 합니다. | `data/alarm/AndroidDepartureRecheckScheduler.kt`, `data/alarm/DepartureRecheckWorker.kt`, `data/alarm/DepartureAlarmCoordinator.kt` |
| 실시간 보정 스냅샷 | 완료 | 출발 30분 이내 첫 버스 실시간 보정 성공 결과를 `RouteRealtimeSnapshot`으로 Room DB에 저장하고, 같은 30분 정책 안에서 실시간 도착정보 실패/매칭 실패 시 20분 이내의 마지막 성공 보정값만 재사용합니다. | `domain/model/RouteRealtimeSnapshot.kt`, `data/local/RouteRealtimeSnapshotDao.kt`, `data/repository/RoomRouteRealtimeSnapshotRepository.kt`, `data/remote/provider/OdsayRouteEstimateProvider.kt` |
| 전체 경로 예상 cache | 완료 | 실시간 보정이 없는 실제 ODsay/Google 경로 provider 성공값을 6시간 Room cache로 저장하고, provider 실패 시 mock fallback 전에 마지막 성공 경로 예상값을 우선 재사용합니다. `RouteEstimate.hasRealtimeAdjustment`가 true인 결과는 저장하지 않습니다. | `domain/model/RouteEstimate.kt`, `data/remote/provider/CachingRouteEstimateProvider.kt`, `data/local/RouteEstimateCacheDao.kt`, `data/repository/RoomRouteEstimateCacheRepository.kt` |
| 지금 출발 권장 처리 | 완료 | 보정된 권장 출발 시각이 이미 지났고 목표 도착 시각이 아직 남아 있으면 루틴 등록/홈/루틴 목록/상세 예측/이동 기록 화면에 `지금 출발`로 표시하고, 출발 알림도 즉시 예약합니다. | `domain/calculator/DepartureTimeCalculator.kt`, `domain/alarm/DepartureAlarmPlanner.kt`, `presentation/common/RoutineRecommendationUiModel.kt` |
| 상세 예측 화면 | 완료 | 권장 출발 시각, 계산 근거, 경로 요약을 표시하고 이동 기록 화면으로 진입합니다. | `presentation/prediction` |
| 이동 기록 화면 UI | 완료 | 출발 예정, 탑승, 도착 단계의 기록 흐름을 제공하고 도착 완료 시 Room에 기록을 저장합니다. | `presentation/tracking/TrackingScreen.kt`, `presentation/tracking/TrackingViewModel.kt` |
| 기록 완료 화면 UI | 완료 | 도착 액션 후 같은 페이지 내부 카드가 아니라 별도 기록 완료 화면으로 전환합니다. | `presentation/tracking/TrackingScreen.kt`, `presentation/MapMateApp.kt` |
| 이동 기록 저장 | 완료 | 루틴명, 출발지/목적지, 이동수단, 추천 출발 시각, 도착 시각, 도착 오차를 `commute_records` 테이블에 저장합니다. | `data/local/CommuteRecordEntity.kt`, `data/repository/RoomCommuteRecordRepository.kt` |
| 개인 보정값 업데이트 | 완료 | 1분 이상 측정한 이동의 도착 오차로 해당 루틴 보정값을 최대 ±5분 조정합니다. 전역 기본값이나 동시 수정한 루틴 정보를 덮어쓰지 않습니다. 준비 지연만 독립 학습하는 정책은 미구현입니다. | `domain/calculator/PersonalBufferOptimizer.kt`, `presentation/tracking/TrackingViewModel.kt`, `data/repository/RoomRoutineRepository.kt` |
| 기록 탭 | 완료 | 저장된 이동 기록 목록, empty state, 전체 기록 수, 평균 도착 오차, 정시/빠른 도착률, 최근 5회 평균을 표시합니다. | `presentation/history/RecordsScreen.kt`, `presentation/history/RecordsViewModel.kt` |
| 홈 화면 | 완료 | 앱 첫 화면에서 현 시간 기준 가장 가까운 다음 출발 루틴의 권장 출발 시각과 저장된 전체 루틴 목록을 확인할 수 있습니다. 출발까지 남은 시간이 60분 이상이면 `k시간 l분` 형식으로 표시합니다. | `presentation/home`, `presentation/common/RoutineRecommendationUiModel.kt` |
| 통계 화면 | 완료 | 별도 탭을 추가하지 않고 기록 탭 상단에 최근 기록 기반 통계 요약을 제공합니다. | `presentation/history/RecordsScreen.kt`, `presentation/history/RecordsUiState.kt` |
| 설정 화면 | 완료 | 보정값, 기본 이동수단, 알림 사용 여부를 변경할 수 있습니다. | `presentation/settings` |
| 모바일 보안 기본 하드닝 | 완료 | 앱 자동 백업을 비활성화하고 Android 데이터 추출에서 DB/DataStore/파일을 제외했으며 release 빌드에 minify와 resource shrink를 적용했습니다. | `AndroidManifest.xml`, `data_extraction_rules.xml`, `app/build.gradle.kts` |

## 현재 앱 진입점

현재 `MainActivity`는 `MapMateApp`을 표시하고, `MapMateApp`의 `NavHost`가 하단 탭 및 등록/상세/측정/완료/구간 수정 화면 스택을 관리합니다.

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

WorkManager 재조회는 다음 출발 기준 T-60/T-30/T-15/T-10/T-5에 예약하고 긴 이동에는 더 이른 작업도 추가합니다. 실행 시 영속 예약과 현재 루틴/설정/완료 이벤트가 여전히 일치하는지 검증합니다. 이미 지난 작업, 변경 전 이벤트와 중복 알림은 배제하고 새 예약의 이전 작업을 정리합니다. 작업 실행 시각은 Android 백그라운드 정책에 따라 지연될 수 있습니다.

이동 시작/구간 변경은 먼저 DataStore 세션에 저장하고, 도착 완료 시 Room에 요약과 구간을 함께 저장합니다. 완료 이벤트 중복 저장을 방지하며 성공 후 세션을 제거합니다. 기록 탭은 실제 출발/도착 날짜와 도착 오차를 표시합니다. 기록 저장 후 해당 루틴 개인 보정값만 조건부 갱신하고 자동 조정 폭은 최대 ±5분으로 제한합니다. 전역 설정 기본값은 바뀌지 않습니다.

## 현재 API 동작

`local.properties`에 `KAKAO_REST_API_KEY`, `ODSAY_API_KEY`, `GOOGLE_ROUTES_API_KEY`, `SEOUL_OPEN_API_KEY`, `SEOUL_BUS_SERVICE_KEY`, `TAGO_SERVICE_KEY`를 설정하면 실제 provider가 우선 동작합니다. 키가 없거나 API 호출이 실패하면 `FallbackPlaceSearchProvider`, `FallbackRouteEstimateProvider`가 기존 mock provider 결과를 반환하므로 발표용 MVP 흐름은 유지됩니다. 경로 fallback이 발생하면 루틴 등록 결과, 홈, 루틴 목록, 상세 예측, 이동 기록 화면에 기본 예상 시간을 사용했다는 상태 메시지를 표시합니다. 현재 위치 좌표의 주소 변환은 Kakao 키가 있을 때만 시도하고 실패 시 기존 현재 위치 fallback 주소를 사용합니다.

현재 경로 API는 루틴 등록 화면에서 선택한 출발지와 목적지 좌표를 사용합니다. 출발지는 장소 검색으로 선택하거나 `현재 위치 사용`으로 휴대폰 위치 좌표를 받아 설정할 수 있으며, Kakao 키가 있으면 현재 위치 좌표를 주소로 변환해 표시합니다.

ODsay 대중교통 길찾기의 예상 이동 시간은 기본 경로 시간으로 사용하고, `path` 후보는 최대 5개까지 비교합니다. 출발 예정 시각이 30분 이내이면 후보별 첫 버스 탑승 구간의 실시간 도착정보를 조회해 대기 지연분을 보수적으로 추가 보정하고, 정류장까지 접근 시간 대비 버스 도착 시간이 너무 빠른 후보는 놓칠 위험 페널티를 받습니다. 목표 도착 시각을 놓치는 후보는 가장 큰 페널티를 받으며, 첫 버스를 타려면 기존 권장 출발 시각보다 더 일찍 나가야 하는 경우에는 `버스 도착 시각 - 정류장 접근 시간 - 최소 탑승 여유 3분` 기준으로 안전 탑승 출발 시각을 계산해 홈/상세/알림 시각에 반영합니다. 버스는 서울 버스 도착정보를 먼저 사용하고, 좌표가 있는 경우 TAGO 정류소/도착정보 fallback을 시도합니다. 후보 전환 이득이 3분 미만이면 기존 ODsay 1순위 경로를 유지합니다. 버스/지하철 위치정보는 운행 상태 보조 설명으로 reason에 반영합니다. 실시간 보정 성공값은 `RouteRealtimeSnapshot`으로 저장되며, 이후 실시간 도착정보가 실패하거나 매칭되지 않으면 출발 30분 이내에서만 20분 이내의 마지막 성공 보정값을 재사용합니다. 실시간 보정 또는 snapshot fallback이 적용된 결과는 일반 `RouteEstimateCache`에 저장하지 않습니다. 전체 경로 API 실패 시에는 6시간 이내의 `RouteEstimateCache`를 mock fallback 전에 재사용합니다. 출발 전 WorkManager 재조회도 같은 `RouteEstimateProvider`를 다시 호출하므로, 키와 좌표/노선 매칭이 맞으면 출발 전 재조회에 실시간 도착정보 보정 또는 fresh snapshot fallback이 반영됩니다. 서울버스는 ODsay 노선 ID가 서울버스 `busRouteId`와 다른 경로를 위해 정류장 ARS 기준 조회와 노선명 매칭을 먼저 사용합니다. TAGO는 ODsay 노선명 괄호 업체명 표기를 제거해 경기/전국 노선 매칭률을 높였습니다.
# 현재 상태 참고

최신 변경과 미검증 범위는 `QUALITY_REVIEW_2026_10_02.md`와 `MOBILE_SECURITY_CHECKLIST.md`를 확인합니다. `IMPLEMENTATION_UPDATE_2026_06_16.md`는 이전 구현 이력입니다. 주변 버스 직접 탐색, 전체 환승 실시간 최적화, 요일/시간대별 학습, 운영 API 키 보호 전략은 후속 작업입니다.
