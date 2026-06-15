# MapMate

## Recent implementation update (2026-06-16)

This branch documents the current app behavior after the latest verification and fixes.

- Departure recheck work is scheduled before the planned departure time and no longer cancels the running recheck worker when it reschedules the next alarm.
- The home dashboard now derives the recommended departure time, countdown text, and progress from refreshed recommendation state instead of a fixed display value.
- After a departure alarm fires, the planner excludes the fired schedule so the same alarm is not immediately scheduled again.
- Records analysis can be filtered by routine, so aggregate statistics are no longer only global across all routines.
- ODsay transit paths are expanded into route segments for tracking. Bus and subway rides now include planned wait segments before the ride segment.
- Segment history optimization remains keyed by `routineId + segmentType + routeName + startName + endName`, so the same routine with a different bus route is learned separately.
- Segment adjustment storage now keeps average delay, average actual duration, min actual duration, max actual duration, sample count, confidence, and update time.
- The tracking flow can measure wait time explicitly. Completing a bus/subway wait segment starts the following ride segment automatically.

Current limitation: the app rechecks ODsay candidate paths and can apply first-bus realtime arrival adjustment, but it does not yet run a dedicated "which bus should I take" decision engine across all nearby alternatives. That work should be handled in a separate branch.

SoongSil University mobile programming team project

## 구간별 소요시간 개인 맞춤 최적화 업데이트

MapMate는 ODsay 대중교통 경로를 `RouteSegment` 단위로 나누고, 이동 기록 중 실제 구간별 소요시간을 측정할 수 있습니다. 이동 완료 후에는 저장된 구간 시간을 수동 수정할 수 있으며, 최근 기록은 `SegmentTimeAdjustment`로 학습되어 다음 추천 시간 계산에 반영됩니다.

현재 모델, UI 흐름, 대체 처리 정책, 한계는 [`docs/SEGMENT_TIME_OPTIMIZATION.md`](docs/SEGMENT_TIME_OPTIMIZATION.md)에 정리되어 있습니다.

MapMate는 반복되는 출퇴근/등교 루틴을 기준으로 사용자가 언제 출발해야 하는지 계산해 주는 Android 앱입니다.

현재 프로젝트는 **Kakao Local API 기반 장소 검색/좌표 역지오코딩**, ODsay/Google Routes 기반 경로 시간 provider, ODsay 후보 경로 랭킹, 출발 30분 이내 첫 버스 실시간 도착정보 보정, mock fallback, Room 기반 루틴/이동 기록 저장, DataStore 기반 설정 저장, WorkManager 기반 출발 전 재조회, AlarmManager 기반 출발 알림, Material 3 기반 출발 준비 대시보드와 루틴 관리 UI까지 구현한 상태입니다.

## 앱 목적

MapMate의 핵심 목적은 범용 지도 앱을 대체하는 것이 아니라, 반복되는 이동 루틴에서 다음 흐름을 안정적으로 연결하는 것입니다.

```text
루틴 등록
→ 설정 탭에서 기본값 관리
→ 출발지 검색 선택 또는 현재 위치 사용
→ 목적지 검색 선택
→ 실제 API 또는 Mock 이동 시간 조회
→ 권장 출발 시각 계산
→ DataStore에 보정/알림/기본 이동수단 설정 저장
→ Room DB에 루틴 저장
→ 홈 화면에서 오늘 권장 출발 시각 확인
→ WorkManager로 출발 전 경로 재조회 예약
→ AlarmManager로 다음 출발 알림 예약
→ 상세 예측 화면에서 계산 근거 확인
→ 이동 기록 UI 진행
→ Room DB에 이동 기록 저장
→ 기록 완료 UI 및 기록 탭에서 저장 기록 확인
```

## 현재 구현된 기능

