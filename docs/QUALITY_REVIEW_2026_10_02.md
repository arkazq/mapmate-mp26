# 2026-10-02 앱 품질·사용성 점검

## 범위와 판단 기준

- 대상: `feature/app-quality-ux-improvements`, `origin/develop`의 `c57d618` 기준 변경.
- 핵심 흐름: 루틴 등록 → 추천/알림 → 이동 측정 → 완료/수정 → 다음 추천.
- 범용 지도 앱이나 자동 위치 추적은 추가하지 않았습니다. 네이버지도/카카오맵의 정보 우선순위와 익숙한 탐색 방식을 참고하되 출퇴근 앱의 기존 목적을 유지했습니다.
- 실제 API 호출 반복 없이 가짜 응답, 고정 시각, 로컬 DB, 오프라인 Pixel 8 에뮬레이터로 확인했습니다.
- 이 기록에서 `관찰됨`은 코드·테스트·화면으로 확인한 내용, `미확인`은 실기기나 운영 환경 검증이 남은 내용입니다.

## 수정 내용

| 영역 | 관찰된 문제 | 반영한 변경 | 직접 검증 |
|---|---|---|---|
| 화면 이동 | 화면 상태만 바꾸어 뒤로가기/복원 흐름이 불명확하고 여백이 중복됨 | Navigation Compose 스택, 화면별 ViewModel, 공통 하단 탐색/한 번의 inset 적용 | `MapMateNavigationTest` |
| 계층 경계 | 화면이 data 권한 구현체와 mock provider를 직접 참조함 | 권한 접근은 domain 인터페이스 주입, 등록 provider는 필수 인자로 전환 | 컴파일/계층 import 검사, `SettingsSystemAccessUiTest` |
| 출발 시각 | 화면별 계산과 실제 알림 정책 결과가 달라질 수 있음 | 공통 `ScheduledRouteProvider`/계산기/예약 coordinator를 통해 확정 시각 전파 | `DepartureRecommendationConsistencyTest` |
| 예약 경쟁 | 경로 조회 중 루틴 변경/완료/통지 후 오래된 결과가 돌아올 수 있음 | 영속 예약 fingerprint, 완료 제외, 통지 ledger 재검사, 조회/발행 잠금 분리 | `DepartureAlarmGuardTest` |
| 측정 복원 | 프로세스가 종료되면 진행 중 경로와 시각을 잃을 수 있음 | DataStore에 측정 경로·목표·시각 저장, 저장 성공 후 진행, 복원 시 재조회 없음 | `DataStoreTrackingSessionStoreTest`, `TrackingRestartTest` |
| 중복 완료 | 반복 클릭/동시 저장/저장 후 읽기 실패가 중복 기록 또는 잘못된 실패 표시로 이어짐 | 루틴+목표 epoch 중복 방지 트랜잭션, 저장 중 잠금, 저장과 후속 읽기 결과 분리 | `RoomCommuteRecordRepositoryTest`, `TrackingViewModelTest` |
| 구간 수정 | 변경하지 않은 구간까지 수동 수정으로 기록하고 여러 행이 부분 저장될 수 있음 | 저장소에서 변경 여부로 수정 표시 결정, 구간 소유권/시간 순서/새 기록의 ID 검증, 원자적 저장 | 저장소 및 `SegmentEditingUiTest` |
| 탑승 설명 | 놓칠 위험 상태에도 여유 있게 탈 수 있다고 설명할 수 있음 | 위험 경고와 중립적인 출발 권장 문구로 구분, 실시간 없음/0분 앞당김은 숨김 | `RouteBoardingAdviceTextTest` |
| 자정 경계 | HH:mm만 표시하거나 날짜 없이 수정하면 전날/다음 날을 혼동함 | 목표 이벤트 epoch 유지, 날짜+시간 선택, 출발/도착 날짜 표시 | 공통 완료 이벤트/추천 UI/구간 수정 테스트 |
| 개인 보정 | 전체 루틴 갱신이 동시 수정/삭제를 덮어쓰고 테스트성 완료가 학습됨 | 해당 루틴 buffer만 조건부 갱신, 전역 기본값 보존, 1분 미만 완료 제외 | `RoomRoutineBufferUpdateTest`, 측정 ViewModel 테스트 |
| 장소 입력 | 검색 응답 지연과 취소, 불명확한 mock 후보, 초안 유실 | 검색 debounce/취소 전파, 선택 좌표 검증, 명확한 빈/오류 상태, SavedStateHandle 초안 | 등록 ViewModel/화면 테스트 |
| 설정 | 숫자 입력 도중 바로 저장되고 저장 실패/권한 거부가 불명확함 | 숫자 초안과 명시 저장, 원자적 기본값 저장, OS 권한 상태/설정 이동 표시 | 설정 및 알림 접근 테스트 |
| 서울버스 ETA | 메시지와 시간 필드 조합이 가짜 차량 후보를 만들거나 운행종료가 유효 시간이 됨 | 1/2차 차량 필드 짝짓기, 무효 메시지 제외, 정류장 파싱 실패 시 기존 조회 대체 | 서울버스 provider 테스트 |
| 외부 API 실패 | 선택적 cache/학습 저장 실패가 정상 경로를 폐기하거나 취소를 fallback으로 삼음 | 선택 저장 실패와 핵심 조회 분리, coroutine 취소 전파 | provider/cache/구간 보정 테스트 |
| 실패 안내 | R8 릴리스에서 난독화된 구현 클래스명이 오류 문구에 노출됨 | 클래스명 대신 안정적인 한국어 설정/네트워크/경로 안내 | `FallbackRouteEstimateProviderTest`, 최적화 APK 화면 확인 |
| 요청 중복 | 한 ODsay 응답에서 같은 첫 버스 대상을 후보별로 반복 조회함 | 해당 응답 범위에서 성공/실패 조회 결과 공유 | ODsay provider 테스트 |
| DB 업데이트 | 2→8, 5→8 경로가 이미 만든 컬럼을 다시 추가하여 실패함 | 기존 6→7, 7→8에서 없는 컬럼만 추가, DB 버전 8 유지 | `MapMateDatabaseMigrationTest` 7개 |
| 네트워크/XML | TAGO 평문, XML 외부 선언, 크기 검사 전에 전체 응답을 버퍼링함 | TAGO HTTPS, 서울 두 정확한 호스트만 HTTP 예외, DTD/ENTITY 거부, 스트리밍/IO 읽기 문자 수 제한/응답 닫기 | `NetworkSecurityPolicyTest`, `SeoulBusXmlRuntimeTest`, `SeoulBusResponseBodyTest` |

