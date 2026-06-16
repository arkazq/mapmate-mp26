# MapMate API 전략

이 문서는 MapMate의 외부 API 연동 전략과 현재 구현 상태를 정리합니다.

현재 프로젝트는 Retrofit 기반 실제 API provider를 사용하지만, 발표와 개발 안정성을 위해 mock fallback을 유지합니다. API 키, 출발지/목적지 좌표가 없거나 실제 API 호출이 실패하면 기존 mock provider 결과로 앱 흐름을 계속 진행합니다.

## 현재 상태

- `KakaoPlaceSearchProvider` 구현: Kakao Local API 키워드 장소 검색 사용
- `KakaoReverseGeocodingProvider` 구현: Kakao Local API 좌표→주소 변환으로 현재 위치 출발지 주소 표시
- `OdsayRouteEstimateProvider` 구현: ODsay 대중교통 경로 검색 사용, 후보 `path` 최대 5개 평가
- `RouteCandidateEvaluator` 구현: 첫 버스 실시간 도착 대기시간과 정류장 접근 시간을 비교해 탑승 가능성이 낮은 ODsay 후보에 페널티 적용
- `SeoulBusRealtimeArrivalProvider` 구현: 서울특별시 버스도착정보조회 서비스로 첫 버스 대기 시간 보정
- `SeoulSubwayRealtimeArrivalProvider` 구현: 서울 지하철 실시간 도착정보 조회 provider 구현 완료, 현재 ODsay 후보 랭킹 보정은 첫 버스 구간에 우선 적용
- `RouteRealtimeSnapshot` 구현: 출발 예정 30분 이내 실시간 보정 성공값을 Room에 저장하고, 같은 30분 정책 안에서만 20분 이내 fresh snapshot을 fallback으로 재사용
- `TagoBusArrivalProvider` 구현: ODsay 첫 버스 탑승 정류장 좌표 기준 TAGO 근접 정류소/도착정보 fallback 사용, 괄호/대괄호 업체명 표기 제거로 노선번호 매칭 보강
- `SeoulBusOperationStatusProvider`, `SeoulSubwayOperationStatusProvider` 구현: 버스/지하철 위치정보를 운행 상태 보조 설명으로 사용
- `GoogleRoutesEstimateProvider` 구현: Google Routes API 경로 시간 조회 사용, 자동차 모드는 `TRAFFIC_AWARE`
- `CachingRouteEstimateProvider` 구현: 실시간 보정이 없는 실제 경로 provider 성공값을 6시간 Room cache로 저장하고 실패 시 mock 전에 재사용
- `FallbackPlaceSearchProvider` 구현: Kakao 실패 또는 빈 결과 시 mock 장소 후보 사용
- `FallbackRouteEstimateProvider` 구현: ODsay/Google 실패 시 mock 이동 시간 사용, fallback 사유를 사용자용 상태 메시지로 요약
- `RouteBoardingAdvice` 구현: 실시간 후보 랭킹 결과를 홈/상세 예측 UI에서 사용할 수 있는 구조화 데이터로 전달
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
TAGO_SERVICE_KEY=API_KEY_PLACEHOLDER
```

Android/Gradle 흐름에서는 `local.properties`를 기본 로컬 키 파일로 사용합니다. `.env`도 Git에 올리지 않는 로컬 파일로 쓸 수는 있지만, 현재 구조에서는 추가 보안 이점을 주지 않으므로 사용하지 않습니다.

주의할 점은 `BuildConfig`로 주입된 키가 APK 안에 포함된다는 것입니다. 이 방식은 저장소 커밋 방지에는 효과가 있지만, 운영 앱에서 키를 완전히 숨기는 수단은 아닙니다. 운영 배포 전에는 API별 앱 제한, API 제한, 호출량 모니터링을 설정하고, 노출되면 안 되는 키나 과금 위험이 큰 API는 백엔드 프록시를 통해 호출하는 방식을 검토합니다.

출발지는 앱의 루틴 등록 화면에서 검색으로 선택하거나 `현재 위치 사용` 버튼으로 설정합니다. 현재 위치는 Kakao 좌표→주소 API로 읽기 쉬운 주소 변환을 시도하고, ODsay/Google Routes 실제 경로 조회는 선택된 출발지와 목적지에 위도/경도 좌표가 있을 때 우선 시도합니다.

## API 후보별 역할

| API | 현재 역할 | 비고 |
|---|---|---|
| Kakao Local API | 장소 검색, 주소, 좌표 조회 | `PlaceSearchProvider`, `ReverseGeocodingProvider` 실제 구현 |
| ODsay API | 한국 대중교통 예상 이동 시간 | `TRANSIT` 우선 provider |
| TAGO 버스정류소정보 API | 전국 버스 정류소 후보 매칭 | ODsay 첫 탑승 정류장 좌표가 있을 때 `cityCode`, `nodeId` 후보 조회 |
| TAGO 버스도착정보 API | 전국 버스 첫 탑승 대기시간 보정 | TAGO `arrtime`을 분 단위로 변환해 보정 |
| Google Routes API | 도보/자동차 예상 이동 시간, 대중교통 fallback | `CAR`는 `TRAFFIC_AWARE`, 필요 시 `TRANSIT` |
| Naver Directions 5 | 미구현 | 자동차 경로 대안 후보 |

## 실시간성 확장 전략

ODsay 대중교통 길찾기 API의 `totalTime`은 대중교통 경로의 기본 예상 소요시간으로 사용합니다. 이 값에는 정류장/역 접근 도보, 환승 도보, 탑승 구간 시간이 포함될 수 있지만, 현재 버스 지연, 도로 정체, 지하철 지연, 실제 정류장 도착 예정 시간까지 완전히 보장하는 실시간 값으로 보지는 않습니다.

따라서 출발 직전 추천 정확도를 높이려면 길찾기 API와 별도의 실시간 도착/위치 API를 조합합니다.

| 목적 | 후보 API | 우선순위 | 사용 기준 |
|---|---|---:|---|
| 장소 검색, 좌표 변환 | Kakao Local API | 높음 | 출발지/목적지 검색, 현재 위치 좌표의 주소 표시 |
| 대중교통 기본 경로 | ODsay 대중교통 길찾기 | 높음 | 기본 `routeDurationMinutes` 산출 |
| 버스 실시간 도착 | 서울특별시 버스도착정보조회 서비스 | 높음 | 서울권 첫 탑승 정류장의 실제 대기 시간 보정 |
| 지하철 실시간 도착 | 서울특별시 지하철 실시간 도착정보 | 중간 | provider 구현 완료, 현재 ODsay 후보 랭킹 보정에는 직접 적용하지 않음 |
| 전국 버스 정류소 매칭 | TAGO 버스정류소정보 API | 중간 | ODsay 첫 탑승 정류장 좌표 기준 `cityCode`, `nodeId` 후보 확보 |
| 전국 버스 실시간 도착 | TAGO 버스도착정보 API | 중간 | 첫 탑승 버스 `arrtime` 보정 |
| 버스 실시간 위치 | 서울특별시 버스위치정보 | 낮음~중간 | 도착정보 보조, 운행 상태 reason 보강 |
| 지하철 열차 위치 | 서울특별시 지하철 실시간 열차 위치정보 | 중간 | 지하철 도착정보 보조, 운행 상태 reason 보강 |
| 자동차 교통 상황 | Google Routes API `TRAFFIC_AWARE` | 낮음~중간 | 자동차 이동수단에서만 적용 |
| 현재 위치 | Android 위치 provider | 높음 | 출발지 자동 설정, 접근 도보 시간 기준 좌표 |

실시간 버스 보정과 후보 경로 선택은 다음 구조를 기준으로 합니다.

1. ODsay 길찾기 결과의 `path` 후보를 최대 5개까지 평가합니다.
2. 각 후보에서 `totalTime`, 환승 수, 도보 시간, `subPath` 기반 `RouteSegment`, 첫 대중교통 구간을 추출합니다.
3. `scheduledDepartureEpochMillis`가 없거나 출발까지 30분을 초과하면 실시간 도착정보와 snapshot fallback을 모두 사용하지 않고 ODsay 기본 후보 시간으로 랭킹합니다.
4. 출발까지 30분 이내이고 첫 탑승 구간이 버스이면 서울 버스 실시간 도착정보를 먼저 조회해 대기 시간을 보정합니다.
5. 서울 버스 매칭이 실패하고 ODsay 첫 탑승 정류장 좌표가 있으면 TAGO 버스정류소정보 API의 근접 정류소 후보를 조회합니다.
6. TAGO 후보는 정류장 거리, 정류장명 유사도, 노선번호 정규화 기준으로 `cityCode`, `nodeId`와 `arrtime`을 매칭합니다. ODsay가 `11(남양여객)`처럼 업체명을 괄호로 붙이는 경우에는 괄호/대괄호 내용을 제거해 TAGO `11`과 비교합니다.
7. 실시간 정보 조회 실패/매칭 실패 시에도 출발까지 30분 이내일 때만 20분 이내 `RouteRealtimeSnapshot`을 재사용하고, 없거나 만료되면 ODsay 기본 예상시간으로 fallback합니다.
8. `RouteCandidateEvaluator`는 탑승 여유, 총 소요시간, 환승 수, 도보 시간, 실시간 신뢰도를 점수화합니다.
9. 탑승 여유가 0분 미만이면 놓칠 위험 후보로 강하게 감점하고, 0~2분이면 빡빡한 후보로 중간 감점합니다.
10. 후보 전환 이득이 3분 미만이면 1순위 후보를 유지해 추천 흔들림을 줄입니다.
11. 선택된 후보의 `subPath`에서 생성한 `RouteSegment`만 `RouteEstimate.segments`에 넣습니다.
12. 보정 결과를 영속화할 때는 `Routine`이 아니라 특정 시점의 `RouteRealtimeSnapshot`에 저장합니다.
13. 실시간 보정 또는 snapshot fallback이 적용된 `RouteEstimate.hasRealtimeAdjustment`는 `true`이며, 일반 6시간 `RouteEstimateCache`에는 저장하지 않습니다.
14. 출발 시각이 이미 지난 시간으로 재계산되면 `지금 출발 권장` 상태로 처리합니다.
15. 현재 AlarmManager 출발 알림은 앱 실행/설정 변경/루틴 변경/부팅 후 다음 권장 출발 시각을 예약합니다.
16. WorkManager는 출발 전 `T-60/T-30/T-15/T-5` 재조회 작업을 예약하고, 긴 이동은 `T-90/T-120`도 추가합니다. 재조회 시 기존 출발 예정 시각을 provider에 전달해 30분 정책 안에서 실시간 보정이 반영됩니다.
17. 이동 기록 저장 시 목표 대비 도착 오차를 개인 보정값에 반영합니다. 현재는 한 번의 기록이 보정값을 과도하게 흔들지 않도록 최대 ±5분 범위에서 조정합니다.
18. 자동차 모드는 Google Routes `TRAFFIC_AWARE` 옵션을 provider 정책으로 적용합니다.
19. 실시간 보정이 없는 실제 ODsay/Google provider 성공값은 전체 경로 예상값으로 6시간 Room cache에 저장하고, 이후 API 실패 시 mock fallback 전에 재사용합니다.
20. 홈과 상세 예측 화면은 `RouteBoardingAdvice`를 사용해 추천 버스, 정류장 접근 시간, 실시간 도착 대기 시간, 탑승 여유, 대안 후보를 사용자용 문장으로 표시합니다.
21. 루틴 등록 화면은 provider의 내부 `reason` 문자열을 그대로 노출하지 않고, 이동 시간과 보정값을 요약한 사용자용 문장을 표시합니다.

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
└── SegmentAdjustedRouteEstimateProvider
    └── FallbackRouteEstimateProvider
        ├── CachingRouteEstimateProvider
        │   └── OdsayRouteEstimateProvider
        │       ├── CompositeTransitArrivalProvider
        │       │   ├── SeoulBusRealtimeArrivalProvider
        │       │   ├── TagoBusArrivalProvider
        │       │   └── SeoulSubwayRealtimeArrivalProvider
        │       └── CompositeTransitOperationStatusProvider
        │           ├── SeoulBusOperationStatusProvider
        │           └── SeoulSubwayOperationStatusProvider
        ├── CachingRouteEstimateProvider
        │   └── GoogleRoutesEstimateProvider
        └── MockRouteEstimateProvider
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
- 실시간 버스 도착정보와 지하철 실시간 도착정보 provider는 구현되어 있지만, 현재 ODsay 후보 경로 랭킹의 실시간 시간 보정과 탑승 가능성 평가는 첫 버스 구간에 우선 적용합니다.
- 주변 정류장의 버스를 직접 탐색해 ODsay 결과 밖의 대안을 추천하는 기능은 아직 구현하지 않았습니다.
- `RouteRealtimeSnapshot` fallback은 같은 ODsay 기본 경로와 첫 버스 탑승 구간에서 20분 이내 성공값만 재사용하며, `scheduledDepartureEpochMillis`가 없거나 출발까지 30분을 초과하면 사용하지 않습니다.
- TAGO provider는 ODsay 첫 탑승 정류장 좌표가 있어야 동작합니다. 좌표가 없거나 지역별 정류장/노선 표기가 맞지 않으면 기존 서울 provider 또는 mock fallback을 유지합니다.
- 서울특별시 버스도착정보조회 서비스 키와 도착정보 endpoint는 정상 응답을 확인했습니다. 다만 일부 ODsay 응답의 `busID/routeID`는 서울버스 `busRouteId`와 일치하지 않을 수 있어, 해당 경로는 서울버스 provider가 매칭 실패 후 TAGO/fresh snapshot/ODsay 기본값으로 fallback합니다.
- 버스 위치정보와 지하철 열차 위치정보는 운행 상태 보조 설명으로만 사용하며, 권장 출발 시각 계산값을 직접 대체하지 않습니다.
- ODsay/Google API 실패 이유는 상세 예외문 대신 `API key 없음`, `좌표 없음`, `이동수단 미지원`, `경로 없음`, `요청 실패` 수준의 사용자용 메시지로 요약하고 mock fallback으로 복구합니다.
- 전체 경로 API 마지막 성공값 cache는 6시간 TTL을 사용합니다. 실시간 보정 또는 snapshot fallback이 적용된 `RouteEstimate`는 이 cache에 저장하지 않습니다. 오래된 cache는 조회 전에 정리하며, cache도 없으면 기존 mock fallback으로 복구합니다.
- 실제 API 키는 저장소에 포함하지 않습니다.
- 로컬 키 파일은 `local.properties`를 사용하고 `.env`는 기본 키 파일로 사용하지 않습니다.
- API 키는 `BuildConfig`를 통해 APK에 들어가므로 운영 배포 전 키 제한 또는 백엔드 프록시를 검토해야 합니다.
- 현재 위치 주소 역지오코딩은 Kakao 키와 네트워크가 유효할 때만 성공하므로, 실패 시에는 `현재 위치` 이름과 fallback 주소를 저장합니다.
- 개인 보정 자동 업데이트는 단일 기록의 도착 오차를 제한적으로 반영합니다. 기록 탭 통계는 표시용이며, 최근 기록 평균이나 이동수단별 보정 분리는 아직 자동 보정 정책에 연결하지 않았습니다.
- 출발 전 재조회는 현재 경로 provider/fallback을 다시 호출하므로, 첫 버스 탑승 구간 매칭과 실시간 API 키가 유효하면 30분 정책 안에서 실시간 도착정보 보정 또는 fresh snapshot fallback이 반영됩니다.

## 다음 권장 작업

1. 서울 버스 도착정보 provider에 정류장 ARS 기준 조회 fallback을 추가하고, 도착목록에서 `rtNm`/`busRouteAbrv`를 ODsay `busNo`와 매칭합니다.
2. TAGO 정류소/노선 매칭을 실제 API 키와 여러 지역 샘플로 계속 검증하고, 도시별 표기 예외를 보강합니다.
3. Google Routes `arrivalTime` 또는 `departureTime`을 추천 계산 흐름에 맞게 연결합니다.
4. 실시간 매칭 실패 상태도 경로 fallback 메시지와 같은 UI 패턴으로 통합합니다.
5. 최근 기록 평균 또는 이동수단별 도착 오차를 개인 보정 정책에 추가합니다.
6. 기본 출발지를 설정 화면에서 저장해 루틴 등록 기본값으로 반영합니다.
7. 운영 배포 전 API 키 제한, 호출량 모니터링, 백엔드 프록시 필요 여부를 확정합니다.
# 현재 API 참고

최신 API 동작은 `docs/IMPLEMENTATION_UPDATE_2026_06_16.md`를 확인합니다. 현재 ODsay는 대중교통 기본 경로 provider이며, 실시간 보정과 탑승 가능성 랭킹은 ODsay 후보 경로 안의 첫 버스 후보에 우선 적용합니다. 주변 정류장의 모든 버스 대안을 직접 탐색하는 기능은 아직 구현하지 않았습니다. 서울버스는 현재 ODsay 노선 ID 기반 조회를 사용하므로, ID가 맞지 않는 경로를 위해 정류장 ARS 기반 도착목록 fallback을 후속 작업으로 둡니다.
