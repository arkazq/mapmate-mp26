# 모바일 보안 점검 체크리스트

이 문서는 MapMate Android 앱의 모바일 보안 점검 결과와 남은 운영 전 확인 항목을 정리합니다.

2026-10-02 기준: 로컬 방어 코드와 에뮬레이터 동작을 점검한 결과이며, 전체 침투 테스트나 운영 보안 인증을 의미하지 않습니다. API 키의 APK 포함과 서울 API의 HTTP 전송 위험은 남아 있습니다.

## 이번 브랜치에서 반영한 항목

- 루틴 등록의 도착 목표 시각 입력을 직접 문자열 입력에서 Material `TimePicker` 선택 방식으로 변경했습니다.
- 루틴명은 제어문자를 제거하고 한 줄 문자열로 정규화하며 최대 30자로 제한합니다.
- 출발지/목적지 검색어는 제어문자를 제거하고 한 줄 문자열로 정규화하며 최대 80자로 제한한 뒤 API 검색에 사용합니다.
- 루틴 등록과 설정 화면의 개인 보정/안전 여유 시간은 ASCII 숫자만 허용하고 최대 2자리로 제한합니다.
- 시간/보정값은 기존 ViewModel 검증을 유지해 UI 외부 이벤트가 들어와도 저장/계산 전에 다시 검증합니다.
- `android:allowBackup="false"`로 앱 자동 백업을 비활성화했습니다.
- Android 12 이상 데이터 추출 규칙에서 database, sharedpref, file 영역을 cloud backup과 device transfer 모두에서 제외했습니다.
- release 빌드에 `minifyEnabled=true`, `shrinkResources=true`를 적용했습니다.
- TAGO 정류소/도착정보를 HTTPS로 변경했습니다. `network_security_config.xml`은 기본 cleartext와 TAGO 평문을 차단하며, 서울 API 두 호스트에만 HTTP 예외를 남깁니다. 하위 도메인은 예외가 아닙니다.
- 서울버스 XML은 Retrofit `@Streaming`으로 전체 본문 버퍼링을 피하고 IO 스레드에서 최대 2,097,152자를 읽은 뒤 응답을 닫습니다. DTD/ENTITY와 외부 엔티티 해석도 차단합니다. 정상 한글/인코딩, 초과 응답과 악성 선언 거부를 로컬 검사했습니다. 실제 외부 API가 악성 XML을 반환하는 취약점을 재현했다는 의미는 아닙니다.
- 루틴/완료 이벤트/구간 소유권과 시간 순서를 저장소에서 재검증하고, 수동 구간 수정은 트랜잭션으로 처리합니다.
- main 코드 기준 `Log`, `println`, `printStackTrace`를 통한 명시적 민감 로그 출력은 확인되지 않았습니다.
- `local.properties`, `.env`, keystore 계열 파일은 `.gitignore`에 포함되어 있으며 Git 추적 대상이 아닙니다.

## 현재 검증 결과

| 항목 | 상태 | 비고 |
| -- | -- | -- |
| 사용자 직접 입력값 제한 | 완료 | 시간은 TimePicker, 문자열은 길이/제어문자 제한, 보정값은 ASCII 숫자 제한 |
| Room/DataStore 자동 백업 차단 | 완료 | `allowBackup=false`, `data_extraction_rules.xml` 제외 규칙 적용 |
| release 축소/난독화 | 완료 | `assembleRelease`로 R8 minify와 리소스 shrink 통과 확인 |
| cleartext 통신 범위 | 부분 보강 | TAGO HTTPS 및 평문 차단, 서울 두 호스트 HTTP 위험은 잔존. `NetworkSecurityPolicyTest`로 허용 범위 확인 |
| 서울버스 XML 방어 | 로컬 검증 | 스트리밍/읽기 문자 수 제한/응답 닫기, DTD/ENTITY 거부, 외부 resolver 차단. JSON 응답 전체와 HTTP 오류 본문의 크기 제한은 별도 검토 필요 |
| 민감 파일 Git 추적 여부 | 완료 | `git ls-files` 기준 `local.properties`, `.env`, keystore 파일 없음 |
| 민감 로그 출력 | 완료 | main 코드에서 직접 로그 출력 패턴 없음 |

## 운영 전 남은 항목

- 현재 API 키는 `local.properties`에서 `BuildConfig`로 주입됩니다. Git에는 올라가지 않지만 APK에 포함된 키는 추출될 수 있으므로 운영 배포 전 API 콘솔에서 앱 제한과 API 제한을 설정해야 합니다.
- 서울 API HTTP 예외에서는 인증키/위치 정보가 평문 통신에 포함될 수 있습니다. HTTPS 지원 또는 백엔드 중계 검증 전에는 안전한 공개 배포라고 간주하지 않습니다.
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
.\gradlew.bat lintDebug lintRelease
```

Android 계측 검사는 별도 QA 에뮬레이터에서만 실행합니다. 알림 예약과 테스트 데이터 저장을 수행하므로 사용자 기록이 있는 휴대폰에서 전체 검사를 실행하지 않습니다. 검증 범위는 [품질 점검 기록](QUALITY_REVIEW_2026_10_02.md)에 정리합니다.
