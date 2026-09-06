# 약속별 참여 마감

약속 생성자가 참여 마감 시각을 선택한다. 고정된 24시간 제한은 없으며 당일 약속도 만들 수 있다.

## 규칙

- 새 약속에는 `participationDeadline`이 필수다. `현재 시각 < 참여 마감 <= 약속 시작`이어야 한다.
- 시각은 서버의 `app.time-zone` 기준이며 기본값은 `Asia/Seoul`이다. 약속 날짜·시간, 참여 마감, 정산 판정과 거래 시각에 같은 기준을 사용한다.
- 마감 **직전까지** 초대, 재초대, 수락, 거절, 수락한 참여의 취소가 가능하다. 마감 정각부터는 409를 반환한다.
- 마감 시점까지 수락한 `ACCEPTED` 참여자만 정산 대상이다. 응답하지 않은 `PENDING` 초대는 정산에 포함하지 않으며, 마감된 초대는 초대 요청 목록에서 제외한다. 별도 스케줄러 없이 매 요청에서 시각을 검사한다.
- 취소는 `ACCEPTED -> CANCELLED`로 기록한다. 거절과 취소를 구분하고, 마감 전 재초대는 기존 행을 `PENDING`으로 되돌린다. 다시 수락해야 참여자가 된다.
- 주최자는 본인 참여만 취소할 수 없다. 기존 약속 삭제 API로 마감 전에 약속 전체를 취소할 수 있다. 마감 후 삭제는 차단한다.
- 마감 변경은 주최자만 가능하며, 기존 마감 전이고 다른 참여자가 한 번도 수락하지 않았어야 한다. 주최자의 자동 참여는 첫 수락으로 세지 않는다. 첫 수락 후에는 취소·재초대를 해도 마감 변경을 다시 허용하지 않는다.
- 사용자가 직접 도착 버튼을 누르는 방식은 유지한다. `ACCEPTED` 참여자만 도착 처리할 수 있고 정산 완료 후에는 도착 상태를 변경할 수 없다.
- 정산 완료 후에는 초대·수락·거절·취소·마감 변경·삭제도 불가능하다.

## API

모든 요청에 기존 JWT 인증을 사용한다.

### 생성: `POST /promise`

```json
{
  "date": "2026-09-07",
  "time": "18:30",
  "participationDeadline": "2026-09-07T18:25:00",
  "title": "저녁 약속",
  "penalty": 1000,
  "latitude": 37.5665,
  "longitude": 126.9780
}
```

시각 예시는 실제 요청 시 미래 시각으로 바꾼다. 마감 누락·과거 시각·시작 이후 마감·잘못된 날짜 형식은 400이다. 기존 클라이언트도 생성 요청에 새 필드를 추가해야 한다.

### 마감 변경: `PATCH /promise/{promiseId}/participation-deadline`

```json
{
  "participationDeadline": "2026-09-07T18:20:00"
}
```

성공하면 204, 주최자가 아니면 403, 이미 마감했거나 첫 수락 이후면 409다. 이미 마감된 약속의 마감을 연장해 참여를 다시 열 수 없다.

### 참여 취소: `POST /promise/cancel/{participantId}`

본문 없이 요청한다. 약속 ID가 아닌 본인의 **참여 ID**를 사용한다. 성공하면 204, 타인의 참여 취소는 403, 마감 이후·주최자 탈퇴·수락 상태가 아닌 참여 취소는 409다. 초대를 아직 수락하지 않은 경우 기존 `/promise/decline/{participantId}`를 사용한다.

### 조회

- 생성·상세 응답: `participationDeadline`, `participationOpen`, `participationDeadlineEditable` 제공. 수정 가능 여부는 조회자 권한도 반영한다.
- 약속 목록: `participationDeadline`, `participationOpen` 제공.
- 초대 목록: `participationDeadline` 제공. 마감 또는 정산 완료된 초대는 제외.
- 상세 응답의 `participants`는 `{participantId, username, status, arrival}` 목록이다. 앱은 이 목록으로 본인 참여 ID와 취소 상태를 표시한다.
- 기존 상세의 `participantUsernames`, `participantIds`는 수락한 참여자만 포함한다. 두 집합의 순서로 사용자와 ID를 연결하지 말고 `participants`를 사용한다.

화면에는 마감 시각과 함께 "참여 마감 이후 취소할 수 없으며, 지각 시 벌금이 적용됩니다"를 표시한다. `participationOpen`은 응답 시점의 값이므로 앱에서 버튼을 숨기더라도 서버의 409를 처리해야 한다.

## DB 적용과 기존 데이터

- `promise.participation_deadline`(datetime), `promise.participation_deadline_locked`(boolean)과 `ParticipantStatus.CANCELLED`가 추가됐다.
- 로컬의 기존 `ddl-auto=update` 환경에서는 시작 시 Hibernate가 스키마 변경을 처리한다. 자동 변경을 사용하지 않는 MariaDB 배포 환경용 SQL은 [참여 마감 스키마 변경](sql/participation_deadline.sql)에 있다. 이 SQL은 애플리케이션에서 자동 실행하지 않는다.
- 기존 약속의 마감 값이 null이면 시작 시각을 마감으로 사용한다. 기존 수락자가 있는 약속도 마감 변경을 제한한다.
- 변경 API와 정산은 약속 행에 대한 쓰기 잠금을 먼저 획득한다. 참여 ID로 변경할 때는 약속 잠금 후 참여 행도 잠금 조회하여 대기 중에 바뀐 참여 상태를 다시 읽는다.
- 기존 운영 데이터의 시각이 다른 시간대 기준이라면 `app.time-zone`을 그 기준에 맞춰 배포해야 한다. 이 기능은 기존 날짜 문자열을 변환하지 않는다.

## 검증 범위

단위 테스트로 마감 직전·정각·직후, 당일 약속 생성, 마감 범위, 수락·취소·재초대와 정산 후 변경 제한을 검사한다. MockMvc로 JSON 날짜 파싱과 HTTP 오류 응답을 검사하며, H2 JPA 테스트로 마감 및 취소 상태와 첫 수락 잠금이 실제로 저장되는지 확인한다.

MariaDB 운영 스키마 변경과 실제 동시 요청 부하 테스트는 별도 검증 대상이다. 서로 다른 약속이 같은 사용자의 잔액을 동시에 갱신하는 기존 문제는 이번 약속 단위 잠금의 범위 밖이다.
