# MapMate Codex Development Standards

> 이 문서는 MapMate 앱을 Codex로 개발하기 전에 팀이 공유해야 할 개발 기준이다.  
> Codex에게 작업을 지시할 때는 이 문서를 기준 문서로 제공하고, 이 문서와 충돌하는 코드를 생성하지 않도록 한다.

---

## 0. 프로젝트 핵심 정의

### 0.1 앱의 목적

MapMate는 범용 지도 앱을 대체하는 앱이 아니다.  
핵심 목적은 **반복되는 출퇴근/등교 루틴에서 사용자가 언제 출발해야 하는지 추천하고 알림으로 알려주는 것**이다.

### 0.2 핵심 사용자 흐름

```text
루틴 등록
→ 지도/API 또는 Mock 데이터로 예상 이동 시간 계산
→ 권장 출발 시각 산출
→ 출발 전 교통 상황 재조회
→ 출발 알림 발송
→ 이동 중 탑승/도착 기록
→ 도착 오차 기반 개인 보정값 업데이트
→ 다음 추천에 반영
```

### 0.3 핵심 구현 목표

이번 프로젝트의 핵심 구현 목표는 다음 흐름을 끝까지 연결하는 것이다.

```text
루틴 등록 → 권장 출발 알림 → 실제 기록 저장 → 다음 추천 보정
```

기능을 많이 추가하는 것보다 위 흐름이 안정적으로 동작하는 것을 우선한다.

---

## 1. MVP 범위 기준

### 1.1 반드시 구현할 기능

| 우선순위 | 기능 | 설명 |
|---|---|---|
| 필수 | 루틴 등록/수정/삭제 | 도착 위치, 도착 시각, 반복 요일, 이동 수단, 개인 보정 시간 저장 |
| 필수 | 권장 출발 시각 계산 | 지도 API 예상 이동 시간 + 개인 보정 시간 + 안전 여유 시간으로 계산 |
| 필수 | 홈 화면 | 오늘의 루틴과 권장 출발 시각 표시 |
| 필수 | 상세 예측 화면 | 예상 이동 시간, 경로 요약, 변동 사유 표시 |
| 필수 | 출발 알림 | 권장 출발 시각에 알림 발송 |
| 필수 | 출발 전 재조회 | WorkManager로 출발 전 예상 이동 시간 재확인 |
| 필수 | 이동 중 기록 | 버스 탑승 여부, 목적지 도착 여부를 버튼으로 기록 |
| 필수 | 개인화 보정 | 도착 오차를 바탕으로 다음 추천에 개인 보정값 반영 |
| 권장 | 기록/통계 화면 | 최근 도착 기록과 도착 오차 표시 |
| 권장 | 설정 화면 | 알림 설정, 개인 보정 시간, 데이터 관리 |

### 1.2 MVP에서 제외할 기능

다음 기능은 Codex가 임의로 구현하지 않는다.

| 제외 기능 | 제외 이유 |
|---|---|
| 범용 지도 앱 수준의 경로 탐색 | 프로젝트 범위 초과 |
| 복잡한 지도 시각화 | 핵심 가치와 직접 관련 낮음 |
| 실시간 위치 추적 기반 자동 기록 | 권한, 배터리, 정확도 이슈로 과제 안정성 저하 |
| 실시간 대중교통 혼잡도 예측 | API 제약과 검증 어려움 |
| 완전 자동 개인화 알고리즘 | MVP에서는 설명 가능한 단순 보정이 적합 |
| LLM 기반 전체 루틴 자동 생성 | 확장 기능으로만 고려 |
| 다중 사용자/서버 동기화 | 로컬 앱 MVP 범위 밖 |

### 1.3 확장 기능 기준

자연어 입력 또는 LLM 기능은 다음 범위로만 제한한다.

- 루틴 문장 해석 보조
- 추천 사유 설명 문장 생성
- 사용자의 이동 패턴을 자연어로 요약

LLM이 추천 시각 계산의 핵심 로직을 대체해서는 안 된다.

---

## 2. 기술 스택 기준

MapMate 프로젝트에서는 다음 기술 스택을 기준으로 한다.

| 영역 | 기준 기술 |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Navigation | Navigation Compose |
| State Management | ViewModel + StateFlow |
| Local DB | Room |
| Settings Storage | DataStore |
| Network | Retrofit2 |
| JSON Parsing | 프로젝트에서 하나만 선택. 기본값은 Kotlinx Serialization 권장 |
| Async | Kotlin Coroutines + Flow |
| Notification | AlarmManager |
| Background Work | WorkManager |
| Test | JUnit, Kotlin Coroutines Test, 필요 시 Room in-memory DB |

### 2.1 기술 스택 운영 원칙

- Compose와 XML View를 혼용하지 않는다.
- Room과 DataStore의 역할을 분리한다.
  - Room: 루틴, 이동 기록처럼 구조화된 데이터 저장
  - DataStore: 알림 설정, 전역 기본 보정값, 앱 설정 저장
