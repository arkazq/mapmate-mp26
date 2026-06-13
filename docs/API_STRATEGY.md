# MapMate API Strategy

이 문서는 MapMate의 외부 API 연동 전략과 현재 구현 상태를 정리합니다.

현재 프로젝트는 Retrofit 기반 실제 API provider를 사용하지만, 발표와 개발 안정성을 위해 mock fallback을 유지합니다. API 키, 출발지/목적지 좌표가 없거나 실제 API 호출이 실패하면 기존 mock provider 결과로 앱 흐름을 계속 진행합니다.

## 현재 상태

- `KakaoPlaceSearchProvider` 구현: Kakao Local API 키워드 장소 검색 사용
- `KakaoReverseGeocodingProvider` 구현: Kakao Local API 좌표→주소 변환으로 현재 위치 출발지 주소 표시
- `OdsayRouteEstimateProvider` 구현: ODsay 대중교통 경로 검색 사용
- `SeoulBusRealtimeArrivalProvider` 구현: 서울특별시 버스도착정보조회 서비스로 첫 버스 대기 시간 보정
- `SeoulSubwayRealtimeArrivalProvider` 구현: 서울 지하철 실시간 도착정보로 첫 지하철 대기 시간 보정
- `GoogleRoutesEstimateProvider` 구현: Google Routes API 경로 시간 조회 사용
- `FallbackPlaceSearchProvider` 구현: Kakao 실패 또는 빈 결과 시 mock 장소 후보 사용
- `FallbackRouteEstimateProvider` 구현: ODsay/Google 실패 시 mock 이동 시간 사용, fallback 사유를 사용자용 상태 메시지로 요약
- 루틴 등록 화면에서 출발지를 Kakao 장소 검색으로 선택 가능
- 루틴 등록 화면에서 Android 현재 위치 권한을 받아 휴대폰 위치를 출발지로 설정하고, Kakao 키가 있으면 좌표를 주소로 변환 가능
- Retrofit2와 Kotlinx Serialization 사용
- API key는 `local.properties`에서 BuildConfig로 주입
- Room 기반 루틴/이동 기록 저장, DataStore 기반 설정 저장과 도착 오차 기반 개인 보정 자동 업데이트는 이미 구현되어 있음
- AlarmManager 기반 출발 알림은 저장된 루틴과 현재 경로 provider/fallback 예상 시간을 사용해 다음 권장 출발 시각을 예약함
- WorkManager 기반 출발 전 재조회는 다음 출발 알림 30분 전에 경로 provider/fallback을 다시 호출해 알림 예약을 갱신함

## API 키 설정

실제 API를 사용하려면 Git에 커밋되지 않는 `local.properties`에 다음 값을 추가합니다.

```properties
KAKAO_REST_API_KEY=API_KEY_PLACEHOLDER
ODSAY_API_KEY=API_KEY_PLACEHOLDER
GOOGLE_ROUTES_API_KEY=API_KEY_PLACEHOLDER
SEOUL_OPEN_API_KEY=API_KEY_PLACEHOLDER
SEOUL_BUS_SERVICE_KEY=API_KEY_PLACEHOLDER
```

출발지는 앱의 루틴 등록 화면에서 검색으로 선택하거나 `현재 위치 사용` 버튼으로 설정합니다. 현재 위치는 Kakao 좌표→주소 API로 읽기 쉬운 주소 변환을 시도하고, ODsay/Google Routes 실제 경로 조회는 선택된 출발지와 목적지에 위도/경도 좌표가 있을 때 우선 시도합니다.

## API 후보별 역할

| API | 현재 역할 | 비고 |
|---|---|---|
| Kakao Local API | 장소 검색, 주소, 좌표 조회 | `PlaceSearchProvider`, `ReverseGeocodingProvider` 실제 구현 |
| ODsay API | 한국 대중교통 예상 이동 시간 | `TRANSIT` 우선 provider |
| TAGO 버스정류소정보 API | 미구현 | 전국 버스 첫 탑승 정류장의 `cityCode`, `nodeId` 후보 매칭 |
| TAGO 버스도착정보 API | 미구현 | 전국 버스 첫 탑승 대기시간 `arrtime` 보정 |
| Google Routes API | 도보/자동차 예상 이동 시간, 대중교통 fallback | `WALK`, `CAR`, 필요 시 `TRANSIT` |
| Naver Directions 5 | 미구현 | 자동차 경로 대안 후보 |

## 실시간성 확장 전략

ODsay 대중교통 길찾기 API의 `totalTime`은 대중교통 경로의 기본 예상 소요시간으로 사용합니다. 이 값에는 정류장/역 접근 도보, 환승 도보, 탑승 구간 시간이 포함될 수 있지만, 현재 버스 지연, 도로 정체, 지하철 지연, 실제 정류장 도착 예정 시간까지 완전히 보장하는 실시간 값으로 보지는 않습니다.