## UI/UX

- 홈은 권장 출발 시각, 추천 버스, 남은 시간, 현재 장소를 우선 표시합니다. 진행 중 측정이 있으면 중복 시작 대신 이어하기를 제공합니다.
- 완료 후에는 오늘 완료/다음 출발 상태를 보여주고 정확히 완료한 목표 이벤트를 재측정하지 않습니다.
- 상세 예측은 실제 경로 구간을 표시합니다. 기본 목표 기준 출발과 버스 탑승 보정 후 출발을 서로 다른 값으로 설명합니다.
- 등록 단계와 일반 화면 구획은 불필요한 중첩 카드 없이 정리했습니다. 루틴 삭제와 작성 중 이탈은 확인을 거칩니다.
- 큰 글자에서 지표는 세로 배치하며 이동수단은 세로 선택으로 전환합니다. 구간 편집은 날짜와 시간을 명시적으로 선택하고, 수정 중인 구간 시간과 저장 전 전체 시간을 구분합니다.
- 기록은 실제 출발/도착 날짜를 보여주고 저장소 오류와 재시도 상태를 구분합니다.
- 측정 화면은 권장 시각보다 늦게 출발해도 된다는 표현을 피합니다. 완료 화면은 저장 사실을 안내하며, 짧은 시험 기록 등 제외 조건이 있는 학습 반영을 확약하지 않습니다.
- 밝은/어두운 테마, 좁은 화면, 1.5~2배 글자 화면을 캡처하여 겹침과 잘림을 확인했습니다. 이는 모든 기기/언어/화면 크기 검증을 의미하지 않습니다.