- Retrofit 응답 모델은 DTO로 받고, Domain Model로 변환해서 사용한다.
- 비동기 처리는 Coroutine과 Flow를 기준으로 한다.
- UI 상태는 StateFlow로 노출한다.
- API 키, 비밀값, 개인 토큰은 Git에 커밋하지 않는다.

---

## 3. 아키텍처 기준

### 3.1 기본 구조

MapMate는 다음 계층 구조를 따른다.

```text
presentation → domain → data
```

| 계층 | 역할 |
|---|---|
| presentation | Compose UI, ViewModel, UI State, UI Event |
| domain | 순수 비즈니스 로직, UseCase, Domain Model, 계산 로직 |
| data | Room, DataStore, Retrofit, Repository 구현체, DTO/Entity |

### 3.2 의존성 방향

```text
presentation depends on domain
data depends on domain
domain depends on nothing Android-specific
```

Domain 계층에는 Android Context, Room, Retrofit, Compose 관련 코드를 넣지 않는다.

### 3.3 기본 패키지 구조

```text
com.mapmate
 ├─ data
 │   ├─ local
 │   │   ├─ db
 │   │   ├─ dao
 │   │   └─ entity
 │   ├─ preferences
 │   ├─ remote
 │   │   ├─ api
 │   │   ├─ datasource
 │   │   └─ dto
 │   ├─ mapper
 │   └─ repository
 ├─ domain
 │   ├─ model
 │   ├─ repository
 │   ├─ usecase
 │   └─ calculator
 ├─ presentation
 │   ├─ home
 │   ├─ routine
 │   ├─ prediction
 │   ├─ tracking
 │   ├─ history
 │   ├─ settings
 │   └─ common
 ├─ notification
 ├─ worker
 ├─ navigation
 └─ di
```

### 3.4 계층별 금지 사항

| 위치 | 금지 사항 |
|---|---|
| Composable | Room DAO 직접 호출 금지 |
| Composable | Retrofit API 직접 호출 금지 |
| Composable | 복잡한 추천 계산 로직 작성 금지 |
| ViewModel | SQL, DTO 파싱, Retrofit 구현 세부사항 직접 처리 금지 |
| Repository | UI 문자열, Compose 상태, 화면 이동 처리 금지 |
| Domain | Android Context, Resource, Room Annotation, Retrofit Annotation 사용 금지 |

---

## 4. Coding Style Guide

### 4.1 Kotlin 기본 스타일

- Kotlin official style을 기준으로 한다.
- ktlint 또는 Android Studio formatter 기준으로 포맷을 통일한다.
- 한 파일에는 하나의 주요 클래스 또는 하나의 주요 역할만 둔다.
- 함수는 하나의 책임만 갖도록 작성한다.
- 불필요한 주석보다 명확한 이름을 우선한다.
- 예외 상황은 무시하지 말고 명시적으로 처리한다.

### 4.2 네이밍 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| Class | PascalCase | `RoutineViewModel`, `RouteRepositoryImpl` |
| Interface | PascalCase | `RouteRepository`, `RoutineRepository` |
| Function | camelCase | `calculateDepartureTime()` |
| Variable | camelCase | `targetArrivalTime` |
| Boolean | `is`, `has`, `should`, `can` 접두사 | `isArrived`, `hasBoardedBus` |
| Constant | UPPER_SNAKE_CASE | `DEFAULT_SAFETY_MARGIN_MINUTES` |
| Composable Screen | PascalCase + `Screen` | `HomeScreen` |
| Stateless UI Component | PascalCase + 역할명 | `DepartureTimeCard` |
| ViewModel Event Handler | `on` 접두사 | `onSaveClick()` |

### 4.3 Boolean 네이밍 기준

```kotlin
val isRoutineEnabled: Boolean
val hasBoardedBus: Boolean
val shouldNotifyBeforeDeparture: Boolean
val canUpdateRoutine: Boolean
```

다음과 같은 모호한 이름은 사용하지 않는다.

```kotlin
val check: Boolean
val flag: Boolean
val state: Boolean
```

### 4.4 시간 단위 네이밍

시간 단위가 포함된 값은 변수명에 단위를 반드시 표시한다.

```kotlin
val routeDurationMinutes: Int
val safetyMarginMinutes: Int
val personalAdjustmentMinutes: Int
val refreshBeforeDepartureMinutes: Int
```

`time`, `duration`, `value`처럼 단위가 불명확한 이름은 피한다.

---

## 5. Compose UI 기준

### 5.1 Route와 Screen 분리

화면은 가능하면 `Route`와 `Screen`으로 분리한다.

```kotlin
@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onNavigateToDetail: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onDetailClick = onNavigateToDetail,
        onRefreshClick = viewModel::refresh,
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onDetailClick: (Long) -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // UI only
}
```

### 5.2 Composable 원칙

- `Screen` Composable은 최대한 stateless하게 작성한다.
- 화면 상태는 `UiState` 하나로 전달한다.
- 사용자 액션은 callback으로 외부에 전달한다.
- Composable 내부에서 API, DB, UseCase를 직접 호출하지 않는다.
- 주요 화면은 `@Preview`를 작성한다.
- 재사용 가능한 UI는 `presentation.common`에 둔다.

