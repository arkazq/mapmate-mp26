# MapMate 아키텍처

## 구간별 소요시간 최적화 아키텍처

구간별 소요시간 최적화 흐름은 기존 경로 예측과 이동 기록 파이프라인을 확장하며, 기존 fallback 동작을 대체하지 않습니다.

```text
ODsay 경로 결과
-> OdsayRouteSegmentMapper
-> RouteEstimate.segments
-> TrackingViewModel 구간 측정
-> CommuteRecord.routeSegments
-> route_segments 테이블
-> SegmentTimeAdjustmentCalculator
-> segment_time_adjustments 테이블
-> SegmentAdjustedRouteEstimateProvider
-> 보정된 RouteEstimate.estimatedMinutes
```

주요 책임:

- `RouteSegment`: 도보, 버스, 지하철, 환승 도보, 목적지 도보, 알 수 없는 이동 구간 하나를 표현하는 도메인 모델입니다.
- `SegmentTimeAdjustment`: 안정적인 구간 key에 대해 학습된 평균 지연값입니다.
- `OdsayRouteSegmentMapper`: ODsay `subPath` 데이터를 순서가 있는 `RouteSegment` 값으로 변환합니다.
- `RoomCommuteRecordRepository`: 이동 기록과 구간 기록을 저장한 뒤 구간 보정값을 갱신합니다.
- `SegmentTimeAdjustmentCalculator`: 최근 완료된 구간을 묶어 가중 평균 지연과 신뢰도를 계산합니다.
- `SegmentAdjustedRouteEstimateProvider`: 기존 경로 예측 provider를 감싸고, 신뢰도가 충분한 구간 지연값을 적용합니다.
- `TrackingScreen`, `TrackingViewModel`: 이동 중 구간 시작/종료 이벤트를 수집합니다.
- `RouteSegmentEditScreen`, `RouteSegmentEditViewModel`: 이동 완료 후 저장된 구간 시간을 수정합니다.

대체 처리 규칙:

- `RouteEstimate.segments`가 비어 있으면 기존 단일 소요시간 기반 측정 UI를 유지합니다.
- 구간 보정값이 없으면 원래 경로 예측값을 사용합니다.
- 신뢰도가 너무 낮으면 구간 보정값을 적용하지 않습니다.
- `personalBufferMinutes`는 구간 지연과 분리합니다. 이 값은 출발/준비 습관을 나타내고, 구간 지연은 실제 이동 소요시간 오차를 나타냅니다.

이 문서는 현재 MapMate 프로젝트의 실제 구현 구조를 설명합니다. 새 기능을 추가할 때는 이 문서를 기준으로 어느 패키지에 코드를 둘지 판단합니다.

현재 앱은 홈 대시보드, 루틴 목록, 단계형 루틴 등록, 상세 예측, 이동 기록 저장, 기록 완료 UI, 기록 목록/통계 요약, 도착 오차 기반 개인 보정 자동 업데이트, 설정 화면, AlarmManager 기반 출발 알림, WorkManager 기반 출발 전 재조회, 버스/지하철 실시간 도착정보 기반 대기 지연 보정, TAGO 버스 도착정보 fallback, 버스/지하철 위치정보 기반 운행 상태 보조 설명, 전체 경로 예상값 cache까지 구현되어 있습니다. Room 기반 루틴/이동 기록/경로 cache 저장과 DataStore 기반 설정 저장은 연결되어 있지만, Navigation Compose는 아직 구현되어 있지 않습니다.

## 현재 아키텍처 개요

현재 구조는 다음 계층을 기준으로 나뉩니다.

```text
presentation → domain ← data
```

- `presentation`: Compose 화면, UI state, UI event, ViewModel
- `domain`: Android 의존성이 없는 모델과 계산 로직, provider/repository interface
- `data`: mock provider 구현체, Room local 저장소, DataStore 설정 저장소, repository 구현체
- `di`: 앱 수준 repository 생성 컨테이너

## 현재 패키지 구조

