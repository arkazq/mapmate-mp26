# MapMate API Strategy

이 문서는 MapMate의 외부 API 연동 전략과 현재 구현 상태를 정리합니다.

현재 프로젝트는 Retrofit 기반 실제 API provider를 추가했지만, 발표와 개발 안정성을 위해 mock fallback을 유지합니다. API 키나 출발 좌표가 없거나 실제 API 호출이 실패하면 기존 mock provider 결과로 앱 흐름을 계속 진행합니다.

## 현재 상태

- `KakaoPlaceSearchProvider` 구현: Kakao Local API 키워드 장소 검색 사용
- `OdsayRouteEstimateProvider` 구현: ODsay 대중교통 경로 검색 사용
- `GoogleRoutesEstimateProvider` 구현: Google Routes API 경로 시간 조회 사용
- `FallbackPlaceSearchProvider` 구현: Kakao 실패 또는 빈 결과 시 mock 장소 후보 사용
- `FallbackRouteEstimateProvider` 구현: ODsay/Google 실패 시 mock 이동 시간 사용
- Retrofit2와 Kotlinx Serialization 사용
- API key는 `local.properties`에서 BuildConfig로 주입
- Room 기반 루틴 저장, DataStore 기반 설정 저장은 이미 구현되어 있음

## API 키 설정

실제 API를 사용하려면 Git에 커밋되지 않는 `local.properties`에 다음 값을 추가합니다.

```properties
KAKAO_REST_API_KEY=API_KEY_PLACEHOLDER
ODSAY_API_KEY=API_KEY_PLACEHOLDER
GOOGLE_ROUTES_API_KEY=API_KEY_PLACEHOLDER
MAPMATE_ORIGIN_LATITUDE=37.4963
MAPMATE_ORIGIN_LONGITUDE=126.9574
```

`MAPMATE_ORIGIN_LATITUDE`, `MAPMATE_ORIGIN_LONGITUDE`는 현재 루틴 등록 화면에 출발지 입력 UI가 없기 때문에 실제 경로 API 호출에 사용하는 데모 출발 좌표입니다. 값이 없으면 경로 API provider는 동작하지 않고 mock fallback을 사용합니다.

## API 후보별 역할

| API | 현재 역할 | 비고 |
|---|---|---|
| Kakao Local API | 장소 검색, 주소, 좌표 조회 | `PlaceSearchProvider` 실제 구현 |
| ODsay API | 한국 대중교통 예상 이동 시간 | `TRANSIT` 우선 provider |
| Google Routes API | 도보/자동차 예상 이동 시간, 대중교통 fallback | `WALK`, `CAR`, 필요 시 `TRANSIT` |
| Naver Directions 5 | 미구현 | 자동차 경로 대안 후보 |

## Provider 구성

장소 검색은 `PlaceSearchProvider` interface 뒤에 숨깁니다.

```text
PlaceSearchProvider
├── FallbackPlaceSearchProvider
│   ├── KakaoPlaceSearchProvider
│   └── MockPlaceSearchProvider
```

이동 시간 조회는 `RouteEstimateProvider` interface 뒤에 숨깁니다.

```text
RouteEstimateProvider
├── FallbackRouteEstimateProvider
│   ├── OdsayRouteEstimateProvider
│   ├── GoogleRoutesEstimateProvider
│   └── MockRouteEstimateProvider
```

`RoutineRegistrationViewModel`은 provider interface에만 의존합니다. Composable이나 ViewModel은 Retrofit 구현 세부사항, API 키, DTO를 직접 알지 않습니다.

## 현재 한계

- 출발지 입력 UI가 아직 없어 실제 경로 API는 `local.properties`의 데모 출발 좌표를 사용합니다.
- Google Routes transit 요청은 현재 도착/출발 시각을 지정하지 않고 즉시 경로 기준으로 조회합니다.
- ODsay/Google API 실패 이유는 UI에 세부 노출하지 않고 mock fallback으로 복구합니다.
- API 응답 캐시나 마지막 성공값 fallback은 아직 없습니다.
- 실제 API 키는 저장소에 포함하지 않습니다.

## 다음 권장 작업

1. 루틴 등록 화면에 출발지 입력 또는 기본 출발지 설정을 추가합니다.
2. `RouteEstimateProvider` 요청 모델을 확장해 출발지와 목표 도착 시각을 명시적으로 전달합니다.
3. Google Routes `arrivalTime` 또는 `departureTime`을 추천 계산 흐름에 맞게 연결합니다.
4. 실제 API 실패 시 마지막 성공값 캐시를 fallback 후보에 추가합니다.
5. API 실패/좌표 없음 상태를 사용자가 이해할 수 있는 UI 메시지로 분리합니다.