### 5.3 Modifier 기준

- 외부에서 스타일을 조정할 수 있는 Composable은 `modifier: Modifier = Modifier`를 제공한다.
- 내부 컴포넌트에도 필요하면 modifier를 전달한다.
- 고정 padding, color, shape은 반복되면 디자인 상수 또는 공통 컴포넌트로 분리한다.

### 5.4 UI 문자열 기준

- 화면에 표시되는 주요 문구는 가능하면 `strings.xml`로 분리한다.
- 임시 개발용 문구는 TODO와 함께 표시한다.
- 시간 표기는 `08:12`, `09:00` 형태를 기본으로 한다.
- 도착 오차는 다음 형식을 기준으로 한다.

```text
예상보다 2분 늦게 도착했어요.
예상보다 3분 빨리 도착했어요.
정시에 도착했어요.
```

---

## 6. State Management 기준

### 6.1 ViewModel 기본 형태

```kotlin
class HomeViewModel(
    private val getTodayRoutineUseCase: GetTodayRoutineUseCase,
    private val calculateRecommendationUseCase: CalculateRecommendationUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            // load data and update state
        }
    }
}
```

### 6.2 UiState 기준

각 화면은 하나의 UiState를 가진다.

```kotlin
data class HomeUiState(
    val isLoading: Boolean = false,
    val todayRoutine: RoutineSummaryUiModel? = null,
    val recommendedDepartureTimeText: String = "",
    val errorMessage: String? = null,
)
```

### 6.3 Event 기준

복잡한 화면에서는 사용자 액션을 sealed interface로 정의한다.

```kotlin
sealed interface RoutineEditEvent {
    data class NameChanged(val name: String) : RoutineEditEvent
    data class ArrivalTimeChanged(val time: LocalTime) : RoutineEditEvent
    data object SaveClicked : RoutineEditEvent
}
```

### 6.4 일회성 이벤트 기준

Snackbar, Toast, Navigation 같은 일회성 이벤트는 UiState에 영구 저장하지 않는다.  
필요하면 `Channel` 또는 `SharedFlow`를 사용한다.

---

## 7. 데이터 모델 기준

### 7.1 Domain Model

```kotlin
data class Routine(
    val id: Long,
    val name: String,
    val destinationName: String,
    val destinationLatitude: Double?,
    val destinationLongitude: Double?,
    val targetArrivalTime: LocalTime,
    val repeatDays: Set<RepeatDay>,
    val transportMode: TransportMode,
    val personalAdjustmentMinutes: Int,
    val safetyMarginMinutes: Int,
    val isEnabled: Boolean,
)
```

```kotlin
data class CommuteRecord(
    val id: Long,
    val routineId: Long,
    val date: LocalDate,
    val recommendedDepartureTime: LocalTime,
    val expectedArrivalTime: LocalTime?,
    val actualDepartureTime: LocalTime?,
    val boardedAt: LocalTime?,
    val arrivedAt: LocalTime?,
    val arrivalOffsetMinutes: Int?,
)
```

### 7.2 Enum 기준

```kotlin
enum class TransportMode {
    WALK,
    BUS,
    SUBWAY,
    TRANSIT,
    CAR,
}
```

```kotlin
enum class RepeatDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY,
}
```

### 7.3 Room Entity 기준

Room Entity는 DB 저장에 적합한 타입을 사용한다.  
`LocalDate`, `LocalTime`, `Set<RepeatDay>`는 TypeConverter 또는 저장용 타입으로 변환한다.

권장 저장 방식:

| Domain Type | DB 저장 방식 |
|---|---|
| `LocalDate` | ISO-8601 String: `2026-05-30` |
| `LocalTime` | String: `09:00` 또는 minute-of-day Int |
| `Set<RepeatDay>` | bitmask Int 또는 comma-separated String |
| `TransportMode` | String enum name |

### 7.4 RoutineEntity 예시

```kotlin
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val destinationName: String,
    val destinationLatitude: Double?,
    val destinationLongitude: Double?,
    val targetArrivalTime: String,
    val repeatDaysMask: Int,
    val transportMode: String,
    val personalAdjustmentMinutes: Int,
    val safetyMarginMinutes: Int,
    val isEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
```

### 7.5 CommuteRecordEntity 예시

```kotlin
@Entity(
    tableName = "commute_records",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("routineId")],
)
data class CommuteRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val routineId: Long,
    val date: String,
    val recommendedDepartureTime: String,
    val expectedArrivalTime: String?,
    val actualDepartureTime: String?,
    val boardedAt: String?,
    val arrivedAt: String?,
    val arrivalOffsetMinutes: Int?,
    val createdAt: Long,
)
```

---

## 8. 추천 로직 기준

### 8.1 기본 산식

권장 출발 시각은 다음 공식으로 계산한다.

```text
권장 출발 시각
= 도착 목표 시각
- 지도/API 예상 이동 시간
- 개인 보정 시간
- 안전 여유 시간
```

예시:

```text
09:00 도착 목표
- 42분 예상 이동 시간
- 6분 개인 보정 시간
- 5분 안전 여유 시간
= 08:07 권장 출발
```

### 8.2 용어 정의

| 용어 | 의미 |
|---|---|
| `targetArrivalTime` | 사용자가 설정한 목표 도착 시각 |
| `routeDurationMinutes` | 지도 API 또는 Mock 응답으로 얻은 예상 이동 시간 |
| `personalAdjustmentMinutes` | 사용자의 실제 기록을 바탕으로 보정하는 시간 |
| `safetyMarginMinutes` | 항상 확보하는 기본 여유 시간 |
| `recommendedDepartureTime` | 최종 권장 출발 시각 |

### 8.3 기본값

| 값 | 기본 기준 |
|---|---|
| `safetyMarginMinutes` | 5분 |
| `personalAdjustmentMinutes` | 최초 0분 |
| `refreshBeforeDepartureMinutes` | 권장 출발 10분 전 |
| 보정 계산 대상 기록 | 최근 3개의 유효 도착 기록 |
| 개인 보정값 범위 | -10분 ~ +20분 |

### 8.4 Calculator 구현 기준

추천 계산은 Android 의존성이 없는 순수 Kotlin 클래스로 작성한다.

```kotlin
class DepartureTimeCalculator {
    fun calculate(
        targetArrivalTime: LocalTime,
        routeDurationMinutes: Int,
        personalAdjustmentMinutes: Int,
        safetyMarginMinutes: Int,
    ): LocalTime {
        return targetArrivalTime
            .minusMinutes(routeDurationMinutes.toLong())
            .minusMinutes(personalAdjustmentMinutes.toLong())
            .minusMinutes(safetyMarginMinutes.toLong())
    }
}
```

### 8.5 도착 오차 계산 기준

도착 오차는 다음 기준으로 계산한다.

```text
arrivalOffsetMinutes = actualArrivalTime - targetArrivalTime
```

| 값 | 의미 |
|---|---|
| 양수 | 목표보다 늦게 도착함. 다음에는 더 일찍 출발해야 함 |
| 0 | 정시 도착 |
| 음수 | 목표보다 빨리 도착함. 다음에는 조금 늦게 출발 가능 |

### 8.6 개인 보정값 업데이트 기준

초기 MVP에서는 설명 가능한 단순식을 사용한다.

```text
recentAverageOffset = 최근 3개 도착 오차 평균
newPersonalAdjustment = oldPersonalAdjustment * 0.7 + recentAverageOffset * 0.3
```

최종 값은 정수로 반올림하고 범위를 제한한다.

```text
newPersonalAdjustmentMinutes = round(newPersonalAdjustment).coerceIn(-10, 20)
```

### 8.7 추천 로직 금지 사항

- LLM이 추천 시각을 직접 산출하지 않는다.
- Gemini 같은 LLM으로 개인보정 시간을 추론하지 않는다. 개인보정은 실제 출발/도착 기록 기반 계산식으로 처리한다.
- ViewModel 또는 Composable에 계산 공식을 직접 작성하지 않는다.
- API 응답 실패 시 앱이 크래시 나면 안 된다.
- 기록이 없는 사용자에게 과도한 보정값을 적용하지 않는다.
- 보정값이 무한히 커지거나 작아지지 않도록 범위를 제한한다.

---

## 9. API / Mock 전략

### 9.1 기본 전략

개발 안정성을 위해 API 연동 전 Mock 기반으로 전체 앱 흐름을 먼저 완성한다.

```text
1단계: MockRouteDataSource로 예상 이동 시간 반환
2단계: 실제 지도/경로 API 연동
3단계: 실제 API 실패 시 Mock 또는 마지막 성공값으로 fallback
```

### 9.2 DataSource 인터페이스 기준

```kotlin
interface RouteDataSource {
    suspend fun getRouteEstimate(request: RouteEstimateRequest): RouteEstimate
}
```

```kotlin
data class RouteEstimate(
    val durationMinutes: Int,
    val summary: String,
    val reason: String?,
)
```

### 9.3 API Key 기준

- API Key는 Git에 커밋하지 않는다.
- `local.properties`, Gradle BuildConfig, 환경 변수 중 하나로 관리한다.
- 샘플 키는 `API_KEY_PLACEHOLDER` 같은 이름으로만 문서화한다.
- PR에 실제 키가 포함되면 merge하지 않는다.

### 9.4 API 실패 처리 기준

| 상황 | 처리 기준 |
|---|---|
| 네트워크 연결 실패 | 마지막 성공한 예상 시간 또는 Mock 값 사용 |
| API quota 초과 | 사용자에게 간단한 오류 안내 후 fallback 사용 |
| 목적지 좌표 없음 | 루틴 설정 화면에서 저장 불가 처리 |
| 응답 파싱 실패 | error log 기록 후 fallback 사용 |
| 출발 전 재조회 실패 | 기존 계산값으로 알림 발송 |

### 9.5 지도 API 사용 범위

지도 API는 다음 목적으로만 사용한다.