```text
com.mapmate
├─ di
│  └─ AppContainer.kt
├─ presentation
│  ├─ MapMateApp.kt
│  ├─ common
│  │  ├─ MapMateCards.kt
│  │  ├─ MapMateDesign.kt
│  │  ├─ MapMateIcon.kt
│  │  ├─ MapMateScaffold.kt
│  │  ├─ MapMateSelectors.kt
│  │  ├─ RoutineDisplayLabels.kt
│  │  ├─ RoutineRecommendationUiModel.kt
│  │  └─ RoutineSummaryCard.kt
  │  ├─ history
│  │  ├─ RecordsScreen.kt
│  │  ├─ RecordsViewModel.kt
│  │  └─ RecordsUiState.kt
│  ├─ home
│  │  ├─ HomeScreen.kt
│  │  ├─ HomeViewModel.kt
│  │  └─ HomeUiState.kt
│  ├─ prediction
│  │  ├─ PredictionDetailScreen.kt
│  │  ├─ PredictionDetailViewModel.kt
│  │  └─ PredictionDetailUiState.kt
│  ├─ routine
│  │  ├─ RoutineRegistrationScreen.kt
│  │  ├─ RoutineRegistrationComponents.kt
│  │  ├─ RoutineRegistrationViewModel.kt
│  │  ├─ RoutineRegistrationUiState.kt
│  │  ├─ RoutineRegistrationEvent.kt
│  │  ├─ RoutinesScreen.kt
│  │  ├─ RoutinesViewModel.kt
│  │  └─ RoutinesUiState.kt
│  ├─ settings
│  │  ├─ SettingsScreen.kt
│  │  ├─ SettingsViewModel.kt
│  │  ├─ SettingsUiState.kt
│  │  └─ SettingsEvent.kt
│  └─ tracking
│     ├─ TrackingScreen.kt
│     ├─ TrackingViewModel.kt
│     └─ TrackingUiState.kt
├─ domain
│  ├─ alarm
│  │  ├─ DepartureAlarmPlanner.kt
│  │  ├─ DepartureAlarmSchedule.kt
│  │  ├─ DepartureAlarmScheduler.kt
│  │  └─ DepartureRecheckScheduler.kt
│  ├─ model
│  │  ├─ AppSettings.kt
│  │  ├─ CommuteRecord.kt
│  │  ├─ Routine.kt
│  │  ├─ Destination.kt
│  │  ├─ TransportMode.kt
│  │  ├─ RepeatDay.kt
│  │  ├─ RouteEstimate.kt
│  │  ├─ RouteRealtimeSnapshot.kt
│  │  ├─ TransitArrivalEstimate.kt
│  │  └─ TransitArrivalQuery.kt
│  ├─ calculator
│  │  └─ DepartureTimeCalculator.kt
│  ├─ provider
│  │  ├─ PlaceSearchProvider.kt
│  │  ├─ ReverseGeocodingProvider.kt
│  │  ├─ RouteEstimateProvider.kt
│  │  └─ TransitArrivalProvider.kt
│  └─ repository
│     ├─ CommuteRecordRepository.kt
│     ├─ RouteRealtimeSnapshotRepository.kt
│     ├─ RoutineRepository.kt
│     └─ SettingsRepository.kt
└─ data
   ├─ alarm
   │  ├─ AndroidDepartureAlarmScheduler.kt
   │  ├─ AndroidDepartureRecheckScheduler.kt
   │  ├─ DepartureAlarmBootReceiver.kt
   │  ├─ DepartureAlarmCoordinator.kt
   │  ├─ DepartureAlarmNotificationPublisher.kt
   │  ├─ DepartureAlarmReceiver.kt
   │  └─ DepartureRecheckWorker.kt
   ├─ local
   │  ├─ CommuteRecordDao.kt
   │  ├─ CommuteRecordEntity.kt
   │  ├─ CommuteRecordMapper.kt
   │  ├─ MapMateDatabase.kt
   │  ├─ RouteRealtimeSnapshotDao.kt
   │  ├─ RouteRealtimeSnapshotEntity.kt
   │  ├─ RouteRealtimeSnapshotMapper.kt
   │  ├─ RoutineDao.kt
   │  ├─ RoutineEntity.kt
   │  └─ RoutineMapper.kt
   ├─ mock
   │  ├─ MockPlaceSearchProvider.kt
   │  └─ MockRouteEstimateProvider.kt
   ├─ location
   │  └─ AndroidCurrentLocationProvider.kt
   ├─ preferences
   │  └─ DataStoreSettingsRepository.kt
   ├─ remote
   │  ├─ api
   │  ├─ config
   │  ├─ dto
   │  └─ provider
   └─ repository
      ├─ RoomCommuteRecordRepository.kt
      ├─ RoomRouteRealtimeSnapshotRepository.kt
      └─ RoomRoutineRepository.kt
```

