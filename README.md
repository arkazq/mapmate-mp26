# MapMate

SoongSil University mobile programming team project

MapMate는 반복되는 출퇴근/등교 루틴을 기준으로 사용자가 언제 출발해야 하는지 계산해 주는 Android 앱입니다.

현재 프로젝트는 **Kakao Local API 기반 장소 검색/좌표 역지오코딩**, ODsay/Google Routes 기반 경로 시간 provider, 서울 버스/지하철 실시간 도착정보 provider, mock fallback, Room 기반 루틴/이동 기록 저장, DataStore 기반 설정 저장, WorkManager 기반 출발 전 재조회, AlarmManager 기반 출발 알림, Material 3 기반 출발 준비 대시보드와 루틴 관리 UI까지 구현한 상태입니다.

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
- 실제 도착 오차 기반 개인 보정 자동 업데이트
- 설정 화면
- DataStore 기반 개인 보정 시간 / 안전 여유 시간 저장
- DataStore 기반 알림 설정값 저장
- DataStore 기반 기본 이동수단 저장
- Android 13 이상 알림 권한 요청
- WorkManager 기반 출발 30분 전 경로 재조회
- AlarmManager 기반 다음 출발 알림 예약
- 부팅/앱 업데이트 후 출발 알림 재예약
- ODsay 첫 탑승 구간 기준 서울 버스/지하철 실시간 도착정보 조회 및 지연 보정
- ODsay / Google Routes / Mock 이동 시간 기반 권장 출발 시각 계산
- 실제 경로 API 실패 시 Mock 이동 시간 fallback
- 실제 경로 API 실패/좌표 누락/미지원 이동수단 fallback 상태 메시지 표시
- Room DB 기반 루틴 저장
- Room DB 기반 출발지 / 목적지 저장
- 입력 검증 오류 표시
- 루틴 저장 성공 상태 표시
- Material 3 기반 공통 카드, 하단 내비게이션, 아이콘 컴포넌트

## 김홍균/Codex 누적 작업 요약

이 섹션은 오늘 작업만이 아니라, 이 프로젝트에서 김홍균이 Codex로 진행해 `develop` 또는 현재 기능 브랜치에 반영한 주요 작업을 정리합니다.