- 루틴 등록 화면
- 홈 / 루틴 / 기록 / 설정 하단 내비게이션
- 홈 화면의 오늘 권장 출발 시각 대시보드
- 홈 화면의 출발까지 남은 시간, 추천 근거, 이동 시간/보정/안전 여유 요약 표시
- 루틴 목록 화면의 저장된 루틴 카드 표시
- 루틴 목록 화면의 루틴 수정 / 삭제
- 루틴 이름 입력
- 출발지 검색어 입력
- 출발지 후보 표시 및 선택
- 휴대폰 현재 위치 기반 출발지 선택
- Kakao 좌표→주소 API 기반 현재 위치 출발지 주소 표시
- 목적지 검색어 입력
- Kakao Local API 기반 목적지 후보 표시 및 선택
- API 키 누락 또는 API 실패 시 Mock 장소 후보 fallback
- 목표 도착 시각 입력
- 반복 요일 선택
- 이동 수단 선택: 대중교통, 도보, 자동차
- 개인 보정 시간 / 안전 여유 시간 입력
- 단계형 루틴 등록 UI
- 루틴 저장 성공 시 홈 화면 이동
- 상세 예측 화면
- 상세 예측 화면의 계산 근거와 경로 요약 표시
- 이동 기록 화면 UI
- 이동 기록 완료 별도 화면 UI
- 이동 기록 Room 저장
- 저장된 이동 기록 목록
- 기록 탭의 전체 기록 수, 평균 도착 오차, 정시/빠른 도착률, 최근 5회 평균 분석
- 실제 도착 오차 기반 개인 보정 자동 업데이트
- 설정 화면
- DataStore 기반 개인 보정 시간 / 안전 여유 시간 저장
- DataStore 기반 알림 설정값 저장
- DataStore 기반 기본 이동수단 저장
- Android 13 이상 알림 권한 요청
- WorkManager 기반 출발 30분 전 경로 재조회
- AlarmManager 기반 다음 출발 알림 예약
- 부팅/앱 업데이트 후 출발 알림 재예약
- ODsay 대중교통 후보 경로 최대 3개 평가 및 안정성 기준 기반 경로 선택
- 출발 예정 시각 30분 이내 ODsay 첫 버스 탑승 구간 기준 서울 버스/TAGO 실시간 도착정보 조회 및 지연 보정
- ODsay 첫 버스 탑승 좌표 기준 TAGO 정류소/도착정보 조회 및 전국 버스 대기 시간 보정 fallback
- 서울 버스 위치정보와 서울 지하철 열차 위치정보 기반 운행 상태 보조 설명
- ODsay / Google Routes / Mock 이동 시간 기반 권장 출발 시각 계산
- Google Routes 자동차 모드 `TRAFFIC_AWARE` 경로 시간 조회
- 실제 경로 API 실패 시 Mock 이동 시간 fallback
- 실제 경로 API 실패 시 6시간 이내 마지막 성공 경로 예상값을 Room 캐시에서 우선 재사용
- 실제 경로 API 실패/좌표 누락/미지원 이동수단 fallback 상태 메시지 표시
- Room DB 기반 루틴 저장
- Room DB 기반 출발지 / 목적지 저장
- 입력 검증 오류 표시
- 루틴 저장 성공 상태 표시
- Material 3 기반 공통 카드, 하단 내비게이션, 아이콘 컴포넌트

## 아직 구현되지 않은 기능

- Google Routes 도착 시각 기준 경로 조회
- 통계 기반 개인 보정 정책 고도화
- TAGO 정류소/노선 매칭의 실기기 API 검증 및 지역별 예외 보강
- 모바일 보안 점검: 민감 정보 Git 포함 여부, 위치/이동 기록 로그 노출, cleartext 통신 범위, release 빌드 보안 설정 확인

## 기술 스택

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel + StateFlow
- DataStore
- Room
- Retrofit2
- Kotlinx Serialization
- Android LocationManager
- Android AlarmManager
- Android WorkManager
- Android NotificationManager
- Mock Provider
- JUnit

## 실제 API 검증