## `presentation/common`

여러 화면에서 재사용하는 UI와 표시 문자열 변환을 담당합니다.

- `RoutineSummaryCard`: 저장된 루틴 요약 카드 표시
- `RoutineDisplayLabels`: 요일과 이동 수단의 한국어 표시 문자열 제공
- `MapMateScaffold`: 앱 공통 Scaffold와 하단 내비게이션 제공
- `MapMateCards`: 화면 헤더, 섹션 카드, 히어로 카드, 계산/타임라인/루틴 카드, 경로 fallback 상태 메시지 등 공통 Compose UI 제공
- `MapMateSelectors`: 요일과 이동수단 선택 UI 제공
- `MapMateIcon`: 로컬 vector drawable 기반 아이콘 래퍼 제공
- `MapMateDesign`: 화면 여백, 카드 radius, elevation 등 presentation 전용 디자인 토큰 제공
- `RoutineRecommendationUiModel`: 여러 화면에서 권장 출발 시각을 표시하기 위한 UI 모델 변환 제공

## `presentation/home`

홈 화면의 UI와 상태 관리를 담당합니다.

- `HomeScreen`: 오늘 권장 출발 시각, 출발까지 남은 시간, 추천 근거, 지표 카드, 오늘의 루틴 요약 표시
- `HomeViewModel`: `RoutineRepository.observeRoutines()`를 관찰하고 홈 대시보드 추천 UI 모델을 생성
- `HomeUiState`: 홈 화면에 필요한 저장 루틴 목록, 대시보드 추천, 상태값

## `presentation/routine`

- `RoutinesScreen`: 저장된 루틴 목록, 활성/비활성 탭 UI, 수정/삭제/상세 예측 액션 표시
- `RoutinesViewModel`: `RoutineRepository.observeRoutines()`를 관찰하고 루틴 목록 추천 UI 모델과 삭제 상태를 관리
- `RoutinesUiState`: 루틴 목록 화면 상태

루틴 등록 화면의 UI와 상태 관리를 담당합니다.

- `RoutineRegistrationScreen`: 기본 정보, 장소, 도착 목표, 반복, 이동수단, 보정 단계형 입력 UI 표시
- `RoutineRegistrationComponents`: 목적지 후보, 반복 요일, 이동 수단, 메시지 등 재사용 UI component
- `RoutineRegistrationViewModel`: 사용자 입력 처리, 검증, 수정 대상 루틴 로딩, DataStore 설정 저장, mock provider 호출, 권장 출발 시각 계산 요청
- `RoutineRegistrationUiState`: 화면에 필요한 모든 상태
- `RoutineRegistrationEvent`: 화면에서 ViewModel로 전달되는 사용자 액션

Composable은 화면 표시와 callback 전달만 담당하고, 계산이나 provider 호출은 직접 하지 않습니다.

## `presentation/prediction`

상세 예측 화면의 UI와 상태 관리를 담당합니다.

- `PredictionDetailScreen`: 권장 출발 시각, 계산 근거, 경로 요약, 이동 기록 시작/루틴 수정 액션 표시
- `PredictionDetailViewModel`: 선택된 루틴 기준으로 `RouteEstimateProvider`를 호출하고 권장 출발 시각 UI 모델을 생성
- `PredictionDetailUiState`: 상세 예측 화면 상태

