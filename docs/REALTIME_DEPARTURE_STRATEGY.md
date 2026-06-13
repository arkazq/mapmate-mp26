# MapMate Realtime Departure Strategy

이 문서는 MapMate의 실시간 출발 시각 보정 개발 기준을 정리합니다. 구현 코드가 아니라, 앞으로 PR에서 따라야 할 설계 기준입니다.

## 핵심 방향

MapMate는 루틴 등록 또는 조회 시점에는 ODsay 대중교통 길찾기 결과로 기본 이동 시간을 계산하고, 출발 시간이 가까워졌을 때 TAGO 버스 실시간 도착정보로 첫 버스 대기 시간을 보정합니다. 보정 결과로 출발 시각이 바뀌면 최종 출발 알림을 다시 예약합니다.

```text
ODsay = 기본 대중교통 경로와 예상 이동 시간
TAGO = 전국 버스 실시간 도착정보 보정
WorkManager = 출발 전 재조회 작업
AlarmManager = 최종 출발 알림
RouteRealtimeSnapshot = 특정 시점의 실시간 보정 결과
```

Gemini 같은 LLM은 현재 필수 기능이 아닙니다. 추천 시각 계산과 개인보정 계산은 API 응답, 실제 이동 기록, 명시적인 계산식으로 처리합니다.

## 전체 처리 흐름

루틴 저장 또는 조회 시점:

```text
Kakao Local API로 출발지/목적지 좌표 확보
→ ODsay 대중교통 길찾기로 기본 경로/이동 시간 조회
→ 초기 권장 출발 시각 계산
→ 기본 출발 알림 예약
→ 출발 전 재조회 작업 예약
```

출발 시간이 가까워진 시점:

```text
WorkManager 실행
→ ODsay 경로 재조회
→ 첫 버스 구간 추출
→ TAGO 버스정류소정보로 cityCode/nodeId 후보 매칭
→ TAGO 버스도착정보로 arrtime 조회
→ 실시간 대기시간 기반 이동 시간 보정
→ RouteRealtimeSnapshot 저장
→ 최종 출발 시각 재계산
→ AlarmManager 알림 재예약
```

## 출발 시각 계산

초기 출발 시각:

```text
initialDepartureTime
= targetArrivalTime
- baseRouteDurationMinutes
- personalBufferMinutes
- safetyMarginMinutes
```

실시간 보정 후 출발 시각:

```text
finalDepartureTime
= targetArrivalTime
- adjustedRouteDurationMinutes
- personalBufferMinutes
- safetyMarginMinutes
```

실시간 보정 결과가 이미 지난 시간이면 그대로 표시하지 않습니다.

```kotlin
val finalDepartureTime =
    if (recalculatedDepartureTime.isBefore(now)) {
        now
    } else {
        recalculatedDepartureTime
    }
```

UI에서는 이 상태를 "지금 출발 권장"으로 처리합니다.

## 보정 결과 저장 위치

`adjustedRouteDurationMinutes`는 `Routine`에 직접 저장하지 않습니다.

- `Routine`: 사용자의 고정 루틴 정보
- `RouteRealtimeSnapshot`: 특정 재조회 시점의 실시간 보정 결과

권장 모델:

```kotlin
data class RouteRealtimeSnapshot(
    val routineId: Long,
    val baseRouteDurationMinutes: Int,
    val adjustedRouteDurationMinutes: Int?,
    val realtimeWaitingMinutes: Int?,
    val delayAdjustmentMinutes: Int?,
    val providerName: String?,
    val matchedStationName: String?,
    val matchedRouteNo: String?,
    val confidence: Double,
    val checkedAt: LocalDateTime,
    val status: RealtimeStatus,
)

enum class RealtimeStatus {
    NOT_CHECKED,
    APPLIED,
    LOW_CONFIDENCE_FALLBACK,
    API_FAILED_FALLBACK,
    NO_ARRIVAL_INFO_FALLBACK,
    STALE_SNAPSHOT_FALLBACK,
}
```

일별 추천 결과까지 저장해야 하면 `DailyRecommendationSnapshot` 같은 별도 모델을 둡니다.

## 버스 실시간 보정

전국 지원 관점에서는 TAGO 버스 API를 기본 provider로 둡니다.

사용 API:

