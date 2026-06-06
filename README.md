# MapMate

SoongSil University mobile programming team project

MapMate는 반복되는 출퇴근/등교 루틴을 기준으로 사용자가 언제 출발해야 하는지 계산해 주는 Android 앱입니다.

현재 프로젝트는 **Kakao Local API 기반 장소 검색**, ODsay/Google Routes 기반 경로 시간 provider, mock fallback, Room 기반 루틴 저장, DataStore 기반 설정 저장, Material 3 기반 출발 준비 대시보드와 루틴 관리 UI까지 구현한 상태입니다.

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
→ 상세 예측 화면에서 계산 근거 확인
→ 이동 기록 UI 진행
→ 기록 완료 UI 확인
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
- 기록 탭 placeholder
- 설정 화면
- DataStore 기반 개인 보정 시간 / 안전 여유 시간 저장
- DataStore 기반 알림 설정값 저장
- DataStore 기반 기본 이동수단 저장
- ODsay / Google Routes / Mock 이동 시간 기반 권장 출발 시각 계산
- 실제 경로 API 실패 시 Mock 이동 시간 fallback
- Room DB 기반 루틴 저장
- Room DB 기반 출발지 / 목적지 저장
- 입력 검증 오류 표시
- 루틴 저장 성공 상태 표시
- Material 3 기반 공통 카드, 하단 내비게이션, 아이콘 컴포넌트

## 아직 구현되지 않은 기능

- 현재 위치 좌표의 주소 역지오코딩
- 버스 실시간 도착정보 기반 대기 시간 보정
- 버스 실시간 위치정보 기반 운행 상태 보조 판단
- 지하철 실시간 도착정보 기반 대기 시간 보정
- 지하철 열차 위치정보 기반 운행 상태 보조 판단
- 실제 API 실패 시 마지막 성공값 캐시
- Google Routes 도착 시각 기준 경로 조회
- 자동차 모드의 Google Routes traffic-aware 경로 조회
- AlarmManager 기반 출발 알림
- WorkManager 기반 출발 전 재조회
- 이동 기록 Room 저장
- 개인 보정값 업데이트
- 실제 저장된 이동 기록 목록
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
- Mock Provider
- JUnit

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
```

API 키가 없거나 호출이 실패해도 앱은 기존 Mock 데이터로 fallback되어 루틴 등록과 권장 출발 시각 계산 흐름을 계속 사용할 수 있습니다.

현재 ODsay 대중교통 길찾기 결과는 기본 예상 이동 시간으로 사용합니다. 다만 버스 지연, 도로 정체, 지하철 지연, 실제 정류장/역 도착 예정 시간을 완전히 보장하는 실시간 값은 아니므로, 실시간성을 높이려면 버스/지하철 실시간 도착정보 provider와 출발 전 재조회 작업을 추가해야 합니다. 자세한 확장 계획은 `docs/API_STRATEGY.md`를 확인합니다.

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
- `docs/FEATURE_STATUS.md`

## 팀원

- 20252752 류태현
- 20252751 김홍균
- 20253327 박성현