## `presentation/tracking`

이동 기록 흐름과 저장 완료 상태를 담당합니다.

- `TrackingScreen`: 출발 예정, 탑승, 도착 단계와 현재 이동 정보 표시
- `TrackingCompletionScreen`: 저장된 `CommuteRecord` 기반 기록 완료 화면 표시
- `TrackingViewModel`: 단계 상태, 경로 요약 로드, 도착 완료 시 `CommuteRecordRepository.saveRecord()` 호출 및 `SettingsRepository`를 통한 개인 보정값 자동 조정
- `TrackingUiState`: 이동 기록 화면 상태와 저장된 완료 기록

현재 `TrackingScreen`은 도착 완료 시 실제 `CommuteRecord`를 저장합니다. 기록 저장이 성공하면 목표 도착 시각 대비 실제 도착 오차를 DataStore 개인 보정값에 반영합니다. 자동 조정 폭은 한 번에 최대 ±5분으로 제한합니다.

## `presentation/history`

기록 탭 UI를 담당합니다.

- `RecordsScreen`: 저장된 이동 기록 목록과 empty state 표시
- `RecordsViewModel`: `CommuteRecordRepository.observeRecords()`를 관찰하고 기록 목록 상태 구성
- `RecordsUiState`: 기록 탭 화면 상태

## `presentation/settings`

설정 화면의 UI와 상태 관리를 담당합니다.

- `SettingsScreen`: 보정값, 기본 이동수단, 알림 설정값 표시와 입력 처리, Android 13 이상 알림 권한 요청
- `SettingsViewModel`: DataStore 설정 관찰과 저장 요청 처리
- `SettingsUiState`: 설정 화면 상태
- `SettingsEvent`: 화면에서 ViewModel로 전달되는 설정 변경 액션

## `domain/model`

앱의 핵심 데이터를 표현합니다.

- `AppSettings`: DataStore에 저장하는 개인 보정 시간, 안전 여유 시간, 알림 설정값, 기본 이동수단과 도착 오차 기반 자동 보정 계산
- `CommuteRecord`: 완료한 이동의 루틴명, 출발/도착 정보, 추천 출발 시각, 실제 도착 시각, 도착 오차
- `Routine`: 루틴 이름, 목적지, 목표 도착 시각, 반복 요일, 이동 수단, 보정 시간
- `Destination`: 장소 이름, 주소, 위도, 경도
- `TransportMode`: `TRANSIT`, `WALK`, `CAR`
- `RepeatDay`: `MONDAY`부터 `SUNDAY`
- `RouteEstimate`: 예상 이동 시간, 요약, provider 이름, 계산 사유, fallback 여부, 사용자용 상태 메시지
- `RouteRealtimeSnapshot`: 특정 경로/첫 탑승 구간의 실시간 보정 성공값과 만료 시각
- `TransitArrivalQuery`: ODsay 첫 탑승 구간에서 추출한 버스/지하철 실시간 도착정보 조회 입력값
- `TransitArrivalEstimate`: 실시간 도착정보 provider가 반환하는 첫 대기 시간, 요약, provider 이름, 계산 사유

## `domain/alarm`

출발 알림 예약에 필요한 순수 계산과 scheduler 추상화를 담당합니다.

- `DepartureAlarmPlanner`: 루틴 반복 요일, 목표 도착 시각, 경로 예상 시간, 보정 시간을 기준으로 다음 출발 알림 시각 계산
- `DepartureAlarmSchedule`: 예약할 루틴, 목적지, 권장 출발 시각, 알림 trigger epoch millis를 담는 모델
- `DepartureAlarmScheduler`: Android `AlarmManager` 구현체를 domain 밖으로 숨기기 위한 interface
- `DepartureRecheckScheduler`: Android `WorkManager` 구현체를 domain 밖으로 숨기기 위한 interface

## `domain/calculator`

순수 계산 로직을 담당합니다.