- TAGO 버스정류소정보 API: 좌표 기반 근접 정류소 조회, `cityCode`, `nodeId` 후보 확보
- TAGO 버스도착정보 API: `cityCode + nodeId` 기준 도착정보 조회, `arrtime` 확보

보정 흐름:

```text
ODsay 경로 결과에서 첫 버스 탑승 구간 추출
→ 정류장명, 정류장 좌표, 노선번호 확보
→ ODsay 첫 탑승 정류장 좌표 기준으로 TAGO 근접 정류소 조회
→ cityCode, nodeId 후보 확보
→ TAGO 버스도착정보 API 호출
→ ODsay 노선정보와 TAGO 도착정보 매칭
→ arrtime을 실시간 첫 탑승 대기시간으로 사용
→ 기본 이동 시간 보정
```

정밀 보정식:

```text
delayAdjustmentMinutes
= realtimeWaitingMinutes
- odsayBaseWaitingMinutes

adjustedRouteDurationMinutes
= baseRouteDurationMinutes
+ delayAdjustmentMinutes
```

1차 구현에서 ODsay 기본 대기시간을 명확히 분리하기 어렵다면 정밀 보정 대신 보수적 추가 보정만 적용합니다. 실시간 첫 버스 대기시간이 임계값 이상일 때만 제한된 범위의 추가 보정을 적용하고, 이상 응답 하나로 알림이 과도하게 앞당겨지지 않도록 보정값 상한을 둡니다.

## ODsay 세부 경로 파싱

현재 기본 `totalTime`만으로는 첫 버스 구간의 대기시간을 정밀하게 분리하기 어렵습니다. 실시간 보정을 구현하려면 ODsay DTO를 세부 경로까지 확장해야 합니다.

필요한 필드 후보:

- `subPath`
- `trafficType`
- `sectionTime`
- `stationStart`
- `stationEnd`
- `startX`, `startY`
- `endX`, `endY`
- `lane`
- `busNo`
- `busId`
- 정류장 수

1차 구현 범위는 첫 버스 탑승 구간 1개 보정으로 제한합니다. 환승 버스 전체 보정은 후순위입니다.

## 정류장/노선 매칭

TAGO 매칭은 `routeno` 단순 비교만으로 처리하지 않습니다. 같은 노선번호, 상하행 반대편 정류장, 근처의 다른 정류장, 표기 차이로 오매칭이 발생할 수 있습니다.

매칭 신뢰도 기준:

1. ODsay 첫 정류장 좌표와 TAGO 정류장 거리
2. ODsay 정류장명과 TAGO 정류장명 유사도
3. ODsay 노선번호와 TAGO `routeno` 정규화 결과
4. 가능하면 방향 또는 다음 정류장 정보

예시 모델:

```kotlin
data class StationMatchScore(
    val station: TagoStation,
    val distanceScore: Double,
    val nameSimilarityScore: Double,
    val routeNoScore: Double,
    val directionScore: Double,
    val totalScore: Double,
)
```

매칭 신뢰도가 낮으면 실시간 보정을 적용하지 않고 ODsay 기본 예상시간을 사용합니다.

## 정류장 검색 기준

집 좌표 또는 출발지 좌표 기준으로 TAGO 근접 정류소를 바로 검색하지 않습니다.

```text
집 좌표 기준 검색: 사용하지 않음
ODsay 첫 탑승 정류장 좌표 기준 검색: 사용
```

ODsay가 이미 사용자가 실제로 걸어갈 첫 탑승 정류장을 선택해주므로, TAGO는 그 정류장을 `cityCode/nodeId`로 매칭하는 용도로만 사용합니다.

## WorkManager와 AlarmManager

역할은 분리합니다.

- `WorkManager`: 출발 전 사전 재조회 작업
- `AlarmManager`: 기본 출발 알림, 최종 출발 알림, 재예약

`WorkManager`는 정확한 시각 실행용 타이머로 보지 않습니다. 최종 알림 시각은 `AlarmManager`가 담당합니다.

권한 고려:

- Android 12 이상: `SCHEDULE_EXACT_ALARM` 또는 inexact alarm fallback
- Android 13 이상: `POST_NOTIFICATIONS`

## Snapshot 유효기간

마지막 성공 보정값은 유효기간 안에서만 fallback으로 사용합니다.

