# MapMate Architecture

이 문서는 현재 MapMate 프로젝트의 실제 구현 구조를 설명합니다. 새 기능을 추가할 때는 이 문서를 기준으로 어느 패키지에 코드를 둘지 판단합니다.

현재 앱은 홈 화면, 루틴 등록 화면, 설정 화면, 저장된 루틴 목록 표시, mock provider 기반 권장 출발 시각 계산, Room 기반 루틴 저장, DataStore 기반 앱 설정 저장 흐름까지 구현되어 있습니다. Retrofit, 실제 알림 예약, Navigation Compose는 아직 구현되어 있지 않습니다.

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
│  │  ├─ RoutineDisplayLabels.kt
│  │  └─ RoutineSummaryCard.kt
│  ├─ home
│  │  ├─ HomeScreen.kt
│  │  ├─ HomeViewModel.kt
│  │  └─ HomeUiState.kt
│  ├─ routine
│  │  ├─ RoutineRegistrationScreen.kt
│  │  ├─ RoutineRegistrationComponents.kt
│  │  ├─ RoutineRegistrationViewModel.kt
│  │  ├─ RoutineRegistrationUiState.kt
│  │  └─ RoutineRegistrationEvent.kt
│  └─ settings
│     ├─ SettingsScreen.kt
│     ├─ SettingsViewModel.kt
│     ├─ SettingsUiState.kt
│     └─ SettingsEvent.kt
├─ domain
│  ├─ model
│  │  ├─ AppSettings.kt
│  │  ├─ Routine.kt
│  │  ├─ Destination.kt
│  │  ├─ TransportMode.kt
│  │  ├─ RepeatDay.kt
│  │  └─ RouteEstimate.kt
│  ├─ calculator
│  │  └─ DepartureTimeCalculator.kt
│  ├─ provider
│  │  ├─ PlaceSearchProvider.kt
│  │  └─ RouteEstimateProvider.kt
│  └─ repository
│     ├─ RoutineRepository.kt
│     └─ SettingsRepository.kt
└─ data
   ├─ local
   │  ├─ MapMateDatabase.kt
   │  ├─ RoutineDao.kt
   │  ├─ RoutineEntity.kt
   │  └─ RoutineMapper.kt
   ├─ mock
   │  ├─ MockPlaceSearchProvider.kt
   │  └─ MockRouteEstimateProvider.kt
   ├─ preferences
   │  └─ DataStoreSettingsRepository.kt
   └─ repository
      └─ RoomRoutineRepository.kt
```

## `presentation/common`

여러 화면에서 재사용하는 UI와 표시 문자열 변환을 담당합니다.

- `RoutineSummaryCard`: 저장된 루틴 요약 카드 표시
- `RoutineDisplayLabels`: 요일과 이동 수단의 한국어 표시 문자열 제공

## `presentation/home`

홈 화면의 UI와 상태 관리를 담당합니다.

- `HomeScreen`: 저장된 루틴 목록, 로딩/오류/빈 상태, 루틴 등록/수정/삭제 액션 표시
- `HomeViewModel`: `RoutineRepository.observeRoutines()`를 관찰하고 루틴 삭제 요청을 처리해 홈 화면 상태 갱신
- `HomeUiState`: 홈 화면에 필요한 저장 루틴 목록과 상태값

## `presentation/routine`

루틴 등록 화면의 UI와 상태 관리를 담당합니다.

- `RoutineRegistrationScreen`: 입력 필드, 결과 카드, 저장 영역 표시
- `RoutineRegistrationComponents`: 목적지 후보, 반복 요일, 이동 수단, 메시지 등 재사용 UI component
- `RoutineRegistrationViewModel`: 사용자 입력 처리, 검증, 수정 대상 루틴 로딩, DataStore 설정 저장, mock provider 호출, 권장 출발 시각 계산 요청
- `RoutineRegistrationUiState`: 화면에 필요한 모든 상태
- `RoutineRegistrationEvent`: 화면에서 ViewModel로 전달되는 사용자 액션

Composable은 화면 표시와 callback 전달만 담당하고, 계산이나 provider 호출은 직접 하지 않습니다.

## `presentation/settings`

설정 화면의 UI와 상태 관리를 담당합니다.

- `SettingsScreen`: 보정값, 기본 이동수단, 알림 설정값 표시와 입력 처리
- `SettingsViewModel`: DataStore 설정 관찰과 저장 요청 처리
- `SettingsUiState`: 설정 화면 상태
- `SettingsEvent`: 화면에서 ViewModel로 전달되는 설정 변경 액션

## `domain/model`

앱의 핵심 데이터를 표현합니다.

- `AppSettings`: DataStore에 저장하는 개인 보정 시간, 안전 여유 시간, 알림 설정값, 기본 이동수단
- `Routine`: 루틴 이름, 목적지, 목표 도착 시각, 반복 요일, 이동 수단, 보정 시간
- `Destination`: 장소 이름, 주소, 위도, 경도
- `TransportMode`: `TRANSIT`, `WALK`, `CAR`
- `RepeatDay`: `MONDAY`부터 `SUNDAY`
- `RouteEstimate`: 예상 이동 시간, 요약, provider 이름, 계산 사유

## `domain/calculator`

순수 계산 로직을 담당합니다.

- `DepartureTimeCalculator`: 목표 도착 시각에서 예상 이동 시간, 개인 보정 시간, 안전 여유 시간을 빼서 권장 출발 시각을 계산

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

실제 API 구현체는 나중에 이 interface를 구현해서 교체합니다.

## `domain/repository`

루틴 저장소와 앱 설정 저장소를 추상화합니다.

- `RoutineRepository`: 루틴 저장, 삭제, 저장된 루틴 관찰 동작을 정의
- `SettingsRepository`: 앱 설정 관찰과 보정값/알림 설정값/기본 이동수단 저장 동작을 정의

ViewModel은 Room DAO나 DataStore를 직접 참조하지 않고 repository interface에 의존합니다.

## `data/local`

Room 기반 로컬 저장 구조입니다.

- `MapMateDatabase`: Room database singleton
- `RoutineDao`: `routines` 테이블 insert/query/observe DAO
- `RoutineEntity`: Room 저장용 entity
- `RoutineMapper`: domain `Routine`과 Room `RoutineEntity` 사이 변환

현재 저장 형식은 다음과 같습니다.

- `LocalTime`: `HH:mm` 문자열
- `Set<RepeatDay>`: enum name을 쉼표로 연결한 문자열
- `TransportMode`: enum name 문자열
- `Destination`: 이름, 주소, 위도, 경도를 entity 컬럼으로 분리

## `data/repository`

domain repository interface의 Room 구현체입니다.

- `RoomRoutineRepository`: `RoutineDao`를 통해 루틴을 Room DB에 저장하고 `Flow<List<Routine>>`으로 관찰

## `data/preferences`

Preferences DataStore 기반 설정 저장 구조입니다.

- `DataStoreSettingsRepository`: 개인 보정 시간, 안전 여유 시간, 알림 설정값, 기본 이동수단을 저장하고 `Flow<AppSettings>`로 관찰

## `di`

앱 수준 의존성 생성을 담당합니다.

- `AppContainer`: `RoomRoutineRepository`와 `DataStoreSettingsRepository`를 생성하고 `MainActivity`에 제공합니다.

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
→ HomeScreen / RoutineRegistrationScreen / SettingsScreen
→ HomeViewModel / RoutineRegistrationViewModel / SettingsViewModel
→ PlaceSearchProvider / RouteEstimateProvider
→ DepartureTimeCalculator
→ RoutineRepository
→ RoutineDao
→ Room Flow
→ SettingsRepository / DataStore
→ RoutineRegistrationUiState
→ UI update
```