- `DepartureTimeCalculator`: 목표 도착 시각에서 예상 이동 시간, 개인 보정 시간, 안전 여유 시간을 빼서 권장 출발 시각을 계산하고, 오늘 권장 출발 시각이 이미 지났지만 목표 도착 시각이 아직 남은 경우 `지금 출발` 상태로 보정

계산 공식은 다음과 같습니다.

```text
recommended departure time
= target arrival time
- estimated travel time
- personal buffer time
- safety margin time
```

## `domain/provider`

외부 데이터 공급자를 추상화하는 interface입니다.

- `PlaceSearchProvider`: 목적지 검색 후보를 반환
- `RouteEstimateProvider`: 목적지와 이동 수단을 기준으로 예상 이동 시간을 반환
- `TransitArrivalProvider`: 첫 탑승 버스/지하철 구간의 실시간 도착 예정 시간을 반환

실제 API 구현체는 나중에 이 interface를 구현해서 교체합니다.

## `domain/repository`

루틴 저장소, 이동 기록 저장소, 앱 설정 저장소를 추상화합니다.

- `CommuteRecordRepository`: 이동 기록 저장과 저장된 기록 관찰 동작을 정의
- `RouteRealtimeSnapshotRepository`: 실시간 보정 성공값 저장, fresh snapshot 조회, 만료 snapshot 정리를 정의
- `RoutineRepository`: 루틴 저장, 삭제, 저장된 루틴 관찰 동작을 정의
- `SettingsRepository`: 앱 설정 관찰, 보정값/알림 설정값/기본 이동수단 저장, 도착 오차 기반 개인 보정 자동 업데이트 동작을 정의

ViewModel은 Room DAO나 DataStore를 직접 참조하지 않고 repository interface에 의존합니다.

## `data/local`

Room 기반 로컬 저장 구조입니다.

- `MapMateDatabase`: Room database singleton
- `CommuteRecordDao`: `commute_records` 테이블 insert/observe DAO
- `CommuteRecordEntity`: Room 저장용 이동 기록 entity
- `CommuteRecordMapper`: domain `CommuteRecord`와 Room `CommuteRecordEntity` 사이 변환
- `RouteRealtimeSnapshotDao`: `route_realtime_snapshots` 테이블 insert/query/delete DAO
- `RouteRealtimeSnapshotEntity`: Room 저장용 실시간 보정 snapshot entity
- `RouteRealtimeSnapshotMapper`: domain `RouteRealtimeSnapshot`과 Room `RouteRealtimeSnapshotEntity` 사이 변환
- `RoutineDao`: `routines` 테이블 insert/query/observe DAO
- `RoutineEntity`: Room 저장용 entity
- `RoutineMapper`: domain `Routine`과 Room `RoutineEntity` 사이 변환

현재 저장 형식은 다음과 같습니다.

- `LocalTime`: `HH:mm` 문자열
- `Set<RepeatDay>`: enum name을 쉼표로 연결한 문자열
- `TransportMode`: enum name 문자열
- `Destination`: 이름, 주소, 위도, 경도를 entity 컬럼으로 분리
- `CommuteRecord`: 루틴명, 출발지/목적지 이름, 이동 수단, 추천 출발 시각, 도착 시각, 도착 오차를 entity 컬럼으로 분리

## `data/repository`

domain repository interface의 Room 구현체입니다.

- `RoomRoutineRepository`: `RoutineDao`를 통해 루틴을 Room DB에 저장하고 `Flow<List<Routine>>`으로 관찰
- `RoomCommuteRecordRepository`: `CommuteRecordDao`를 통해 이동 기록을 Room DB에 저장하고 `Flow<List<CommuteRecord>>`로 관찰
- `RoomRouteRealtimeSnapshotRepository`: 실시간 보정 성공값을 Room DB에 저장하고 20분 이내 fresh snapshot fallback 조회에 사용

## `data/preferences`

Preferences DataStore 기반 설정 저장 구조입니다.

