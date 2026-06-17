# MapMate

## 최근 구현 업데이트 (2026-06-16)

이 문서는 최근 검증과 수정 이후의 현재 앱 동작을 정리합니다.

- 출발 전 재조회 작업은 권장 출발 시각보다 먼저 예약되며, 재조회 worker가 다음 알림을 다시 잡을 때 실행 중인 자기 자신을 취소하지 않습니다.
- 홈 대시보드는 고정 표시값이 아니라 새로 계산된 추천 상태에서 권장 출발 시각, 남은 시간, 진행률을 표시합니다.
- 출발 알림이 울린 뒤에는 방금 울린 출발 이벤트를 제외하고 다음 반복 알림을 계산합니다.
- 기록 분석은 전체 기록뿐 아니라 루틴별로도 필터링할 수 있습니다.
- ODsay 대중교통 경로는 `RouteSegment`로 분해되며, 버스/지하철 탑승 전 계획 대기 구간도 별도 구간으로 다룹니다.
- 구간 기록 학습은 `routineId + segmentType + routeName + startName + endName` 기준으로 저장되어 같은 루틴이라도 다른 노선은 별도로 학습합니다.
- 구간 보정값은 평균 지연, 평균 실제 소요시간, 최소/최대 실제 소요시간, 표본 수, 신뢰도, 갱신 시각을 저장합니다.
- 이동 기록 화면은 대기 시간을 명시적으로 측정할 수 있고, 버스/지하철 대기 구간 완료 시 다음 탑승 구간을 자동으로 시작합니다.
- 홈과 상세 예측 화면은 첫 버스 탑승 가능성 기반 후보 랭킹 결과를 사용자용 카드로 표시합니다.
- 첫 버스를 타려면 기존 권장 출발 시각보다 더 일찍 나가야 하는 경우, 목표 도착 시각을 우선해 안전 탑승 출발 시각으로 홈/상세/알림 시각을 앞당깁니다.
- 홈 대시보드는 저장 순서가 아니라 현 시간 기준 가장 가까운 다음 출발 루틴을 메인 추천으로 표시하고, 저장된 모든 루틴을 별도 목록으로 함께 보여줍니다.
- 홈과 상세 예측 화면의 권장 출발 시각은 출발 알림 보정 정책과 같은 기준으로 표시해 알림 시각과 화면 시각이 어긋나지 않도록 맞춥니다.
- 출발 30분 이내에는 설정에서 켜고 끌 수 있는 출발 전 상태 알림을 표시하며, T-30/T-15/T-10/T-5 재조회 결과로 권장 출발 시각이 바뀌면 같은 알림을 갱신합니다.
- 루틴 등록의 계산 결과는 API 내부 응답 문자열을 그대로 보여주지 않고 사용자용 요약 문장으로 표시합니다.
- 루틴 등록의 도착 목표 시각은 직접 문자열 입력 대신 시간 선택기로 설정하며, 루틴명/장소 검색어/보정값 입력은 길이와 허용 문자 범위를 제한합니다.
- 앱 백업과 Android 12 이상 데이터 추출/기기 이전에서 Room DB, DataStore, 앱 파일을 제외하고, release 빌드는 축소/난독화와 리소스 shrink를 적용합니다.
- TAGO 경기/전국 버스 매칭은 ODsay 노선명에 붙는 괄호 업체명 표기를 제거해 `11(남양여객)`과 TAGO `11` 같은 표기 차이를 같은 노선으로 처리합니다.

현재 한계: 앱은 ODsay 후보 경로 안에서 첫 버스 탑승 가능성을 평가하지만, ODsay 결과 밖의 주변 버스 대안을 직접 탐색하지는 않습니다. 서울 버스 도착정보는 ODsay `startArsID`가 있으면 정류장 ARS 기준 도착목록에서 노선번호를 매칭하고, 실패하면 기존 `busRouteId` 조회로 fallback합니다. 알림 화면의 대안 경로 설명 UI는 후속 작업입니다.

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
- 홈 화면의 현 시간 기준 다음 출발 루틴 메인 표시
- 홈 화면의 저장된 전체 루틴 요약 목록
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
- 목표 도착 시각 TimePicker 선택
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
- DataStore 기반 출발 전 상태 알림 설정값 저장
- DataStore 기반 기본 이동수단 저장
- Android 13 이상 알림 권한 요청
- WorkManager 기반 출발 30분 전 경로 재조회
- AlarmManager 기반 다음 출발 알림 예약
- 출발 30분 이내 권장 출발 시각 상태 알림 표시 및 재조회 결과 갱신
- 부팅/앱 업데이트 후 출발 알림 재예약
- ODsay 대중교통 후보 경로 최대 5개 평가 및 첫 버스 탑승 가능성 기반 경로 선택
- 출발 예정 시각 30분 이내 ODsay 첫 버스 탑승 구간 기준 서울 버스/TAGO 실시간 도착정보 조회, 지연 보정, 탑승 여유 시간 기반 후보 랭킹
- 첫 버스 도착 예정 시각과 정류장 접근 시간을 역산한 안전 탑승 출발 시각 반영
- 홈 화면의 첫 버스 추천 요약 카드
- 상세 예측 화면의 탑승 판단 카드 및 대안 후보 표시
- 루틴 등록 계산 결과의 사용자용 요약 문구 표시
- ODsay 첫 버스 탑승 좌표 기준 TAGO 정류소/도착정보 조회 및 전국 버스 대기 시간 보정 fallback
- TAGO 노선번호 매칭 시 괄호/대괄호 업체명 표기 제거
- 서울 버스 위치정보와 서울 지하철 열차 위치정보 기반 운행 상태 보조 설명
- ODsay / Google Routes / Mock 이동 시간 기반 권장 출발 시각 계산
- Google Routes 자동차 모드 `TRAFFIC_AWARE` 경로 시간 조회
- 실제 경로 API 실패 시 Mock 이동 시간 fallback
- 실제 경로 API 실패 시 6시간 이내 마지막 성공 경로 예상값을 Room 캐시에서 우선 재사용
- 실제 경로 API 실패/좌표 누락/미지원 이동수단 fallback 상태 메시지 표시
- Room DB 기반 루틴 저장
- Room DB 기반 출발지 / 목적지 저장
- 입력 검증 오류 표시
- 루틴명/장소 검색어 제어문자 제거 및 길이 제한
- 보정 시간 ASCII 숫자 입력 제한
- 루틴 저장 성공 상태 표시
- 앱 백업 비활성화 및 데이터 추출 제외
- release 빌드 코드 축소/난독화와 리소스 shrink 적용
- Material 3 기반 공통 카드, 하단 내비게이션, 아이콘 컴포넌트

