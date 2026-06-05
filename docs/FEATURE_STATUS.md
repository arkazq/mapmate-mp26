# MapMate Feature Status

현재 구현 상태를 기준으로 기능별 진행 상황을 정리합니다.

| 기능 | 상태 | 설명 | 관련 위치 |
| -- | -- | -- | ----- |
| Android 초기 프로젝트 | 완료 | Android Studio 생성 기반 프로젝트가 구성되어 있습니다. | `app`, `gradle`, `settings.gradle.kts` |
| 홈/루틴/설정 탭 진입점 | 완료 | 앱 상단 탭으로 홈, 루틴 등록 화면, 설정 화면을 전환합니다. | `presentation/MapMateApp.kt` |
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
| Room DB 저장 | 완료 | 입력값 검증 후 `routines` 테이블에 루틴을 실제 저장합니다. | `data/local`, `data/repository/RoomRoutineRepository.kt` |
| 저장된 루틴 목록 표시 | 완료 | Room `Flow`를 관찰해 저장된 루틴을 홈 화면에 표시합니다. | `presentation/home/HomeScreen.kt`, `presentation/home/HomeViewModel.kt` |
| 루틴 수정/삭제 | 완료 | 홈 화면의 저장된 루틴 카드에서 기존 값을 루틴 등록 화면으로 불러와 수정하거나 Room DB에서 삭제할 수 있습니다. | `presentation/home`, `presentation/routine`, `data/local/RoutineDao.kt` |
| DataStore 설정 저장 | 완료 | 개인 보정 시간, 안전 여유 시간, 알림 설정값, 기본 이동수단을 Preferences DataStore에 저장하고 앱 시작 시 복원합니다. | `data/preferences/DataStoreSettingsRepository.kt`, `domain/repository/SettingsRepository.kt` |
| 실제 Kakao API | 미구현 | 장소 검색/좌표 변환 API는 아직 연결하지 않았습니다. | 예정 |
| 실제 ODsay API | 미구현 | 대중교통 경로 검색 API는 아직 연결하지 않았습니다. | 예정 |
| 실제 Google Routes API | 미구현 | 대중교통 경로 검색 대안 API는 아직 연결하지 않았습니다. | 예정 |
| 알림 | 부분 완료 | 알림 사용 여부 설정값은 DataStore에 저장합니다. `AlarmManager` 기반 실제 예약은 아직 없습니다. | `presentation/settings/SettingsScreen.kt`, 예정 |
| WorkManager 재조회 | 미구현 | 출발 전 이동 시간 재조회 작업은 아직 없습니다. | 예정 |
| 이동 기록 저장 | 미구현 | 탑승/도착 기록 저장 기능은 아직 없습니다. | 예정 |
| 개인 보정값 업데이트 | 미구현 | 실제 도착 오차 기반 개인 보정 업데이트는 아직 없습니다. | 예정 |
| 홈 화면 | 완료 | 앱 첫 화면에서 저장된 루틴 목록과 빈 상태의 루틴 등록 버튼을 확인할 수 있습니다. | `presentation/home` |
| 통계 화면 | 미구현 | 최근 기록/통계 화면은 아직 없습니다. | 예정 |
| 설정 화면 | 완료 | 보정값, 기본 이동수단, 알림 사용 여부를 변경할 수 있습니다. | `presentation/settings` |

## 현재 앱 진입점

현재 `MainActivity`는 `MapMateApp`을 표시하고, `MapMateApp`이 홈/루틴/설정 탭을 관리합니다.

```text
MainActivity
→ MapMateApp
├→ HomeRoute
├→ RoutineRegistrationRoute
└→ SettingsRoute
```

## 현재 저장 동작

현재 `루틴 저장` 버튼은 입력값 검증 후 Room DB의 `routines` 테이블에 루틴을 저장합니다. 저장된 루틴은 Room `Flow`를 통해 홈 화면의 `저장된 루틴` 영역에 바로 표시됩니다. 홈 화면의 `수정` 버튼은 선택한 루틴을 루틴 등록 화면에 채우고, `삭제` 버튼은 해당 루틴을 Room DB에서 제거합니다.

개인 보정 시간과 안전 여유 시간은 입력값이 0~60분 범위로 유효할 때 Preferences DataStore에 저장됩니다. 기본 이동수단과 알림 사용 여부도 같은 DataStore에 저장됩니다. 앱을 다시 실행하면 `SettingsRepository`를 통해 마지막 설정을 읽어 루틴 등록 화면과 설정 화면의 기본값으로 반영합니다.