따라서 출발 직전 추천 정확도를 높이려면 길찾기 API와 별도의 실시간 도착/위치 API를 조합합니다.

| 목적 | 후보 API | 우선순위 | 사용 기준 |
|---|---|---:|---|
| 장소 검색, 좌표 변환 | Kakao Local API | 높음 | 출발지/목적지 검색, 현재 위치 좌표의 주소 표시 |
| 대중교통 기본 경로 | ODsay 대중교통 길찾기 | 높음 | 기본 `routeDurationMinutes` 산출 |
| 버스 실시간 도착 | 서울특별시 버스도착정보조회 서비스 | 높음 | 서울권 첫 탑승 정류장의 실제 대기 시간 보정 |
| 지하철 실시간 도착 | 서울특별시 지하철 실시간 도착정보 | 높음 | 서울권 첫 탑승역의 실제 도착 예정 보정 |
| 전국 버스 정류소 매칭 | TAGO 버스정류소정보 API | 중간 | 전국 확장 시 ODsay 첫 탑승 정류장 좌표 기준 `cityCode`, `nodeId` 후보 확보 |
| 전국 버스 실시간 도착 | TAGO 버스도착정보 API | 중간 | 전국 확장 시 첫 탑승 버스 `arrtime` 보정 |
| 버스 실시간 위치 | ODsay 실시간 버스 위치정보 또는 지역별 버스위치정보 서비스 | 낮음~중간 | 도착정보 보조, 운행 지연 판단 |
| 지하철 열차 위치 | 서울특별시 지하철 실시간 열차 위치정보 | 중간 | 지하철 도착정보 보조 |
| 자동차 교통 상황 | Google Routes API `TRAFFIC_AWARE` | 낮음~중간 | 자동차 이동수단에서만 우선 적용 |
| 현재 위치 | Android 위치 provider | 높음 | 출발지 자동 설정, 접근 도보 시간 기준 좌표 |

실시간 버스 보정은 다음 구조를 기준으로 합니다.

1. 현재 ODsay 길찾기 결과를 기본 예상 이동 시간으로 유지합니다.
2. ODsay 경로 응답에서 첫 탑승 수단, 정류장/역 이름, 노선 정보를 추출합니다.
3. 첫 탑승 구간이 버스이면 서울 버스 실시간 도착정보를 조회해 대기 시간을 보정합니다.
4. 첫 탑승 구간이 지하철이면 서울 지하철 실시간 도착정보를 조회해 대기 시간을 보정합니다.
5. 실시간 정보 조회 실패, 낮은 매칭 신뢰도, 오래된 마지막 성공값은 ODsay 기본 예상시간으로 fallback합니다.
6. 전국 버스 확장 시에는 ODsay 첫 탑승 정류장 좌표 기준으로 TAGO 버스정류소정보 API의 근접 정류소 후보를 조회합니다.
7. 전국 버스 확장 시 정류장 거리, 정류장명 유사도, 노선번호 정규화, 방향 정보를 기준으로 `cityCode`, `nodeId` 후보의 매칭 신뢰도를 계산합니다.
8. 보정 결과를 영속화할 때는 `Routine`이 아니라 특정 시점의 `RouteRealtimeSnapshot`에 저장합니다.
9. 출발 시각이 이미 지난 시간으로 재계산되면 `지금 출발 권장` 상태로 처리합니다.
10. 현재 AlarmManager 출발 알림은 앱 실행/설정 변경/루틴 변경/부팅 후 다음 권장 출발 시각을 예약합니다.
11. WorkManager는 출발 알림 30분 전에 경로 provider/fallback을 다시 호출해 예약된 알림을 보정합니다.
12. 이동 기록 저장 시 목표 대비 도착 오차를 개인 보정값에 반영합니다. 현재는 한 번의 기록이 보정값을 과도하게 흔들지 않도록 최대 ±5분 범위에서 조정합니다.
13. 자동차 모드는 Google Routes `TRAFFIC_AWARE` 옵션을 별도 provider 정책으로 적용합니다.

MVP에서는 실시간 API가 실패해도 기존 `ODsay/Google Routes → Mock fallback` 흐름을 유지해야 합니다. 실시간 API 결과는 권장 출발 시각을 보정하는 추가 입력값으로만 사용하고, 추천 공식 자체는 변경하지 않습니다. 실제 경로 API가 mock fallback으로 내려가면 화면에는 기본 예상 시간을 사용했다는 상태와 요약 사유만 표시합니다.

자세한 실시간 출발 시각 보정 기준은 `docs/REALTIME_DEPARTURE_STRATEGY.md`를 따릅니다.

## Provider 구성

장소 검색은 `PlaceSearchProvider` interface 뒤에 숨깁니다.

```text
PlaceSearchProvider
├── FallbackPlaceSearchProvider
│   ├── KakaoPlaceSearchProvider
│   └── MockPlaceSearchProvider
```