- [PR #8](https://github.com/arkazq/mapmate-mp26/pull/8) `feature/routine-room-storage`
  - 루틴 저장을 mock/화면 상태가 아니라 Room DB 기반으로 전환
  - `RoutineRepository`, Room entity/DAO/mapper, 저장/조회 흐름 추가
  - 루틴 저장 관련 단위 테스트와 문서 정리
- [PR #9](https://github.com/arkazq/mapmate-mp26/pull/9) `feature/datastore-settings`
  - Preferences DataStore 기반 설정 저장 추가
  - 개인 보정 시간, 안전 여유 시간, 알림 사용 여부, 기본 이동수단 저장/복원
  - 설정 화면과 앱 상위 탭/내비게이션 흐름 정리
- [PR #11](https://github.com/arkazq/mapmate-mp26/pull/11) `feature/home`
  - 홈 탭을 앱 진입 화면으로 추가
  - Room `Flow`로 저장된 루틴을 홈/루틴 화면에 표시
  - 루틴 카드의 수정/삭제 동작 연결
  - 루틴 표시용 공통 UI/라벨 정리와 Kotlin 단위 테스트 정리
- [PR #16](https://github.com/arkazq/mapmate-mp26/pull/16) `feature/commute-history-storage`
  - `CommuteRecord` 도메인 모델과 Room `commute_records` 테이블 추가
  - 이동 기록 완료 시 루틴명, 출발지/목적지, 추천 출발 시각, 실제 도착 시각, 도착 오차 저장
  - 도착 오차를 기준으로 DataStore 개인 보정값을 자동 조정
  - 기록 완료 화면을 저장된 기록 기반으로 표시
  - 기록 탭에서 저장된 이동 기록 목록과 empty state 표시
  - 알림 설정이 켜져 있으면 저장된 루틴과 경로 예상 시간을 기준으로 다음 출발 알림을 AlarmManager에 예약
  - 출발 30분 전 WorkManager 작업을 예약해 경로 예상 시간을 다시 조회하고 알림을 재예약
  - ODsay 경로의 첫 탑승 구간에서 서울 버스/지하철 실시간 도착정보를 조회해 기본 예상 시간보다 대기가 길 때 지연분을 반영
  - `README.md`, `docs/FEATURE_STATUS.md`, `docs/ARCHITECTURE.md`, `docs/API_STRATEGY.md`에 구현 상태 반영
- [PR #18](https://github.com/arkazq/mapmate-mp26/pull/18) `codex/current-location-reverse-geocode`
  - Kakao Local API 좌표→주소 변환 endpoint를 `KakaoLocalApi`에 추가
  - `ReverseGeocodingProvider`와 `KakaoReverseGeocodingProvider`를 추가해 현재 위치 좌표를 읽기 쉬운 주소로 변환
  - 키 누락/호출 실패 시 기존 현재 위치 fallback 주소를 유지
  - `scripts/verify-local-apis.ps1`로 로컬 `local.properties` 기반 실제 Kakao/ODsay 호출 검증 가능
- `codex/api-status-messages`
  - `RouteEstimate`와 추천 UI 모델에 fallback 여부와 사용자용 상태 메시지 필드 추가
  - ODsay/Google Routes 실패 시 `API key 없음`, `좌표 없음`, `이동수단 미지원`, `경로 없음`, `요청 실패` 수준으로 사유를 요약
  - 루틴 등록 결과, 홈, 루틴 목록, 상세 예측, 이동 기록 화면에서 fallback 상태 메시지 표시
  - ODsay API 호출 없이 단위 테스트와 임시 clean copy 기준 `./gradlew.bat build`로 검증
- `codex/realtime-snapshot-fallback`
  - ODsay 첫 탑승 실시간 보정 성공 결과를 `RouteRealtimeSnapshot`으로 Room DB에 저장
  - 실시간 도착정보가 실패/매칭 실패하면 20분 이내의 마지막 성공 보정값만 재사용
  - 만료된 스냅샷은 조회 전 정리하고, 만료 후에는 ODsay 기본 예상 시간으로 fallback
  - 실제 API 호출 없이 fake provider 기반 단위 테스트로 저장/재사용/만료 흐름 검증
- `codex/departure-now-clamp`
  - 권장 출발 시각이 현재 시각보다 이미 지났고 도착 목표가 아직 남아 있으면 `지금 출발`로 표시
  - 루틴 등록, 홈, 루틴 목록, 상세 예측, 이동 기록 화면의 추천 출발 표시를 즉시 출발 상태에 맞게 정리
  - AlarmManager 예약도 오늘 도착 목표가 아직 남은 경우 다음 주로 넘기지 않고 즉시 알림으로 처리
  - 실제 API 호출 없이 계산기/알림 플래너/추천 UI 모델 단위 테스트로 검증

검증은 OneDrive 작업 폴더의 Gradle build 디렉터리 잠금 이슈를 피하기 위해 필요 시 OneDrive 밖 clean/temp copy에서 반복했습니다. API key와 `local.properties`는 Git에 포함하지 않는 것을 기준으로 확인했습니다.

## 아직 구현되지 않은 기능

- 버스 실시간 위치정보 기반 운행 상태 보조 판단
- 지하철 열차 위치정보 기반 운행 상태 보조 판단
- 전체 경로 API 응답 마지막 성공값 캐시
- Google Routes 도착 시각 기준 경로 조회
- 자동차 모드의 Google Routes traffic-aware 경로 조회
- 통계/기록 분석 화면

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
```

API 키가 없거나 호출이 실패해도 앱은 기존 Mock 데이터로 fallback되어 루틴 등록과 권장 출발 시각 계산 흐름을 계속 사용할 수 있습니다.

현재 ODsay 대중교통 길찾기 결과는 기본 예상 이동 시간으로 사용합니다. ODsay 응답에서 첫 탑승 구간이 버스이면 서울 버스도착정보조회 서비스, 지하철이면 서울 지하철 실시간 도착정보를 조회해 첫 대기 시간이 기본 대기 기준보다 길 때만 이동 시간을 보수적으로 늘립니다. 실시간 API 키가 없거나 호출/매칭에 실패하면 20분 이내의 `RouteRealtimeSnapshot`을 먼저 재사용하고, 없거나 만료되면 기존 ODsay/Google/Mock fallback 흐름을 유지합니다. 보정 후 권장 출발 시각이 이미 지났고 목표 도착 시각이 아직 남아 있으면 앱은 `지금 출발` 상태로 표시합니다. 전국 버스 확장과 TAGO 기반 정류장 매칭 같은 후속 전략은 `docs/API_STRATEGY.md`와 `docs/REALTIME_DEPARTURE_STRATEGY.md`를 확인합니다.

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
- `docs/FEATURE_STATUS.md`

## 팀원

- 20252752 류태현
- 20252751 김홍균
- 20253327 박성현