- `DataStoreSettingsRepository`: 개인 보정 시간, 안전 여유 시간, 알림 설정값, 기본 이동수단을 저장하고 `Flow<AppSettings>`로 관찰하며, 이동 기록 도착 오차를 개인 보정값에 반영

## `data/alarm`

Android `AlarmManager`, `BroadcastReceiver`, `NotificationManager` 기반 출발 알림을 담당합니다.

- `DepartureAlarmCoordinator`: 알림 설정과 저장된 루틴을 읽어 다음 출발 알림을 예약/취소
- `AndroidDepartureAlarmScheduler`: `AlarmManager`로 가장 가까운 출발 알림 하나를 예약하고 기존 예약을 취소
- `AndroidDepartureRecheckScheduler`: 출발 30분 전 unique WorkManager one-time work를 예약하고 기존 재조회 작업을 취소
- `DepartureAlarmReceiver`: 예약된 알림을 수신해 notification을 표시하고 다음 반복 알림을 재예약
- `DepartureAlarmBootReceiver`: 기기 부팅 또는 앱 업데이트 후 알림을 재예약
- `DepartureAlarmNotificationPublisher`: notification channel 생성과 출발 알림 표시
- `DepartureRecheckWorker`: 현재 루틴/설정과 경로 provider를 다시 사용해 다음 출발 알림과 다음 재조회 작업을 갱신

## `data/remote`

Retrofit 기반 외부 API 구현체와 DTO를 담당합니다.

- `KakaoPlaceSearchProvider`: Kakao Local API 키워드 장소 검색 결과를 `Destination` 후보로 변환
- `KakaoReverseGeocodingProvider`: 현재 위치 좌표를 Kakao Local API 좌표→주소 응답으로 변환
- `OdsayRouteEstimateProvider`: ODsay 대중교통 경로 결과를 기본 예상 이동 시간으로 사용하고, 첫 탑승 구간을 `TransitArrivalProvider`에 전달해 실시간 대기 지연을 보정하며 성공값은 `RouteRealtimeSnapshotRepository`에 저장
- `CompositeTransitArrivalProvider`: 버스/지하철 실시간 도착정보 provider를 순서대로 시도하고 실패하면 `null`을 반환
- `SeoulBusRealtimeArrivalProvider`: 서울특별시 버스도착정보조회 서비스 XML 응답을 파싱해 첫 버스 대기 시간을 계산
- `SeoulSubwayRealtimeArrivalProvider`: 서울 지하철 실시간 도착정보 JSON 응답에서 가장 빠른 첫 지하철 대기 시간을 계산
- `network_security_config.xml`: 서울 공공 API의 HTTP endpoint에 한해 cleartext 통신을 허용

## `di`

앱 수준 의존성 생성을 담당합니다.

- `AppContainer`: `RoomRoutineRepository`, `RoomCommuteRecordRepository`, `RoomRouteRealtimeSnapshotRepository`, `DataStoreSettingsRepository`, `DepartureAlarmCoordinator`, 외부 API provider를 생성하고 `MainActivity`에 제공합니다.

## `data/mock`

현재 앱에서 사용하는 mock 구현체입니다.

- `MockPlaceSearchProvider`: 숭실대학교, 강남역, 서울역, 홍대입구역 후보 반환
- `MockRouteEstimateProvider`: 이동 수단별 고정 예상 시간 반환
  - `TRANSIT`: 42분
  - `WALK`: 25분
  - `CAR`: 30분

## 현재 데이터 흐름

```text
User input
→ MapMateApp
→ HomeScreen / RoutinesScreen / RoutineRegistrationScreen / PredictionDetailScreen / TrackingScreen / SettingsScreen
→ HomeViewModel / RoutinesViewModel / RoutineRegistrationViewModel / PredictionDetailViewModel / TrackingViewModel / SettingsViewModel
→ PlaceSearchProvider / CurrentLocationProvider / ReverseGeocodingProvider / RouteEstimateProvider / TransitArrivalProvider
→ DepartureTimeCalculator
→ RoutineRepository
→ RoutineDao
→ RouteRealtimeSnapshotRepository
→ RouteRealtimeSnapshotDao
→ CommuteRecordRepository
→ CommuteRecordDao
→ Room Flow
→ SettingsRepository / DataStore
→ HomeUiState / RoutinesUiState / RoutineRegistrationUiState / PredictionDetailUiState / TrackingUiState / RecordsUiState / SettingsUiState
→ UI update
```