```kotlin
fun isFreshSnapshot(
    snapshot: RouteRealtimeSnapshot,
    nowEpochMillis: Long,
): Boolean {
    return snapshot.expiresAtEpochMillis >= nowEpochMillis
}
```

현재 구현은 20분 TTL을 사용합니다. 유효기간이 지난 스냅샷은 조회 전에 정리하고 ODsay 기본 예상시간을 사용합니다.

## Fallback 기준

실시간 정보 실패는 정상 케이스로 처리합니다.

| 상황 | 처리 |
|---|---|
| TAGO 정류장 매칭 실패 | ODsay 기본 시간 사용 |
| TAGO 도착정보 없음 | ODsay 기본 시간 사용 |
| 내 노선이 도착정보 목록에 없음 | ODsay 기본 시간 사용 |
| 매칭 신뢰도 낮음 | ODsay 기본 시간 사용 |
| 재조회 지연 | fresh한 마지막 성공 보정값 또는 ODsay 기본 시간 사용 |
| 마지막 성공 보정값 오래됨 | `STALE_SNAPSHOT_FALLBACK`, ODsay 기본 시간 사용 |
| 네트워크/API 장애 | fresh한 마지막 성공 보정값 또는 ODsay 기본 시간 사용 |

UI 문구 예:

```text
실시간 정보를 확인하지 못해 기본 예상시간 기준으로 알려드립니다.
실시간 교통 정보를 반영하면 지금 출발하는 것이 좋습니다.
```

## 지하철 실시간 정보

지하철 실시간 provider는 1차 구현에서 제외합니다.

- 전국 공통 지하철 실시간 API로 깔끔하게 처리하기 어렵습니다.
- 지역별 API 매칭 로직이 별도로 필요합니다.
- 버스 TAGO 보정만으로도 핵심 실시간 보정 흐름을 검증할 수 있습니다.

1차 구현에서는 지하철 구간은 ODsay 기본 예상시간을 유지합니다. 이후 서울권은 서울시 지하철 실시간 도착정보, 부산/대구/대전/광주 등은 지역별 API를 검토합니다.

## 개인보정 시간

개인보정 시간은 실제 이동 기록 기반으로 계산합니다. Gemini 같은 LLM 추론으로 계산하지 않습니다.

```text
personalBufferMinutes
= 기본 보정값
+ 최근 출발 지연 평균
+ 최근 도착 지각 평균
```

이를 위해 이동 기록 저장 모델이 필요합니다.

```kotlin
data class CommuteHistory(
    val routineId: Long,
    val recommendedDepartureTime: LocalDateTime,
    val actualDepartureTime: LocalDateTime?,
    val targetArrivalTime: LocalDateTime,
    val actualArrivalTime: LocalDateTime?,
    val routeDurationMinutes: Int,
    val adjustedRouteDurationMinutes: Int?,
    val personalBufferMinutes: Int,
    val createdAt: LocalDateTime,
)
```

## 구현 우선순위

1. ODsay 세부 경로 DTO 확장
2. 첫 버스 구간 추출
3. TAGO 정류소정보 provider
4. TAGO 도착정보 provider
5. 노선번호 정규화와 매칭 점수식
6. `RouteRealtimeSnapshot` 저장 (구현 완료)
7. `adjustedRouteDurationMinutes` 계산
8. fallback과 stale 처리 (20분 이내 fresh snapshot 재사용 구현 완료)
9. `finalDepartureTime < now` clamp 처리 (구현 완료)
10. `AlarmManager` 기본 알림
11. `WorkManager` 출발 전 재조회
12. `AlarmManager` 재예약
13. 이동 기록 저장
14. 개인보정 자동 계산
15. 지하철 지역별 provider

## 최종 기준

초기 경로 계산은 ODsay가 담당하고, 버스 실시간 보정은 TAGO가 담당합니다. 보정 결과는 `Routine`이 아니라 `RouteRealtimeSnapshot`에 저장하며, 마지막 성공 보정값은 유효기간 안에서만 사용합니다. 실시간 정류장/노선 매칭 신뢰도가 낮거나 재조회가 지연된 경우에는 ODsay 기본 예상시간으로 fallback합니다. 현재 MapMate에서 중요한 것은 실시간 데이터를 무조건 믿는 것이 아니라, 매칭 신뢰도, 스냅샷 유효기간, fallback을 갖춘 안정적인 보정 구조입니다.
