# MapMate 문서

이 폴더는 아키텍처, API 전략, 실시간 출발 보정, 구간별 소요시간 최적화, 보안 점검, 기능 상태 문서를 모아 둔 위치입니다.

## 현재 업데이트

- [구현 업데이트 - 2026-06-16](IMPLEMENTATION_UPDATE_2026_06_16.md)

현재 브랜치를 검토할 때는 이 파일을 먼저 확인합니다. 주요 내용은 다음과 같습니다.

- 출발 전 재조회와 알림 반복 예약 수정
- 홈 UI 추천 상태 동기화
- 루틴별 기록 분석
- 대중교통 대기 구간 측정
- 구간 보정값의 최소/최대 실제 소요시간 저장
- 첫 버스 탑승 가능성 기반 후보 경로 랭킹
- 홈/상세 예측의 탑승 판단 UI
- 향후 주변 버스 직접 탐색 범위

## 주요 문서

- [아키텍처](ARCHITECTURE.md)
- [API 전략](API_STRATEGY.md)
- [실시간 출발 전략](REALTIME_DEPARTURE_STRATEGY.md)
- [구간별 소요시간 최적화](SEGMENT_TIME_OPTIMIZATION.md)
- [기능 상태](FEATURE_STATUS.md)
- [모바일 보안 점검 체크리스트](MOBILE_SECURITY_CHECKLIST.md)
- [팀 Git 가이드](MAPMATE_TEAM_GIT_GUIDE.md)