- 예상 이동 시간 조회
- 경로 요약 정보 조회
- 목적지 좌표 또는 장소 정보 보조

MVP에서는 지도 화면 전체 구현을 목표로 하지 않는다.

### 9.6 실시간 출발 시각 보정 기준

실시간 출발 시각 보정은 `docs/REALTIME_DEPARTURE_STRATEGY.md`를 기준으로 한다.

- ODsay는 기본 대중교통 경로와 기본 예상 이동 시간을 산출한다.
- TAGO 버스정류소정보와 버스도착정보는 첫 버스 탑승 대기시간 보정에 사용한다.
- ODsay 첫 탑승 정류장 좌표를 기준으로 TAGO 정류소 후보를 찾는다. 집 좌표나 출발지 좌표만으로 정류소를 매칭하지 않는다.
- 정류장 거리, 정류장명 유사도, 노선번호 정규화, 방향 정보를 기준으로 매칭 신뢰도를 계산한다.
- 매칭 신뢰도가 낮거나 API 조회가 실패하면 ODsay 기본 예상시간으로 fallback한다.
- `adjustedRouteDurationMinutes`는 `Routine`에 저장하지 않고 `RouteRealtimeSnapshot` 같은 시점별 스냅샷으로 저장한다.
- 마지막 성공 보정값은 유효기간 안에서만 사용하고, 오래된 값은 `STALE_SNAPSHOT_FALLBACK`으로 처리한다.
- 실시간 보정으로 재계산한 출발 시각이 현재 시각보다 빠르면 "지금 출발 권장"으로 clamp한다.
- 1차 구현은 첫 버스 탑승 구간 1개 보정으로 제한한다. 지하철 실시간 provider는 지역별 API 검토 후 후순위로 추가한다.

---

## 10. 알림 및 백그라운드 작업 기준

### 10.1 알림 흐름

```text
루틴 저장/수정
→ 권장 출발 시각 계산
→ 출발 전 재조회 Work 예약
→ 출발 알림 Alarm 예약
→ 알림 클릭 시 상세 예측 화면으로 이동
```

### 10.2 AlarmManager 기준

- 루틴이 저장되면 해당 루틴의 다음 알림을 예약한다.
- 루틴이 수정되면 기존 알림을 취소하고 다시 예약한다.
- 루틴이 삭제되거나 비활성화되면 예약된 알림을 취소한다.
- 권장 출발 시각이 이미 지난 경우 당일 알림을 예약하지 않는다.
- 반복 요일이 아닌 날에는 알림을 예약하지 않는다.
- WorkManager 재조회로 최종 출발 시각이 변경되면 기존 알림을 취소하고 재예약한다.
- Android 12 이상에서는 exact alarm 권한 또는 inexact alarm fallback을 고려한다.
- Android 13 이상에서는 notification 권한을 고려한다.

### 10.3 WorkManager 기준

- 권장 출발 시각 약 10분 전에 출발 전 재조회를 수행한다.
- 재조회 결과로 예상 이동 시간이 바뀌면 권장 출발 시각을 다시 계산한다.
- 재계산된 권장 출발 시각이 기존 알림보다 앞당겨지면 알림 갱신 여부를 판단한다.
- 재조회 실패 시 기존 계산값을 유지한다.
- WorkManager는 정확한 시각 실행용 타이머로 보지 않는다. 최종 출발 알림은 AlarmManager가 담당한다.
- 재조회 결과는 `RouteRealtimeSnapshot`으로 저장하고, 마지막 성공값을 사용할 때는 유효기간을 확인한다.

### 10.4 알림 문구 기준

```text
MapMate
오늘 회사 출근은 08:12에 출발하는 것을 추천해요.
예상 이동 시간 42분, 개인 보정 6분, 안전 여유 5분이 반영됐어요.
```

문구는 추천 이유를 간단히 포함해야 한다.

---

## 11. 화면 구성 기준

### 11.1 화면 목록

| 화면 | 역할 |
|---|---|
| Home | 오늘의 루틴, 권장 출발 시각 표시 |
| RoutineEdit | 루틴 등록/수정 |
| PredictionDetail | 예상 이동 시간, 추천 이유, 경로 요약 표시 |
| Tracking | 탑승 여부, 도착 여부 기록 |
| ArrivalComplete | 도착 결과와 오차 표시 |
| History | 최근 기록과 도착 패턴 표시 |
| Settings | 알림, 개인 보정 시간, 데이터 관리 |

### 11.2 Navigation Route 기준

```kotlin
sealed class MapMateRoute(val route: String) {
    data object Home : MapMateRoute("home")
    data object RoutineEdit : MapMateRoute("routine_edit?routineId={routineId}")
    data object PredictionDetail : MapMateRoute("prediction/{routineId}")
    data object Tracking : MapMateRoute("tracking/{recordId}")
    data object History : MapMateRoute("history")
    data object Settings : MapMateRoute("settings")
}
```

### 11.3 UI 상태 기준

모든 주요 화면은 다음 상태를 고려한다.