상세 흐름은 다음과 같습니다.

1. 사용자가 루틴 이름, 목적지, 도착 시각, 요일, 이동 수단, 보정 시간을 입력합니다.
2. `RoutineRegistrationScreen`은 입력 이벤트를 `RoutineRegistrationEvent`로 ViewModel에 전달합니다.
3. `RoutineRegistrationViewModel`은 상태를 갱신하고 입력값을 검증합니다.
4. 개인 보정 시간과 안전 여유 시간, 기본 이동수단, 알림 설정값이 변경되면 `SettingsRepository`를 통해 DataStore에 저장합니다.
5. 목적지 후보는 `PlaceSearchProvider`를 통해 조회합니다.
6. 예상 이동 시간은 `RouteEstimateProvider`를 통해 조회합니다.
7. 권장 출발 시각은 `DepartureTimeCalculator`로 계산합니다.
8. 저장 버튼을 누르면 `RoutineRepository`를 통해 Room DB에 루틴을 저장합니다.
9. 홈에서 수정 버튼을 누르면 해당 루틴이 `RoutineRegistrationUiState`에 채워지고 같은 id로 다시 저장됩니다.
10. 홈에서 삭제 버튼을 누르면 `RoutineRepository.deleteRoutine()`을 통해 Room DB에서 제거합니다.
11. 저장된 루틴 목록은 Room `Flow`를 통해 `HomeUiState.savedRoutines`에 반영됩니다.
12. DataStore 설정은 `Flow<AppSettings>`를 통해 루틴 등록 화면과 설정 화면의 기본값에 반영됩니다.
13. 결과는 `RoutineRegistrationUiState`에 반영되고 UI가 다시 그려집니다.

## 설계 원칙

- Composable은 UI 표시만 담당합니다.
- 상태 변경은 ViewModel을 통해 처리합니다.
- 계산 로직은 UI 밖의 `DepartureTimeCalculator`에 둡니다.
- 외부 API는 provider interface 뒤에 숨깁니다.
- 로컬 저장은 repository interface 뒤에 숨깁니다.
- 앱 수준 의존성 생성은 `AppContainer`에 모읍니다.
- 현재는 mock provider를 사용합니다.
- 실제 API 구현체는 나중에 provider interface 구현체로 교체할 수 있습니다.
- domain 계층에는 Android, Compose, Room, Retrofit 의존성을 넣지 않습니다.

## 앞으로 확장할 영역

- 실제 API provider
- 알림
- 이동 기록
- 개인 보정 로직
- Navigation Compose 기반 상세 화면 전환
- 통계 화면
- 설정 화면
