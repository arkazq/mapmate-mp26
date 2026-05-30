# MapMate API Strategy

이 문서는 MapMate의 외부 API 연동 전략을 정리합니다.

현재 프로젝트는 실제 API를 사용하지 않습니다. 발표와 개발 안정성을 위해 mock provider 기반으로 MVP 흐름을 먼저 구현했습니다.

## 현재 상태

- 실제 API 연동 없음
- `MockPlaceSearchProvider` 사용
- `MockRouteEstimateProvider` 사용
- API key 필요 없음
- Retrofit 미사용
- Room/DataStore 미사용
- 발표/개발 안정성을 위해 mock 기반으로 루틴 등록 → 예상 이동 시간 조회 → 권장 출발 시각 계산 흐름을 먼저 구현

## API 후보별 역할

| API | 역할 | 비고 |
|---|---|---|
| Kakao Local API | 장소 검색, 주소 검색, 좌표 변환 | `PlaceSearchProvider` 실제 구현 후보 |
| ODsay API | 한국 대중교통 경로 검색 후보, 대중교통 예상 이동 시간 후보 | `RouteEstimateProvider`의 대중교통 구현 후보 |
| Google Routes API | 대중교통 경로 검색 대안, arrival/departure time 기반 경로 계산 후보 | ODsay 대안 또는 보조 후보 |
| Naver Directions 5 | 자동차 경로 후보 | 대중교통 메인 API로 사용하지 않음 |

## Provider 교체 전략

장소 검색은 `PlaceSearchProvider` interface 뒤에 숨깁니다.

```text
PlaceSearchProvider
├── MockPlaceSearchProvider
└── KakaoPlaceSearchProvider    // future
```

이동 시간 조회는 `RouteEstimateProvider` interface 뒤에 숨깁니다.

```text
RouteEstimateProvider
├── MockRouteEstimateProvider
├── OdsayRouteEstimateProvider  // future
├── GoogleRouteEstimateProvider // future
└── NaverCarRouteEstimateProvider // future
```

현재 `RoutineRegistrationViewModel`은 provider interface에 의존합니다. 따라서 실제 API를 붙일 때는 mock provider를 제거하지 않고, 새 구현체를 추가한 뒤 주입 방식을 정리하는 방향으로 확장합니다.

## 실제 API 연동 시 주의사항

- API key를 Git에 커밋하지 않습니다.
- `local.properties`, `secrets.properties`, 환경 변수 등으로 비밀값을 분리합니다.
- Retrofit 추가는 별도 PR에서 진행합니다.
- 실제 API 실패 시 mock fallback을 유지합니다.
- 발표 시연용 mock 데이터는 제거하지 않고 유지합니다.
- API quota, 과금, 응답 실패 대응이 필요합니다.
- 목적지 좌표가 없거나 경로 검색이 실패한 경우 사용자에게 명확한 오류를 표시해야 합니다.
- 실제 API provider 추가 시 mock provider를 제거하지 말고 fallback으로 유지합니다.

## 향후 권장 작업 순서

1. `KakaoPlaceSearchProvider`로 장소 검색과 좌표 변환을 구현합니다.
2. `OdsayRouteEstimateProvider`로 한국 대중교통 예상 이동 시간을 구현합니다.
3. 필요하면 `GoogleRouteEstimateProvider`를 대안으로 추가합니다.
4. 자동차 경로가 필요할 때만 `NaverCarRouteEstimateProvider`를 추가합니다.
5. API 실패 시 `MockRouteEstimateProvider` 또는 마지막 성공값으로 fallback합니다.