| 상태 | 필요 여부 |
|---|---|
| Loading | 데이터 로딩 중 |
| Empty | 루틴 또는 기록 없음 |
| Success | 정상 표시 |
| Error | API/DB/권한 오류 |

### 11.4 홈 화면 기준

홈 화면은 사용자가 앱을 열자마자 다음 정보를 확인할 수 있어야 한다.

- 오늘 활성화된 루틴
- 목표 도착 시각
- 권장 출발 시각
- 추천 시각이 바뀐 이유
- 상세 보기 버튼
- 루틴이 없는 경우 루틴 등록 버튼

---

## 12. Repository / UseCase 기준

### 12.1 Repository 인터페이스

Domain 계층에 Repository 인터페이스를 둔다.

```kotlin
interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    suspend fun getRoutine(id: Long): Routine?
    suspend fun saveRoutine(routine: Routine): Long
    suspend fun deleteRoutine(id: Long)
}
```

```kotlin
interface CommuteRecordRepository {
    fun observeRecords(routineId: Long): Flow<List<CommuteRecord>>
    suspend fun saveRecord(record: CommuteRecord): Long
    suspend fun updateArrival(recordId: Long, arrivedAt: LocalTime)
}
```

### 12.2 UseCase 기준

UseCase는 하나의 사용자 행위 또는 하나의 비즈니스 기능을 나타낸다.

권장 UseCase:

```text
GetTodayRoutineUseCase
SaveRoutineUseCase
CalculateRecommendationUseCase
ScheduleRoutineNotificationUseCase
RecordBoardingUseCase
RecordArrivalUseCase
UpdatePersonalAdjustmentUseCase
GetCommuteHistoryUseCase
```

### 12.3 UseCase 작성 원칙

- UseCase 이름은 동사로 시작한다.
- 하나의 UseCase는 하나의 명확한 기능만 수행한다.
- 여러 Repository를 조합하는 로직은 ViewModel이 아니라 UseCase에서 처리한다.
- 순수 계산은 Calculator로 분리하고 UseCase에서 호출한다.

---

## 13. 에러 처리 기준

### 13.1 Result 타입 기준

비즈니스 로직에서 실패 가능성이 있는 경우 `Result` 또는 sealed class를 사용한다.

```kotlin
sealed interface MapMateResult<out T> {
    data class Success<T>(val data: T) : MapMateResult<T>
    data class Error(val reason: MapMateError) : MapMateResult<Nothing>
}
```

```kotlin
sealed interface MapMateError {
    data object NetworkUnavailable : MapMateError
    data object RouteNotFound : MapMateError
    data object PermissionDenied : MapMateError
    data object Unknown : MapMateError
}
```

### 13.2 사용자에게 보여줄 오류 문구

- 기술적인 오류 내용을 그대로 노출하지 않는다.
- 사용자가 다음 행동을 알 수 있게 작성한다.

예시:

```text
경로 정보를 불러오지 못했어요. 마지막으로 계산된 출발 시각을 표시할게요.
알림 권한이 꺼져 있어요. 출발 알림을 받으려면 알림 권한을 허용해 주세요.
루틴을 저장하지 못했어요. 입력값을 확인한 뒤 다시 시도해 주세요.
```

---

## 14. 테스트 기준

### 14.1 반드시 테스트할 로직

| 테스트 대상 | 테스트 내용 |
|---|---|
| DepartureTimeCalculator | 추천 출발 시각 계산 정확성 |
| PersonalAdjustmentCalculator | 도착 오차 기반 보정값 업데이트 |
| RepeatDay Logic | 오늘 요일에 루틴이 활성화되는지 |
| RoutineRepository | 루틴 저장/조회/삭제 |
| CommuteRecordRepository | 기록 저장/도착 업데이트 |
| Notification Scheduler | 알림 예약/취소 로직 |
| RouteDataSource | API 실패 시 fallback 동작 |

### 14.2 추천 계산 테스트 예시

```kotlin
@Test
fun calculateDepartureTime_returnsExpectedTime() {
    val calculator = DepartureTimeCalculator()

    val result = calculator.calculate(
        targetArrivalTime = LocalTime.of(9, 0),
        routeDurationMinutes = 42,
        personalAdjustmentMinutes = 6,
        safetyMarginMinutes = 5,
    )

    assertEquals(LocalTime.of(8, 7), result)
}
```

### 14.3 테스트 우선순위

```text
1. 순수 계산 로직 단위 테스트
2. Repository 단위 테스트
3. ViewModel 상태 변경 테스트
4. 주요 화면 Compose Preview 확인
5. 발표 시나리오 수동 테스트
```

### 14.4 발표 전 수동 테스트 시나리오

```text
1. 루틴이 없는 상태에서 홈 화면 진입
2. 회사 출근 루틴 등록: 평일 09:00 도착
3. 예상 이동 시간으로 권장 출발 시각 계산 확인
4. 알림 예약 확인
5. 이동 중 탑승 버튼 클릭
6. 목적지 도착 버튼 클릭
7. 도착 오차 저장 확인
8. 다음 추천에 개인 보정값 반영 확인
9. 기록/통계 화면에서 최근 기록 확인
```

