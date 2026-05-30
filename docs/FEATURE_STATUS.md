# MapMate Feature Status

현재 구현 상태를 기준으로 기능별 진행 상황을 정리합니다.

| 기능 | 상태 | 설명 | 관련 위치 |
| -- | -- | -- | ----- |
| Android 초기 프로젝트 | 완료 | Android Studio 생성 기반 프로젝트가 구성되어 있습니다. | `app`, `gradle`, `settings.gradle.kts` |
| 루틴 등록 UI | 완료 | 루틴 이름, 목적지, 도착 시각, 요일, 이동 수단, 보정 시간 입력 화면이 있습니다. | `presentation/routine/RoutineRegistrationScreen.kt` |
| 목적지 mock 후보 표시 | 완료 | 숭실대학교, 강남역, 서울역, 홍대입구역 후보를 표시합니다. | `data/mock/MockPlaceSearchProvider.kt` |
| 목적지 선택 | 완료 | 목적지 후보를 선택하면 `selectedDestination`에 반영됩니다. | `presentation/routine/RoutineRegistrationViewModel.kt` |
| 반복 요일 선택 | 완료 | 월~일 반복 요일을 선택/해제할 수 있습니다. | `presentation/routine/RoutineRegistrationComponents.kt` |
| 이동 수단 선택 | 완료 | `TRANSIT`, `WALK`, `CAR` 이동 수단을 선택할 수 있습니다. | `domain/model/TransportMode.kt` |
| 권장 출발 시각 계산 | 완료 | Mock 예상 이동 시간과 보정 시간을 기준으로 권장 출발 시각을 계산합니다. | `presentation/routine/RoutineRegistrationViewModel.kt` |
| `DepartureTimeCalculator` | 완료 | 순수 Kotlin 계산기로 권장 출발 시각을 계산합니다. | `domain/calculator/DepartureTimeCalculator.kt` |
| 계산 로직 테스트 | 완료 | 09:00, 42분, 6분, 5분 입력 시 08:07을 검증합니다. | `app/src/test/java/com/mapmate/domain/calculator/DepartureTimeCalculatorTest.kt` |
| Mock 장소 검색 provider | 완료 | `PlaceSearchProvider`의 mock 구현체가 있습니다. | `data/mock/MockPlaceSearchProvider.kt` |
| Mock 이동 시간 provider | 완료 | 이동 수단별 고정 예상 시간을 반환합니다. | `data/mock/MockRouteEstimateProvider.kt` |
| Room DB 저장 | 미구현 | 실제 루틴 저장소는 아직 없습니다. 현재 저장은 성공 상태 메시지 표시까지입니다. | 예정 |
| DataStore 설정 저장 | 미구현 | 앱 설정 저장 기능은 아직 없습니다. | 예정 |
| 실제 Kakao API | 미구현 | 장소 검색/좌표 변환 API는 아직 연결하지 않았습니다. | 예정 |
| 실제 ODsay API | 미구현 | 대중교통 경로 검색 API는 아직 연결하지 않았습니다. | 예정 |
| 실제 Google Routes API | 미구현 | 대중교통 경로 검색 대안 API는 아직 연결하지 않았습니다. | 예정 |
| 알림 | 미구현 | `AlarmManager` 기반 출발 알림은 아직 없습니다. | 예정 |
| WorkManager 재조회 | 미구현 | 출발 전 이동 시간 재조회 작업은 아직 없습니다. | 예정 |
| 이동 기록 저장 | 미구현 | 탑승/도착 기록 저장 기능은 아직 없습니다. | 예정 |
| 개인 보정값 업데이트 | 미구현 | 실제 도착 오차 기반 개인 보정 업데이트는 아직 없습니다. | 예정 |
| 홈 화면 | 미구현 | 앱 진입 화면은 현재 루틴 등록 화면입니다. | `MainActivity.kt` |
| 통계 화면 | 미구현 | 최근 기록/통계 화면은 아직 없습니다. | 예정 |
| 설정 화면 | 미구현 | 알림 설정, 보정값 설정 화면은 아직 없습니다. | 예정 |

## 현재 앱 진입점

현재 `MainActivity`는 `RoutineRegistrationRoute`를 바로 표시합니다.

```text
MainActivity
→ RoutineRegistrationRoute
→ RoutineRegistrationScreen
```

## 현재 저장 동작

현재 `루틴 저장` 버튼은 Room DB에 저장하지 않습니다. 입력값 검증 후 성공 메시지를 표시하는 단계까지 구현되어 있습니다.