실제 API 호출을 확인하려면 Git에 커밋되지 않는 `local.properties`에 필요한 키를 넣은 뒤 아래 명령을 실행합니다. 스크립트는 키 값을 출력하지 않고 존재 여부와 응답 요약만 표시합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local-apis.ps1
```

## 실행 방법

1. Android Studio에서 프로젝트 루트 `C:\dev\mp26_mapmate`를 엽니다.
2. Gradle Sync를 실행합니다.
3. Android Emulator를 실행합니다.
4. Android Studio의 Run 버튼으로 앱을 실행합니다.

빌드 확인은 Windows 터미널에서 다음 명령어를 사용합니다.

```cmd
gradlew.bat build
```

실제 API를 사용하려면 Git에 커밋되지 않는 `local.properties`에 필요한 키를 추가합니다.

```properties
KAKAO_REST_API_KEY=API_KEY_PLACEHOLDER
ODSAY_API_KEY=API_KEY_PLACEHOLDER
GOOGLE_ROUTES_API_KEY=API_KEY_PLACEHOLDER
SEOUL_OPEN_API_KEY=API_KEY_PLACEHOLDER
SEOUL_BUS_SERVICE_KEY=API_KEY_PLACEHOLDER
TAGO_SERVICE_KEY=API_KEY_PLACEHOLDER
```

API 키가 없거나 호출이 실패해도 앱은 기존 Mock 데이터로 fallback되어 루틴 등록과 권장 출발 시각 계산 흐름을 계속 사용할 수 있습니다.

현재 ODsay 대중교통 길찾기 결과는 기본 예상 이동 시간으로 사용합니다. ODsay 응답의 `path` 후보는 최대 3개까지 평가하며, 출발 예정 시각이 30분 이내인 경우에만 첫 버스 탑승 구간의 서울 버스도착정보조회 서비스 또는 TAGO 버스도착정보를 조회해 첫 대기 시간이 기본 대기 기준보다 길 때 이동 시간을 보수적으로 늘립니다. 후보 경로를 바꿔도 이득이 3분 미만이면 기존 ODsay 1순위 경로를 유지해 불필요한 경로 흔들림을 줄입니다. 실시간 조회가 허용된 상황에서 호출/매칭에 실패하면 20분 이내의 `RouteRealtimeSnapshot`만 재사용하고, 출발 예정 시각이 없거나 30분을 초과하면 실시간 조회와 snapshot fallback을 모두 사용하지 않습니다. 실시간 보정이 적용된 `RouteEstimate`는 일반 6시간 경로 cache에 저장하지 않습니다. 버스/지하철 위치정보는 계산값을 직접 대체하지 않고 운행 상태 보조 설명으로 reason에 반영합니다. 보정 후 권장 출발 시각이 이미 지났고 목표 도착 시각이 아직 남아 있으면 앱은 `지금 출발` 상태로 표시합니다. TAGO 정류소/노선 매칭의 지역별 예외는 실제 API 키 기반 검증으로 계속 보강합니다.

## 브랜치 전략 요약

- `main`: 발표/제출 가능한 안정 버전
- `develop`: 통합 개발 브랜치
- `feature/*`: 기능 개발 브랜치
- `docs/*`: 문서 작업 브랜치

자세한 Git 규칙은 `docs/MAPMATE_TEAM_GIT_GUIDE.md`를 따릅니다.

## 주요 문서

- `MAPMATE_CODEX_DEVELOPMENT_STANDARDS.md`
- `docs/MAPMATE_TEAM_GIT_GUIDE.md`
- `docs/ARCHITECTURE.md`
- `docs/API_STRATEGY.md`
- `docs/REALTIME_DEPARTURE_STRATEGY.md`
- `docs/SEGMENT_TIME_OPTIMIZATION.md`
- `docs/IMPLEMENTATION_UPDATE_2026_06_16.md`
- `docs/MOBILE_SECURITY_CHECKLIST.md`
- `docs/FEATURE_STATUS.md`

## 팀원

- 20252752 류태현
- 20252751 김홍균
- 20253327 박성현