상세 흐름은 다음과 같습니다.

1. 사용자가 루틴 이름, 목적지, 도착 시각, 요일, 이동 수단, 보정 시간을 입력합니다.
2. `RoutineRegistrationScreen`은 입력 이벤트를 `RoutineRegistrationEvent`로 ViewModel에 전달합니다.
3. `RoutineRegistrationViewModel`은 상태를 갱신하고 입력값을 검증합니다.
4. 개인 보정 시간과 안전 여유 시간, 기본 이동수단, 알림 설정값이 변경되면 `SettingsRepository`를 통해 DataStore에 저장합니다.
5. 출발지/목적지 후보는 `PlaceSearchProvider`를 통해 조회합니다.
6. 현재 위치 출발지는 `CurrentLocationProvider`가 좌표를 가져오고, Kakao 키가 있으면 `ReverseGeocodingProvider`로 주소 변환을 시도합니다.
7. 예상 이동 시간은 `RouteEstimateProvider`를 통해 조회합니다. 대중교통 ODsay 경로에서 첫 탑승 구간을 추출할 수 있으면 `TransitArrivalProvider`로 실시간 도착정보를 조회해 지연분을 보정하고, 성공값은 `RouteRealtimeSnapshotRepository`에 저장합니다. 실시간 조회/매칭이 실패하면 20분 이내 fresh snapshot이 있을 때만 마지막 성공 보정값을 재사용합니다.
8. 권장 출발 시각은 `DepartureTimeCalculator`로 계산합니다. 계산 결과가 이미 지난 시간이면서 목표 도착 시각이 아직 남아 있으면 UI 모델은 `지금 출발`로 표시하고, 알림 플래너는 즉시 알림을 예약합니다.
9. 저장 버튼을 누르면 `RoutineRepository`를 통해 Room DB에 루틴을 저장합니다.
10. 홈에서 수정 버튼을 누르면 해당 루틴이 `RoutineRegistrationUiState`에 채워지고 같은 id로 다시 저장됩니다.
11. 홈에서 삭제 버튼을 누르면 `RoutineRepository.deleteRoutine()`을 통해 Room DB에서 제거합니다.
12. 저장된 루틴 목록은 Room `Flow`를 통해 `HomeUiState.savedRoutines`와 `RoutinesUiState.recommendations`에 반영됩니다.
13. DataStore 설정은 `Flow<AppSettings>`를 통해 루틴 등록 화면과 설정 화면의 기본값에 반영됩니다.
14. 상세 예측 화면과 이동 기록 화면은 선택된 루틴을 기준으로 `RouteEstimateProvider`를 호출해 권장 출발 시각 UI 모델을 구성합니다.
15. 이동 기록 화면에서 도착을 완료하면 `CommuteRecordRepository.saveRecord()`를 통해 Room DB에 기록을 저장합니다.
16. 기록 저장이 성공하면 `SettingsRepository.updatePersonalBufferForArrivalDelta()`가 도착 오차를 개인 보정값에 반영합니다.
17. 이동 기록 완료 화면은 저장된 `CommuteRecord`의 실제 도착 시각과 목표 대비 오차를 표시합니다.
18. 기록 탭은 `CommuteRecordRepository.observeRecords()`를 관찰해 저장된 기록 목록을 최신순으로 표시합니다.
19. 앱 실행 중 `DepartureAlarmCoordinator`는 알림 설정과 저장된 루틴 목록을 관찰해 다음 출발 알림을 예약하거나 취소합니다.
20. 다음 출발 알림이 30분보다 더 남아 있으면 `AndroidDepartureRecheckScheduler`가 WorkManager one-time work를 예약합니다.
21. `DepartureRecheckWorker`는 출발 전 경로 예상 시간을 다시 조회하고 다음 출발 알림과 재조회 작업을 갱신합니다.
22. 예약된 알림이 울리면 `DepartureAlarmReceiver`가 notification을 표시하고 다음 반복 알림을 다시 예약합니다.
23. 결과는 각 화면의 UiState에 반영되고 UI가 다시 그려집니다.