현재 위치 좌표의 주소 변환은 `ReverseGeocodingProvider` interface 뒤에 숨깁니다.

```text
ReverseGeocodingProvider
└── KakaoReverseGeocodingProvider
```

이동 시간 조회는 `RouteEstimateProvider` interface 뒤에 숨깁니다.

```text
RouteEstimateProvider
├── FallbackRouteEstimateProvider
│   ├── OdsayRouteEstimateProvider
│   │   └── CompositeTransitArrivalProvider
│   │       ├── SeoulBusRealtimeArrivalProvider
│   │       └── SeoulSubwayRealtimeArrivalProvider
│   ├── GoogleRoutesEstimateProvider
│   └── MockRouteEstimateProvider
```

`RoutineRegistrationViewModel`은 provider interface에만 의존합니다. Composable이나 ViewModel은 Retrofit 구현 세부사항, API 키, DTO를 직접 알지 않습니다.

경로 조회 provider 입력은 다음 값을 기준으로 합니다.

```text
origin + destination + transportMode
```

`origin`은 사용자가 검색으로 선택한 출발지 또는 Android 현재 위치 provider가 반환한 좌표입니다. 현재 위치 provider는 Kakao 좌표→주소 API 호출이 성공하면 주소 문자열을 함께 채우고, 실패하면 기존 fallback 주소를 사용합니다.

## 현재 한계

- Google Routes transit 요청은 현재 도착/출발 시각을 지정하지 않고 즉시 경로 기준으로 조회합니다.
- ODsay 길찾기 결과만으로는 현재 버스/지하철 지연과 실제 정류장/역 도착 예정 시간을 완전히 보장하지 않습니다.
- 실시간 버스 도착정보와 지하철 실시간 도착정보 provider는 구현되어 있지만, ODsay 응답의 정류소/노선 ID 또는 역명 매칭이 실패하면 보정하지 않고 기존 ODsay 예상 시간을 유지합니다.
- 버스 위치정보와 지하철 열차 위치정보는 아직 provider로 구현되어 있지 않습니다.
- 실시간 보정 결과를 저장하는 `RouteRealtimeSnapshot` 저장 구조는 아직 없습니다.
- TAGO 버스정류소정보와 TAGO 버스도착정보 provider는 아직 구현되어 있지 않습니다.
- ODsay/Google API 실패 이유는 상세 예외문 대신 `API key 없음`, `좌표 없음`, `이동수단 미지원`, `경로 없음`, `요청 실패` 수준의 사용자용 메시지로 요약하고 mock fallback으로 복구합니다.
- API 응답 캐시나 유효기간이 있는 마지막 성공 보정값 fallback은 아직 없습니다.
- 실제 API 키는 저장소에 포함하지 않습니다.
- 현재 위치 주소 역지오코딩은 Kakao 키와 네트워크가 유효할 때만 성공하므로, 실패 시에는 `현재 위치` 이름과 fallback 주소를 저장합니다.
- 개인 보정 자동 업데이트는 단일 기록의 도착 오차를 제한적으로 반영하며, 최근 기록 평균이나 이동수단별 보정 분리는 아직 하지 않습니다.
- 출발 전 재조회는 현재 경로 provider/fallback을 다시 호출하므로, 첫 탑승 구간 매칭과 실시간 API 키가 유효하면 버스/지하철 도착정보 보정까지 함께 반영됩니다.

## 다음 권장 작업

1. TAGO 버스정류소정보 provider를 추가해 ODsay 첫 탑승 정류장 좌표 기준으로 `cityCode`, `nodeId` 후보를 찾습니다.
2. TAGO 버스도착정보 provider를 추가해 첫 버스 `arrtime`을 조회합니다.
3. ODsay 노선번호와 TAGO `routeno` 정규화, 정류장 거리, 정류장명 유사도 기반 매칭 점수식을 구현합니다.
4. `RouteRealtimeSnapshot` 저장 구조와 유효기간 기반 stale fallback을 추가합니다.
5. `adjustedRouteDurationMinutes` 계산과 `finalDepartureTime < now` clamp 처리를 추가합니다.
6. 버스 실시간 위치정보와 지하철 열차 위치정보 provider를 추가해 도착정보 보조와 운행 상태 표시를 강화합니다.
7. Google Routes `arrivalTime` 또는 `departureTime`을 추천 계산 흐름에 맞게 연결하고, 자동차 모드에는 `TRAFFIC_AWARE` 정책을 검토합니다.
8. 실시간 매칭 실패 상태도 경로 fallback 메시지와 같은 UI 패턴으로 통합합니다.
9. 최근 기록 평균 또는 이동수단별 도착 오차를 개인 보정 정책에 추가합니다.
10. 기본 출발지를 설정 화면에서 저장해 루틴 등록 기본값으로 반영합니다.