## 아직 구현되지 않은 기능

- Google Routes 도착 시각 기준 경로 조회
- 통계 기반 개인 보정 정책 고도화
- TAGO 정류소/노선 매칭의 실기기 API 검증 및 지역별 예외 보강
- 운영 배포 전 API 키 제한/백엔드 프록시 검토
- release 서명/keystore 관리 정책 정리

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

API 키는 개발 편의를 위해 `local.properties`에서 `BuildConfig`로 주입합니다. 이 방식은 Git 커밋을 막는 용도에는 적합하지만, Android APK에 포함된 키는 추출될 수 있습니다. 운영 배포 전에는 각 API 콘솔에서 앱 제한/API 제한을 설정하고, 외부에 노출되면 안 되는 키는 백엔드 프록시를 통해 호출하는 방식을 검토해야 합니다. `.env`는 이 프로젝트의 Android/Gradle 흐름에서는 보안상 이점을 주지 않으므로 기본 키 파일로 사용하지 않습니다.

현재 ODsay 대중교통 길찾기 결과는 기본 예상 이동 시간으로 사용합니다. ODsay 응답의 `path` 후보는 최대 5개까지 평가하며, 출발 예정 시각이 30분 이내인 경우에만 첫 버스 탑승 구간의 서울 버스도착정보조회 서비스 또는 TAGO 버스도착정보를 조회합니다. `RouteCandidateEvaluator`는 정류장까지 접근 시간과 실시간 버스 도착 대기시간을 비교해 탑승 여유가 부족한 후보에 페널티를 주고, 총 소요시간/환승/도보/실시간 신뢰도와 목표 도착 가능성을 함께 점수화합니다. 첫 버스를 타려면 기존 권장 출발 시각보다 더 일찍 나가야 하는 경우에는 `버스 도착 시각 - 정류장 접근 시간 - 최소 탑승 여유 3분`으로 안전 탑승 출발 시각을 계산하고, 목표 도착 시각을 놓치지 않는 쪽을 우선해 홈/상세/알림 시각에 반영합니다. 후보 경로를 바꿔도 이득이 3분 미만이면 기존 ODsay 1순위 경로를 유지해 불필요한 경로 흔들림을 줄입니다. 실시간 조회가 허용된 상황에서 호출/매칭에 실패하면 20분 이내의 `RouteRealtimeSnapshot`만 재사용하고, 출발 예정 시각이 없거나 출발까지 30분을 초과하거나 이미 지난 출발 이벤트이면 실시간 조회와 snapshot fallback을 모두 사용하지 않습니다. 실시간 보정이 적용된 `RouteEstimate`는 일반 6시간 경로 cache에 저장하지 않습니다. 버스/지하철 위치정보는 계산값을 직접 대체하지 않고 운행 상태 보조 설명으로 reason에 반영합니다. 홈 화면은 선택된 첫 버스와 탑승 여유를 요약 카드로 보여주고, 상세 예측 화면은 추천 후보와 대안 후보를 `탑승 판단` 카드로 보여줍니다. 루틴 등록 계산 결과는 API 내부 reason 문자열 대신 이동 시간과 보정값을 요약한 사용자용 문장을 표시합니다. 보정 후 권장 출발 시각이 이미 지났고 목표 도착 시각이 아직 남아 있으면 앱은 `지금 출발` 상태로 표시합니다. 홈의 남은 출발 시간은 60분 이상일 때 `k시간 l분` 형식으로 표시합니다. 홈 메인 추천과 이동 기록 시작 대상은 저장 순서가 아니라 현재 시각 기준 가장 가까운 다음 출발 루틴을 따릅니다. 출발 전 상태 알림은 출발 30분 이내에 권장 출발 시각을 알림창에 표시하고, 재조회 결과가 바뀌면 같은 알림을 갱신합니다. TAGO 정류소/노선 매칭은 괄호 업체명 제거를 포함해 보강했으며, 서울 버스는 ODsay `startArsID`와 노선번호를 우선 사용해 정류장 도착목록을 조회하고 실패 시 기존 `busRouteId` 조회로 fallback합니다.

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