---

## 15. Git / GitHub 협업 기준

### 15.1 브랜치 전략

```text
main
 └─ develop
     ├─ feature/home
     ├─ feature/routine
     ├─ feature/route-api
     ├─ feature/notification
     ├─ feature/tracking
     ├─ feature/history
     └─ fix/... 
```

| 브랜치 | 역할 |
|---|---|
| `main` | 발표 가능한 안정 버전 |
| `develop` | 통합 개발 브랜치 |
| `feature/*` | 기능 개발 |
| `fix/*` | 버그 수정 |
| `docs/*` | 문서 수정 |
| `refactor/*` | 구조 개선 |

### 15.2 커밋 메시지 기준

```text
feat: add routine registration screen
fix: correct departure time calculation
refactor: separate route repository
style: apply ktlint formatting
docs: update codex development standards
test: add departure calculator test
chore: update gradle configuration
```

### 15.3 커밋 타입

| 타입 | 의미 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 구조 개선 |
| `style` | 포맷팅, 세미콜론, import 정리 |
| `docs` | 문서 변경 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드 설정, 의존성, 기타 작업 |

### 15.4 PR 기준

PR에는 다음 내용을 포함한다.

```markdown
## Summary
- 구현한 내용 요약

## Test
- 실행한 테스트 또는 확인 방법

## Screenshot
- UI 변경 시 스크린샷 첨부

## Notes
- 리뷰어가 알아야 할 사항
```

### 15.5 Merge 기준

- `main`에 직접 push하지 않는다.
- 기능 개발은 `feature/*` 브랜치에서 진행한다.
- 최소 1명 이상의 리뷰 후 merge한다.
- Codex가 생성한 코드는 작성자가 직접 이해하고 설명할 수 있어야 한다.
- 빌드 실패, 명백한 크래시, 실제 API 키 포함 PR은 merge하지 않는다.

---

## 16. 팀 역할 기준

| 담당 | 주요 책임 |
|---|---|
| 프론트엔드 담당 | Compose 화면, Navigation, UI 상태 연결, Preview |
| DB/API/로직 담당 | Room, DataStore, Retrofit, Repository, 추천 로직 |
| 문서/테스트 담당 | README, 발표 자료, 테스트 시나리오, 수동 검증 |

역할은 주 담당자를 의미하며, 최종 merge 전에는 서로의 코드를 리뷰한다.

---

## 17. Codex 사용 기준

### 17.1 Codex 기본 원칙

Codex는 보조 개발 도구이다.  
Codex가 생성한 코드를 그대로 신뢰하지 않고, 반드시 사람이 검토한 뒤 반영한다.

### 17.2 Codex에게 허용할 작업

| 허용 작업 | 설명 |
|---|---|
| Compose UI 초안 | Screen, Card, Button, Preview 작성 |
| ViewModel boilerplate | UiState, Event, StateFlow 구조 작성 |
| Room Entity/DAO 초안 | 기준에 맞는 Entity와 DAO 작성 |
| Repository boilerplate | Interface 구현, mapper 작성 |
| Retrofit DTO 초안 | API 응답 구조에 맞는 DTO 작성 |
| 테스트 코드 초안 | Calculator, UseCase, Repository 테스트 작성 |
| 리팩토링 제안 | 중복 제거, 계층 분리, 함수 분리 |

### 17.3 Codex에게 맡기면 안 되는 작업

| 제한 작업 | 이유 |
|---|---|
| 이해하지 못한 대규모 구조 변경 | 팀 유지보수 어려움 |
| API Key 처리 임의 변경 | 보안 위험 |
| 추천 공식 임의 변경 | 프로젝트 핵심 기준 훼손 |
| DB Migration 임의 작성 | 데이터 손상 가능성 |
| Gradle 의존성 무단 추가 | 빌드 충돌 가능성 |
| MVP 범위 밖 기능 추가 | 일정 지연 |

### 17.4 Codex 프롬프트 작성 기준

Codex에게 작업을 맡길 때는 반드시 다음 정보를 포함한다.

```text
1. 작업할 기능
2. 수정하거나 생성할 파일 경로
3. 따라야 할 아키텍처 기준
4. 입력/출력 조건
5. 금지 사항
6. 테스트 또는 검증 기준
```

### 17.5 Codex 프롬프트 템플릿

```text
You are working on the MapMate Android project.
Follow MAPMATE_CODEX_DEVELOPMENT_STANDARDS.md.

Task:
- [구현할 작업]

Project rules:
- Kotlin + Jetpack Compose only.
- Use ViewModel + StateFlow for screen state.
- Do not access Room or Retrofit directly from Composable.
- Keep business logic in domain/usecase or domain/calculator.
- Do not change the recommendation formula unless explicitly asked.
- Do not add new dependencies without explaining why.

Files to create or modify:
- [파일 경로]

Expected behavior:
- [기대 동작]

Test requirements:
- [테스트 기준]
```

### 17.6 Codex 결과 검토 체크리스트

Codex 결과물을 merge하기 전 다음을 확인한다.