## 검증 결과

2026-10-02 로컬 최종 검사 기준입니다. 인증된 외부 API나 실기기 검증 결과로 확대 해석하지 않습니다.

| 검사 | 관찰된 결과 | 범위/한계 |
|---|---|---|
| `testDebugUnitTest` | 51개 suite, 237개 통과, 실패/오류/건너뛰기 0 | 가짜 응답·고정 시각·상태/정책 검증 |
| `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` | 통과 | lint/build가 사용자 흐름이나 실제 API 성공을 보장하지는 않음 |
| Room 마이그레이션 | 7개 통과 | 버전 1~7 합성 DB fixture의 자동 upgrade/전체 스키마, 기존 행과 NULL/기존 epoch 보존. 구형 APK 실기기 업데이트와는 구분 |
| 알림 접근 | 권한 거부/허용 각각 2개 통과 | AlarmManager의 정확/inexact window와 설정 진입 확인, 실제 알림 전달 시간은 미확인 |
| 측정 프로세스 재시작 | 준비→강제 종료→복원/저장→강제 종료→완료 유지 확인 | 별도 QA 에뮬레이터, 실제 휴대폰/OEM 정책 미확인 |
| 통합 Android 화면/저장소 검사 | 61개 통과 | 별도 QA 에뮬레이터만 사용, 마지막 측정/완료 문구 검사 포함 |
| `assembleRelease`, `lintRelease` | 통과 | R8/리소스 축소 확인, 운영 서명/배포 검증은 별도 |
| 최적화 APK 수동 실행 | 설정 저장/회전 초안/측정 재시작/완료 유지/실제 시스템 설정 진입 확인 | 오프라인 QA 루틴, debug 서명 복사본. 공개 배포/실제 이동 검증 아님 |
| `git diff --check` | 통과 | 비밀 파일/생성물/불필요한 변경도 별도 점검 |

lint는 오류 없이 종료했지만 경고 38개와 힌트 1개가 남아 있습니다. 주로 의존성 갱신, 사용하지 않는 리소스, 기존 target SDK 및 코드 스타일 항목이며 경고를 숨기거나 의존성을 일괄 갱신하지 않았습니다.

마이그레이션 중복 컬럼, 느린 조회가 통지를 막는 경쟁, 기록 소유권/수정 표시, 잘못된 탑승 확신 문구, XML 전체 본문 버퍼링, 실패 문구의 구현 클래스명 노출은 **수정 전 실패를 재현하고 수정 후 같은 검사를 통과**시켰습니다. 나머지 검사도 실행 결과만 근거로 기재합니다.

debug/release lint를 KSP 재생성과 한 명령에 묶었을 때 생성 파일을 찾지 못하는 오류가 한 번 발생했습니다. 아래처럼 debug와 release를 분리해 검사합니다. 앱 로직 실패나 검사 성공으로 잘못 분류하지 않습니다.

