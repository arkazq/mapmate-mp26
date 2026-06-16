# 모바일 보안 점검 체크리스트

이 문서는 MapMate Android 앱의 모바일 보안 점검 결과와 남은 운영 전 확인 항목을 정리합니다.

## 이번 브랜치에서 반영한 항목

- 루틴 등록의 도착 목표 시각 입력을 직접 문자열 입력에서 Material `TimePicker` 선택 방식으로 변경했습니다.
- 루틴명은 제어문자를 제거하고 한 줄 문자열로 정규화하며 최대 30자로 제한합니다.
- 출발지/목적지 검색어는 제어문자를 제거하고 한 줄 문자열로 정규화하며 최대 80자로 제한한 뒤 API 검색에 사용합니다.
- 루틴 등록과 설정 화면의 개인 보정/안전 여유 시간은 ASCII 숫자만 허용하고 최대 2자리로 제한합니다.
- 시간/보정값은 기존 ViewModel 검증을 유지해 UI 외부 이벤트가 들어와도 저장/계산 전에 다시 검증합니다.
- `android:allowBackup="false"`로 앱 자동 백업을 비활성화했습니다.
- Android 12 이상 데이터 추출 규칙에서 database, sharedpref, file 영역을 cloud backup과 device transfer 모두에서 제외했습니다.
- release 빌드에 `minifyEnabled=true`, `shrinkResources=true`를 적용했습니다.
- `network_security_config.xml`은 기본 cleartext를 차단하고, 필요한 공공 API 도메인에만 HTTP cleartext를 허용합니다.
- main 코드 기준 `Log`, `println`, `printStackTrace`를 통한 명시적 민감 로그 출력은 확인되지 않았습니다.
- `local.properties`, `.env`, keystore 계열 파일은 `.gitignore`에 포함되어 있으며 Git 추적 대상이 아닙니다.

## 현재 검증 결과

| 항목 | 상태 | 비고 |
| -- | -- | -- |
| 사용자 직접 입력값 제한 | 완료 | 시간은 TimePicker, 문자열은 길이/제어문자 제한, 보정값은 ASCII 숫자 제한 |
| Room/DataStore 자동 백업 차단 | 완료 | `allowBackup=false`, `data_extraction_rules.xml` 제외 규칙 적용 |
| release 축소/난독화 | 완료 | `assembleRelease`로 R8 minify와 리소스 shrink 통과 확인 |
| cleartext 통신 범위 | 완료 | 서울/TAGO 공공 API 도메인으로 제한 |
| 민감 파일 Git 추적 여부 | 완료 | `git ls-files` 기준 `local.properties`, `.env`, keystore 파일 없음 |
| 민감 로그 출력 | 완료 | main 코드에서 직접 로그 출력 패턴 없음 |

## 운영 전 남은 항목

- 현재 API 키는 `local.properties`에서 `BuildConfig`로 주입됩니다. Git에는 올라가지 않지만 APK에 포함된 키는 추출될 수 있으므로 운영 배포 전 API 콘솔에서 앱 제한과 API 제한을 설정해야 합니다.
- 외부에 노출되면 안 되는 키나 과금 위험이 큰 API는 백엔드 프록시를 통해 호출하는 방식을 검토해야 합니다.
- 로컬 `local.properties`가 화면 공유, 로그, 첨부 파일 등으로 노출된 적이 있으면 해당 키를 재발급해야 합니다.
- release 서명 keystore 생성, 보관 위치, 접근 권한, 백업 정책을 별도로 정해야 합니다.
- 위치, 루틴, 이동 기록, 구간별 시간 데이터는 개인정보로 취급하고 개인정보 처리방침/보관 기간/삭제 정책을 정리해야 합니다.
- 크래시 리포트나 분석 SDK를 도입할 경우 위치 좌표, API 키, 루틴명, 목적지명이 전송되지 않도록 마스킹 규칙을 추가해야 합니다.
- WebView를 도입하는 경우 JavaScript bridge, file access, mixed content 설정을 별도 점검해야 합니다.

## 검증 명령

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```