```text
[ ] 프로젝트 패키지 구조를 따르는가?
[ ] Composable에서 DB/API를 직접 호출하지 않는가?
[ ] ViewModel이 UiState를 StateFlow로 제공하는가?
[ ] 추천 계산 공식이 기준과 일치하는가?
[ ] API Key 또는 비밀값이 포함되지 않았는가?
[ ] 불필요한 의존성을 추가하지 않았는가?
[ ] 빌드가 성공하는가?
[ ] 주요 로직 테스트가 있는가?
[ ] 함수/변수명이 의미 있게 작성되었는가?
[ ] MVP 범위를 벗어난 기능을 임의로 추가하지 않았는가?
```

---

## 18. 구현 순서 기준

개발은 다음 순서로 진행한다.

### 18.1 1단계: 프로젝트 뼈대와 UI

- 패키지 구조 생성
- Navigation 구성
- Home 화면 초안
- RoutineEdit 화면 초안
- PredictionDetail 화면 초안
- Tracking 화면 초안

### 18.2 2단계: 로컬 저장

- RoutineEntity, CommuteRecordEntity 작성
- DAO 작성
- Room Database 구성
- DataStore 설정 저장 구성
- Repository 구현

### 18.3 3단계: 추천 계산

- DepartureTimeCalculator 작성
- PersonalAdjustmentCalculator 작성
- CalculateRecommendationUseCase 작성
- 단위 테스트 작성

### 18.4 4단계: Mock API 연동

- RouteDataSource 인터페이스 작성
- MockRouteDataSource 작성
- 예상 이동 시간 기반 추천 흐름 연결

### 18.5 5단계: 알림과 기록

- AlarmManager 기반 출발 알림 예약
- WorkManager 기반 출발 전 재조회
- TAGO 기반 첫 버스 실시간 보정 결과를 반영한 알림 재예약
- 탑승/도착 기록 저장
- 도착 오차 계산

### 18.6 6단계: 실제 API 또는 발표용 고정 데이터

- 실제 지도 API 연결
- API 실패 fallback 구현
- 데모용 고정 구간 또는 Mock 응답 유지

### 18.6.1 실시간 출발 보정 구현 순서

1. ODsay 세부 경로 DTO 확장
2. 첫 버스 구간 추출
3. TAGO 정류소정보 provider 구현
4. TAGO 도착정보 provider 구현
5. 노선번호 정규화와 매칭 점수식 구현
6. `RouteRealtimeSnapshot` 저장
7. `adjustedRouteDurationMinutes` 계산
8. fallback과 stale 처리
9. `finalDepartureTime < now` clamp 처리
10. AlarmManager 기본 알림
11. WorkManager 출발 전 재조회
12. AlarmManager 재예약
13. 이동 기록 저장
14. 개인보정 자동 계산
15. 지하철 지역별 provider

### 18.7 7단계: 발표 안정화

- 주요 시나리오 수동 테스트
- 오류 상태 처리
- UI 문구 정리
- README와 발표 자료 업데이트

---

## 19. Definition of Done

하나의 기능은 다음 조건을 만족해야 완료로 본다.

```text
[ ] 기능이 MVP 범위 안에 있다.
[ ] 기준 패키지 구조에 맞게 구현되었다.
[ ] UI는 Compose로 구현되었다.
[ ] ViewModel은 StateFlow로 UiState를 제공한다.
[ ] DB/API 접근은 Repository를 통해 이루어진다.
[ ] 추천 계산 또는 보정 로직은 domain 계층에 있다.
[ ] 오류/빈 상태/로딩 상태 중 필요한 상태를 처리한다.
[ ] 주요 로직은 테스트 또는 수동 테스트로 검증되었다.
[ ] 실제 API 키가 포함되지 않았다.
[ ] PR 설명과 테스트 방법이 작성되었다.
[ ] 팀원이 코드를 읽고 이해할 수 있다.
```

---

## 20. 최종 개발 전 체크리스트

개발 시작 전에 팀이 다음 항목을 확정한다.

```text
[ ] MVP 필수 기능 확정
[ ] 제외 기능 확정
[ ] 기술 스택 확정
[ ] 패키지 구조 확정
[ ] Coding style 확정
[ ] Git branch/commit/PR 규칙 확정
[ ] Codex 사용 규칙 확정
[ ] DB Entity 구조 확정
[ ] 추천 계산 공식 확정
[ ] 개인 보정값 업데이트 방식 확정
[ ] Mock API 전략 확정
[ ] 실제 지도 API 후보 확정
[ ] 알림/백그라운드 동작 기준 확정
[ ] 화면 목록과 Navigation 구조 확정
[ ] 테스트 대상 확정
[ ] 발표용 데모 시나리오 확정
```

---

## 21. 가장 중요한 원칙

```text
기능을 많이 만드는 것보다
루틴 등록 → 추천 계산 → 알림 → 기록 → 보정
흐름이 끊기지 않게 만드는 것이 우선이다.
```

Codex는 이 원칙을 벗어나는 코드를 생성하지 않아야 한다.
