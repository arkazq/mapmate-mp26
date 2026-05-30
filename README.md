# MapMate

SoongSil University mobile programming team project

MapMate는 반복되는 출퇴근/등교 루틴을 기준으로 사용자가 언제 출발해야 하는지 계산해 주는 Android 앱입니다.

현재 프로젝트는 실제 지도 API나 DB 저장을 붙이기 전, **mock 기반 MVP foundation**을 먼저 구현한 상태입니다.

## 앱 목적

MapMate의 핵심 목적은 범용 지도 앱을 대체하는 것이 아니라, 반복되는 이동 루틴에서 다음 흐름을 안정적으로 연결하는 것입니다.

```text
루틴 등록
→ Mock 목적지 검색
→ Mock 이동 시간 조회
→ 권장 출발 시각 계산
→ 루틴 저장 성공 상태 표시
```

## 현재 구현된 기능

- 루틴 등록 화면
- 루틴 이름 입력
- 목적지 검색어 입력
- Mock 목적지 후보 표시 및 선택
- 목표 도착 시각 입력
- 반복 요일 선택
- 이동 수단 선택: 대중교통, 도보, 자동차
- 개인 보정 시간 / 안전 여유 시간 입력
- Mock 이동 시간 기반 권장 출발 시각 계산
- 입력 검증 오류 표시
- 루틴 저장 성공 상태 표시

## 아직 구현되지 않은 기능

- Room DB 저장
- DataStore 설정 저장
- 실제 Kakao Local API 연동
- 실제 ODsay API 연동
- 실제 Google Routes API 연동
- AlarmManager 기반 출발 알림
- WorkManager 기반 출발 전 재조회
- 이동 기록 저장
- 개인 보정값 업데이트
- 홈 화면, 통계 화면, 설정 화면

## 기술 스택

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel + StateFlow
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