## 재검사 방법

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
.\gradlew.bat assembleRelease lintRelease
git diff --check
```

Android SDK `platform-tools/adb.exe`를 사용하고 대상은 테스트 전용 에뮬레이터로 지정합니다. `TrackingRestartTest`는 한 번에 실행하지 않고 준비/복원/완료 유지 메서드를 각각 실행하며 그 사이 `am force-stop com.mapmate`를 수행합니다. 다른 통합 검사는 이 클래스를 제외해 실행합니다.

주의: 전체 계측 검사는 테스트 데이터와 알림 예약을 변경할 수 있습니다. 사용자 기록이 있는 실기기에서 그대로 실행하지 않습니다. QA 로그/화면에는 실제 API 키나 개인 경로를 넣지 않습니다.

## 머지 전 검토

변경은 아직 커밋/공개하지 않았습니다. 다음 순서로 검토하면 화면 변경과 데이터 무결성 변경을 구분하기 쉽습니다.

1. 공통 domain 계산/완료 이벤트와 저장소 트랜잭션, 기존 DB upgrade 보존.
2. 예약 coordinator의 stale 입력/통지 중복/조회 경쟁과 OS 권한 거부 처리.
3. 측정 세션 복원, 구간 수정, 내비게이션과 화면별 확정 출발 시각 전파.
4. 외부 provider 취소/대체 처리와 ETA 파싱, 스트리밍/XML/HTTP 제한.
5. 홈·등록·기록·설정의 큰 글자/날짜/오류 상태 및 문서의 한계 표시.

실기기 알림 전달과 실제 API 매칭을 확인하기 전에는 에뮬레이터 검사 결과만으로 운영 배포를 승인하지 않습니다.

## 남은 한계

- 실제 휴대폰, OEM 절전, Doze 상태에서 T-30/T-15/T-10/T-5와 출발 알림을 끝까지 검증하지 않았습니다. WorkManager는 정확 시각 실행 도구가 아닙니다.
- 진행 중 측정은 이어하기로 진입하지만 홈의 다음 출발 안내가 측정 상태 전용 화면으로 바뀌는 것은 아닙니다. 측정과 향후 다른 출발 이벤트를 함께 표현하는 추가 사용성 검토가 필요합니다.
- 이번 점검에서 실제 서울/TAGO/ODsay 인증 및 지역별 정류장 매칭 성공을 새로 확인하지 않았습니다.
- 개인 buffer의 자동 학습은 전체 도착 오차 기반입니다. 준비 지연만 분리하는 학습, 요일/시간대 개인화, 구간 보정과의 이중 영향 분리는 후속 과제입니다.
- 구간 보정은 선택된 경로에 적용하며 각 후보의 접근 시간을 먼저 개인화하여 버스 slack을 다시 평가하지는 않습니다.
- 첫 버스 이후 전체 환승 차량/배차표 최적화, ODsay 후보 밖 주변 버스 직접 탐색은 범위 밖입니다.
- API 키는 여전히 BuildConfig/APK에 포함됩니다. 서울 HTTP 예외의 평문 전송 위험도 남아 있어 운영 배포 전 제한/중계/키 회전 검토가 필요합니다.
- XML 성공 응답은 읽기를 제한합니다. Retrofit의 HTTP 오류 본문 처리, 다른 JSON 응답 크기 및 전체 프로세스 메모리 상한까지 검증한 것은 아닙니다.
- 변경 범위가 넓으므로 머지 전 별도의 사람 검토가 필요합니다. 자동 검사 통과를 독립 코드 리뷰로 대체하지 않습니다.
- release 서명과 사용자 데이터 삭제/보관/개인정보 정책은 별도 확정이 필요합니다.

## 참고 자료

2026-10-02에 확인한 공식 자료입니다. UI 원칙과 OS 제약을 참고했으며 외부 API 실호출 증거가 아닙니다.

- [네이버지도 도움말](https://help.naver.com/service/5640/contents/20776?lang=ko&osType=MOBILE)
- [카카오맵 경로 화면](https://m.map.kakao.com/actions/routeView)
- [Android 알림 예약](https://developer.android.com/develop/background-work/services/alarms)
- [Android 14 정확한 알림 권한 변경](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [공공데이터 서울버스 도착정보](https://www.data.go.kr/data/15000314/openapi.do)
- [공공데이터 TAGO 버스도착정보](https://www.data.go.kr/data/15098530/openapi.do)
- [OWASP XML 외부 엔티티 방어 지침](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html)
