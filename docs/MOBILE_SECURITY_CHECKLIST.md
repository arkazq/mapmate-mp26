# 모바일 보안 점검 체크리스트

이 문서는 MapMate의 Android 보안 이슈를 나중에 별도로 점검하기 위한 기준입니다. 현재 구현 완료 항목이 아니라, 출시 전 또는 주요 기능 병합 전 확인해야 할 보안 점검 목록입니다.

## 점검 범위

- API key, keystore, `local.properties`, `.env` 같은 민감 정보가 Git에 포함되지 않는지 확인합니다.
- Retrofit/OkHttp 요청 로그에 API key, 위치 좌표, 사용자의 루틴 정보가 노출되지 않는지 확인합니다.
- `network_security_config.xml`에서 cleartext HTTP 허용 범위가 서울/TAGO 공공 API 등 필요한 endpoint로만 제한되어 있는지 확인합니다.
- Room DB에 저장되는 루틴, 목적지, 출발지, 이동 기록, 구간별 시간 데이터가 개인정보로 취급되어야 하는지 검토합니다.
- 앱 백업, 디버그 빌드, 로그캣, 크래시 리포트에 위치/이동 기록이 남지 않는지 확인합니다.
- 위치 권한과 알림 권한 요청 문구가 실제 사용 목적과 일치하는지 확인합니다.
- 외부 API 실패 시 fallback 메시지가 내부 오류, endpoint, key 상태를 과도하게 노출하지 않는지 확인합니다.
- WebView를 도입하는 경우 JavaScript bridge, file access, mixed content 설정을 별도로 점검합니다.
- release 빌드에서 `debuggable=false`, 난독화/축소 설정, 서명 설정, API key 주입 방식을 확인합니다.

## 우선순위

1. 민감 정보 Git 포함 여부 확인
2. 위치/이동 기록 로그 노출 여부 확인
3. cleartext 통신 허용 범위 확인
4. Room 저장 데이터의 개인정보 취급 기준 정리
5. release 빌드 보안 설정 확인

## 현재 상태

- 모바일 보안 점검은 아직 별도 구현/검증 단계에 들어가지 않았습니다.
- 추후 보안 점검 브랜치에서 체크리스트 항목별로 확인 결과를 업데이트합니다.