## 최근 확장 구조

기록 탭은 `RecordsViewModel`이 `CommuteRecordRepository.observeRecords()`를 관찰하면서 `RecordsStats`를 함께 계산합니다. `RecordsScreen`은 저장된 이동 기록 목록 위에 전체 기록 수, 평균 도착 오차, 정시/빠른 도착률, 늦은 도착 수, 자주 쓴 이동수단, 최근 5회 평균 오차를 표시합니다. 이 통계는 현재 표시용이며, 개인 보정 자동 업데이트 정책에는 아직 직접 연결하지 않습니다.

전체 경로 예상값 cache는 `RouteEstimateCacheRepository`와 Room `route_estimate_cache` 테이블이 담당합니다. `CachingRouteEstimateProvider`는 ODsay/Google 실제 provider가 성공하면 6시간 TTL로 예상값을 저장하고, 이후 해당 provider가 실패하면 mock fallback 전에 fresh cache를 반환합니다. 실시간 첫 탑승 보정값은 기존처럼 `RouteRealtimeSnapshot`에 별도로 저장됩니다.

대중교통 실시간 보정은 `CompositeTransitArrivalProvider`가 서울 버스 도착정보, TAGO 버스 도착정보, 서울 지하철 실시간 도착정보를 순서대로 시도합니다. TAGO provider는 ODsay 첫 버스 탑승 정류장 좌표가 있을 때 근접 정류소를 조회하고, 정류장명/거리/노선번호 매칭으로 `arrtime`을 선택합니다. 좌표, 키, 매칭이 없으면 null을 반환해 기존 provider/fallback 흐름을 유지합니다.

운행 상태 보조 설명은 `TransitOperationStatusProvider` 계층이 담당합니다. `SeoulBusOperationStatusProvider`는 서울 버스 위치정보에서 운행 중 차량 수와 정류장 접근 차량 수를 요약하고, `SeoulSubwayOperationStatusProvider`는 서울 지하철 실시간 열차 위치정보에서 호선 운행 상태와 현재 역 주변 열차 수를 요약합니다. 이 값은 권장 출발 시각 계산값을 직접 대체하지 않고 ODsay reason에 보조 설명으로 붙습니다.

Google Routes 자동차 모드는 `GoogleRoutesEstimateProvider`에서 `routingPreference = TRAFFIC_AWARE`를 보냅니다. Google Routes 문서상 이 값은 `DRIVE` 계열에서 사용하는 옵션이므로 도보/대중교통 요청에는 넣지 않습니다.

## 설계 원칙

- Composable은 UI 표시만 담당합니다.
- 상태 변경은 ViewModel을 통해 처리합니다.
- 계산 로직은 UI 밖의 `DepartureTimeCalculator`에 둡니다.
- 외부 API는 provider interface 뒤에 숨깁니다.
- 로컬 저장은 repository interface 뒤에 숨깁니다.
- 앱 수준 의존성 생성은 `AppContainer`에 모읍니다.
- 실제 API provider는 fallback provider 뒤에 두고, 키 누락/호출 실패 시 mock provider 결과로 복구합니다. 경로 fallback은 UI 모델로 전달되어 주요 화면에 상태 메시지로 표시됩니다.
- domain 계층에는 Android, Compose, Room, Retrofit 의존성을 넣지 않습니다.

## 앞으로 확장할 영역

- 통계/분석 화면
- 필요 시 Navigation Compose 도입
- 모바일 보안 점검: 민감 정보 저장/로그 노출, 위치/이동 기록 개인정보 취급, cleartext 통신 범위, release 빌드 보안 설정 확인
