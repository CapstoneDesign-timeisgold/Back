# Jiki 백엔드 개발과 운영 안내

참여와 정산 규칙, API, DB 적용, 검증 기록 및 Nginx 배포 절차를 이 문서에서 관리한다. 프로젝트 소개와 기본 실행 방법은 [루트 README](../../README.md)에서 확인한다. 새 변경은 관련 항목에 반영하고, 같은 설명을 별도 문서에 중복 작성하지 않는다.

- [변경 기록](#changes)
- [API 오류 응답](#api-errors)
- [참여 마감과 도착 확인](#participation)
- [정산과 잠금 재시도](#settlement)
- [DB 제약 점검](#db-check)
- [테스트 실행](#tests)
- [Nginx 배포](#deployment)

<a id="changes"></a>

## 변경 기록

| 항목 | 원인 | 수정 | 결과 |
| --- | --- | --- | --- |
| 요청자 식별과 권한 | 본문의 사용자명을 신뢰하고 타인의 리소스 접근 가능 | JWT 인증 사용자로 식별하고 친구 요청 및 약속과 정산의 권한 검증 적용 | 컨트롤러와 서비스 테스트에서 타인 요청 거절 확인 |
| 서버 시작 | 보안 설정과 인증 서비스의 순환 의존성 | `PasswordEncoder`를 `PasswordConfig`로 분리 | JDK 17과 MariaDB 환경에서 8080 포트 기동 확인 |
| 정산 대상과 시점 | 미수락 참여자 포함 및 약속 전 정산 가능 | `ACCEPTED`만 포함하고 시작 전 정산 차단 | 혼합 상태 참여자와 미래 약속 테스트 통과 |
| 친구와 초대 중복 | 자기 자신 요청 및 중복 관계 생성 가능 | 양방향 관계 검사, 참여자 고유 제약, 기존 거절 초대 재사용 적용 | 자기 자신 요청과 대기 중 중복 초대 거절 확인 |
| 약속 입력 | 빈 제목, 음수 벌금, 잘못된 좌표와 날짜 저장 가능 | 제목 필수 및 100자 제한, 벌금 0 이상, 위도 -90~90 및 경도 -180~180 검증과 날짜 파싱 적용 | 필드 오류와 존재하지 않는 날짜의 저장 전 거절 확인 |
| 참여 마감과 취소 | 참여 변경 가능 시점과 첫 수락 이후 마감 변경 기준 필요 | 참여 마감, `CANCELLED` 상태 및 첫 수락 잠금 적용 | 시각 경계와 취소 및 재초대 규칙 검증 |
| 목록 조회 | 지연 로딩에 따른 N+1 조회 | 수락 상태 DB 필터와 약속 및 생성자 fetch join 적용 | 목록 크기와 관계없이 서비스 SQL 2회 확인 |
| 정산 무결성 | 나머지 금액 소실, 관리자 기록 누락 및 공유 잔액 동시 갱신 충돌 | 계정 순서 잠금, 거래 기록 고유 제약 및 결과 스냅샷 적용 | 금액 보존, 거래 내역 일치 및 재요청 시 금액 반영 1회 확인 |
| 정산 전용 계정 | 사용자명에 의존한 자금 계정 식별 | 시스템 계정 표식과 고유 제약, 일반 가입 및 인증 차단 적용 | 기존 계정은 명시적 ID 지정 후 잔액과 거래 내역을 유지하며 전환 |
| API 오류 응답 | `IllegalStateException` 전체를 401로 처리 | 인증, 권한, 상태 충돌 및 내부 오류 구분 | 로그인 실패 응답 통일과 Spring MVC 오류 상태 유지 |
| 도착 판정 | 확인 여부만으로 정상 도착과 지각 구분 불가 | 서버 도착 시각 저장과 시작 정각 기준 판정 적용 | 시각 경계, 위조 시각 무시 및 정산 반영 검증 |
| 잠금 충돌 재시도 | 잠금 대기 초과와 데드락 발생 시 전체 재시도 부재 | 잠금당 2초 대기와 최대 3회 시도, 503 응답 및 MariaDB CI 추가 | 일반 테스트 156개와 실제 MariaDB 테스트 6개 통과 |

### 목록 조회 측정

수락 약속 10/50/100개에서 서비스 SQL이 22/102/202회 발생하던 것을 각 2회로 축소했다. 약속 100개와 k6 VU 10 조건에서 p95 응답 시간의 3회 중앙값은 58.09ms에서 8.81ms로 감소했다. VU 1/10/30 전후 측정 총 82,405건에서 HTTP 오류와 응답 필드 불일치는 0건이었고, SQL 2회를 검사하는 회귀 테스트를 추가했다.

동일 PC의 MariaDB 10.11.19와 k6 2.3.0에서 워밍업 5초 후 조건별 10초씩 3회 측정한 로컬 결과다. 같은 계정으로 조회하되 약속 생성자가 모두 다른 테스트 데이터를 사용했다.

<a id="api-errors"></a>

## API 오류 응답

| 상태 | 적용 기준 |
| --- | --- |
| 400 | 필수값 누락과 공백, 잘못된 JSON과 ID 형식, 자기 자신에게 친구 요청 |
| 401 | 미인증, 존재하지 않는 아이디 또는 잘못된 비밀번호, 전용 계정 로그인 |
| 403 | 다른 사용자의 친구 요청 처리, 권한 없는 약속 조회와 초대 및 삭제 |
| 404 | 존재하지 않는 사용자, 친구 요청 또는 약속 |
| 409 | 회원 정보 중복, 이미 처리한 요청, 참여와 정산의 상태 충돌 |
| 500 | 예상치 못한 내부 오류 |
| 503 | 정산 잠금 충돌 재시도 소진, `Retry-After: 1` 반환 |

인증 실패는 `BadCredentialsException`, 권한 부족은 `AccessDeniedException`, 상태 충돌은 `ConflictException`으로 구분한다. 로그인 실패 메시지는 아이디 존재 여부와 무관하게 동일하다. 회원가입은 중복 사전 검사와 DB 고유 제약을 함께 사용하고, 다른 무결성 오류를 중복 가입으로 처리하지 않는다. Spring MVC의 404, 405, 415는 유지하며 내부 오류 상세는 서버 로그에만 기록한다.

<a id="participation"></a>

## 참여 마감과 도착 확인

약속 생성자가 참여 마감 시각을 선택한다. 고정된 24시간 제한은 없으며 당일 약속도 만들 수 있다.

### 규칙

- 새 약속에는 `participationDeadline`이 필수다. `현재 시각 < 참여 마감 <= 약속 시작`이어야 한다.
- 시각은 서버의 `app.time-zone` 기준이며 기본값은 `Asia/Seoul`이다. 약속 날짜·시간, 참여 마감, 정산 판정과 거래 시각에 같은 기준을 사용한다.
- 마감 **직전까지** 초대, 재초대, 수락, 거절, 수락한 참여의 취소가 가능하다. 마감 정각부터는 409를 반환한다.
- 마감 시점까지 수락한 `ACCEPTED` 참여자만 정산 대상이다. 응답하지 않은 `PENDING` 초대는 정산에 포함하지 않으며, 마감된 초대는 초대 요청 목록에서 제외한다. 별도 스케줄러 없이 매 요청에서 시각을 검사한다.
- 취소는 `ACCEPTED -> CANCELLED`로 기록한다. 거절과 취소를 구분하고, 마감 전 재초대는 기존 행을 `PENDING`으로 되돌린다. 다시 수락해야 참여자가 된다.
- 주최자는 본인 참여만 취소할 수 없다. 기존 약속 삭제 API로 마감 전에 약속 전체를 취소할 수 있다. 마감 후 삭제는 차단한다.
- 마감 변경은 주최자만 가능하며, 기존 마감 전이고 다른 참여자가 한 번도 수락하지 않았어야 한다. 주최자의 자동 참여는 첫 수락으로 세지 않는다. 첫 수락 후에는 취소·재초대를 해도 마감 변경을 다시 허용하지 않는다.
- 사용자가 직접 도착 버튼을 누르는 방식 유지. 약속 시작 10분 전부터 `ACCEPTED` 참여자 본인만 확인 가능. 서버 시각이 약속 시작 정각까지면 정상 도착, 이후면 지각으로 기록. 정산 완료 후 변경 차단.
- 정산 완료 후에는 초대·수락·거절·취소·마감 변경·삭제도 불가능하다.

### API

모든 요청에 기존 JWT 인증을 사용한다.

#### 생성: `POST /promise`

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

#### 마감 변경: `PATCH /promise/{promiseId}/participation-deadline`

```json
{
  "participationDeadline": "2026-09-07T18:20:00"
}
```

성공하면 204, 주최자가 아니면 403, 이미 마감했거나 첫 수락 이후면 409다. 이미 마감된 약속의 마감을 연장해 참여를 다시 열 수 없다.

#### 참여 취소: `POST /promise/cancel/{participantId}`

본문 없이 요청한다. 약속 ID가 아닌 본인의 **참여 ID**를 사용한다. 성공하면 204, 타인의 참여 취소는 403, 마감 이후·주최자 탈퇴·수락 상태가 아닌 참여 취소는 409다. 초대를 아직 수락하지 않은 경우 기존 `/promise/decline/{participantId}`를 사용한다.

#### 조회

- 생성·상세 응답: `participationDeadline`, `participationOpen`, `participationDeadlineEditable` 제공. 수정 가능 여부는 조회자 권한도 반영한다.
- 약속 목록: `participationDeadline`, `participationOpen` 제공.
- 초대 목록: `participationDeadline` 제공. 마감 또는 정산 완료된 초대는 제외.
- 상세 응답의 `participants`는 `{participantId, username, status, arrival, arrivedAt, arrivalStatus}` 목록이다. 앱은 이 목록으로 본인 참여 ID와 취소·도착 상태를 표시한다.
- 기존 상세의 `participantUsernames`, `participantIds`는 수락한 참여자만 포함한다. 두 집합의 순서로 사용자와 ID를 연결하지 말고 `participants`를 사용한다.

화면에는 마감 시각과 함께 "참여 마감 이후 취소할 수 없으며, 지각 시 벌금이 적용됩니다"를 표시한다. `participationOpen`은 응답 시점의 값이므로 앱에서 버튼을 숨기더라도 서버의 409를 처리해야 한다.

<a id="arrival-confirmation"></a>

#### 도착 확인: `POST /promise/update-late-status`

- 요청 필드: `promiseId`, `arrival=true`. 성공 시 기존과 동일하게 204 응답.
- `arrival` 누락·false 요청은 400. 클라이언트가 보내는 시각·다른 사용자명은 판정에 사용하지 않음.
- 약속 10분 전보다 이른 확인, 수락하지 않은 참여, 중복 확인, 정산 완료 후 확인은 409. 본인 참여가 없는 경우 404.
- 시각은 약속 잠금을 획득한 뒤 서버의 `Clock`으로 읽고 `DATETIME(6)`와 같은 마이크로초 정밀도로 저장. 시작 정각은 정상 도착, 저장된 시각이 시작을 넘으면 지각.
- 지각 후 확인도 `arrival=true`와 실제 `arrivedAt`을 남기되, 정산 시 벌금 대상으로 처리. 도착 확인 여부와 보상 자격은 별개.
- 참여자 응답의 `arrivalStatus`: `NOT_CONFIRMED`(미확인), `ON_TIME`(정상 도착), `LATE`(지각). `arrival`은 확인 여부, `arrivedAt`은 서버 확인 시각이며 미확인은 null.
- 생성·상세 응답에 `arrivalOpensAt`, `arrivalConfirmationOpen` 추가. 후자는 약속 전체의 확인 시간대가 열렸는지 나타내며, 본인 참여 상태·이미 확인했는지는 `participants`에서 별도 확인 필요.
- 마감 전 참여 취소·재초대 시 도착 확인과 시각 초기화. 재수락 후 다시 확인해야 함.

### DB 적용과 기존 데이터

- `promise.participation_deadline`(datetime), `promise.participation_deadline_locked`(boolean)과 `ParticipantStatus.CANCELLED`가 추가됐다.
- 로컬의 기존 `ddl-auto=update` 환경에서는 시작 시 Hibernate가 스키마 변경을 처리한다. 자동 변경을 사용하지 않는 MariaDB 배포 환경용 SQL은 [참여 마감 스키마 변경](sql/participation_deadline.sql)에 있다. 이 SQL은 애플리케이션에서 자동 실행하지 않는다.
- 기존 약속의 마감 값이 null이면 시작 시각을 마감으로 사용한다. 기존 수락자가 있는 약속도 마감 변경을 제한한다.
- 변경 API와 정산은 약속 행에 대한 쓰기 잠금을 먼저 획득한다. 참여 ID로 변경할 때는 약속 잠금 후 참여 행도 잠금 조회하여 대기 중에 바뀐 참여 상태를 다시 읽는다.
- 도착 시각 컬럼 배포 SQL: [arrival_confirmation.sql](sql/arrival_confirmation.sql). 기존 `arrival=true`, `arrived_at=NULL` 기록은 실제 확인 시각을 추정하지 않고 종전의 정상 도착 취급 유지. 새 확인부터 서버 시각 규칙 적용. 기존 확정 정산 결과는 변경하지 않음.
- 기존 운영 데이터의 시각이 다른 시간대 기준이라면 `app.time-zone`을 그 기준에 맞춰 배포해야 한다. 이 기능은 기존 날짜 문자열을 변환하지 않는다.

### 검증 범위

단위 테스트로 마감 직전·정각·직후, 당일 약속 생성, 마감 범위, 수락·취소·재초대와 정산 후 변경 제한을 검사한다. MockMvc로 JSON 날짜 파싱과 HTTP 오류 응답을 검사하며, H2 JPA 테스트로 마감 및 취소 상태와 첫 수락 잠금이 실제로 저장되는지 확인한다.

MariaDB 운영 스키마 변경은 별도 적용 대상이다. 서로 다른 약속이 같은 사용자의 잔액을 동시에 갱신하는 문제는 후속 [정산 무결성 개선](#settlement)에서 계정 잠금과 현재 잔액 조회로 처리하고, 로컬 MariaDB 동시 실험으로 검증했다.

<a id="settlement"></a>

## 정산

### 정산 규칙

수락한 참여자만 정산한다. 지각자의 벌금 합계를 정시 도착자 수로 나누고, 1원 미만의 금액은 올림하여 균등 지급한다. 초과 지급액은 `admin` 계정에서 차감한다. 정시 도착자가 없으면 벌금 전액을 관리자에게 적립한다. 관리자 부담금과 적립금도 거래 내역에 남긴다.

정시 도착 판정은 도착 확인 여부와 서버에 저장한 `arrivedAt`으로 처리. 약속 시작 정각까지 확인한 참여자만 보상 대상이며, 시작 후 확인자는 `arrival=true`여도 지각자로 분류. 미확인 참여자도 벌금 대상. [도착 확인 규칙](#arrival-confirmation)에 따라 10분 전부터 확인 허용. 시각이 없는 과거 확인 기록과 이미 확정된 정산 결과는 종전 처리 유지.

예를 들어 벌금 4,000원을 3명에게 지급하면 각 1,334원씩 총 4,002원을 지급하고 관리자가 2원을 부담한다. 정확히 나누어지면 관리자 부담금은 없다. 응답의 `adminAmount`는 일반 참여자 보상·벌금 외 관리자 잔액 조정액으로, 부담금은 음수(예: `-2`), 적립금은 양수다.

관리자도 참여자라면 일반 참여자 규칙을 적용한다. 관리자가 정시 도착자이면 보상 입금과 부담금 출금을 별도로 기록하고, 지각자이면 벌금과 부담금을 한 출금 기록으로 합친다. 전원 지각 시 관리자 벌금 출금과 벌금 전액 입금도 별도로 남긴다.

잔액이 부족해도 벌금을 차감하는 기존 정책은 유지하며, 관리자 부담금도 잔액이 부족하면 음수 잔액으로 반영한다. 음수 벌금과 `int` 범위를 넘는 계산은 409로 거절하며 잔액·거래 기록·정산 상태를 모두 롤백한다.

### 동시 정산과 재시도

1. 약속 행을 잠그고 생성자 권한과 정산 시점을 검사한다.
2. 영향을 받는 모든 계정(관리자 포함)을 사용자 ID 오름차순으로 잠근다.
3. `SELECT money ... FOR UPDATE`로 현재 잔액을 직접 읽는다. 앞서 조회한 JPA 엔티티의 잔액을 재사용하지 않는다.
4. 잔액 변경, 약속 ID가 있는 거래 기록, 확정 결과 JSON, 정산 완료 상태를 같은 트랜잭션으로 저장한다.

약속 ID가 재시도 식별자다. 새 방식으로 정산된 약속에 생성자가 다시 요청하면 금액을 재반영하지 않는다. `POST /settlement/reward`는 최초·재시도 모두 기존 성공 응답인 204를 반환하고, `GET /settlement/{promiseId}/result`는 저장된 확정 결과를 반환한다. 생성자 외 사용자의 정산 요청은 계속 차단한다.

`money_record(promise_id, user_id, is_penalty)` 유일 제약으로 같은 약속·계정·방향의 중복 거래를 막는다. 잠금 조회와 잔액 변경은 반드시 같은 트랜잭션에서 실행해야 한다. 직접 SQL로 변경한 잔액은 기존 영속 엔티티에 자동 반영되지 않으므로 정산 중 잔액 계산은 잠금 조회 결과만 사용한다. 앞으로 다른 잔액 변경 기능을 추가할 때도 이 규칙을 공유해야 한다.

### 잠금 충돌 재시도와 MariaDB 회귀 검증 (2026-11-16)

약속당 한 번 정산하는 기존 구조와 금액 정책은 유지한다. HTTP 정산 요청은 `SettlementRetryService`를 거쳐 기존 `SettlementService`를 호출한다. 재시도 서비스에는 `Propagation.NEVER`를 적용해 이미 진행 중인 트랜잭션에 참여하지 못하게 한다. 매 시도는 기존 서비스의 트랜잭션 프록시를 통과하며, 커밋 오류까지 확인하고 실패한 트랜잭션이 롤백된 다음 정산 전체를 다시 실행한다. 같은 클래스 내부 호출로 트랜잭션 경계를 우회하지 않는다.

- 정산의 약속 잠금은 JPA 잠금 시간 제한 2초, 계정 잠금은 MariaDB `FOR UPDATE WAIT 2`로 잠금 한 번당 대기를 제한한다. 약속 변경 등 다른 기능의 기존 잠금 조회는 유지한다. 이 시간은 요청 전체의 제한 시간이 아니며, 여러 계정에서 각각 대기하면 전체 시간은 늘어날 수 있다.
- 최초 실행을 포함해 최대 3회 시도한다. 재시도 전 대기는 첫 번째 50~100ms, 두 번째 100~200ms 사이의 무작위 값이다. 이때 트랜잭션과 DB 잠금은 유지하지 않는다.
- Spring/JPA의 비관적 잠금 충돌과 MariaDB 오류 1205(잠금 대기 초과), 1213(데드락)만 재시도한다. 권한·업무 규칙·유니크 제약 위반 및 일반 연결 오류는 재시도하지 않는다. 스레드 인터럽트가 발생하면 상태를 보존하고 중단한다.
- 재시도 소진 시 `503 Service Unavailable`, `Retry-After: 1`을 반환한다. 클라이언트는 같은 `promiseId`로 재요청할 수 있다. 성공은 최초·중복 모두 기존처럼 204다. DB 오류 상세는 응답에 노출하지 않는다.

<a id="db-check"></a>

#### 실제 DB 제약 확인

[읽기 전용 점검 SQL](sql/check_settlement_integrity.sql)은 InnoDB 여부, 정산 컬럼, 실제 유니크 인덱스의 컬럼 구성, 중복 거래 그룹, 사용자 없는 신규 거래를 검사한다. 대상 DB를 지정해서 실행하고 모든 항목이 `PASS`인지 확인한다. 데이터나 스키마를 변경하지 않으며, 이 SQL을 실행해도 누락된 제약이 자동 생성되지는 않는다.

```bash
mariadb --defaults-extra-file=/path/to/read-only-client.cnf --database=jiki < docs/sql/check_settlement_integrity.sql
```

2026-11-16 현재 로컬 설정이 연결하는 `127.0.0.1:3306/jiki`(MariaDB 10.11.19)에서 다섯 점검 모두 PASS였다. `uk_record_promise_user_direction`은 실제 UNIQUE 인덱스이고, 컬럼 순서는 `promise_id,user_id,is_penalty`였다. 중복 그룹과 사용자 없는 식별 거래는 모두 0건이었다. 운영 서버 DB의 적용 상태는 별도로 확인해야 한다.

#### 반복 실행하는 MariaDB 테스트

`settlementConcurrencyTest`는 k6 없이 실행하는 실제 MariaDB 회귀 테스트다. 일반 `test`와 기존 성능 비교용 `settlementExperiment`는 분리해서 유지한다. **이 테스트는 `create-drop`으로 스키마를 생성·삭제하므로 폐기 가능한 전용 DB와 해당 DB에만 권한이 있는 계정을 준비해야 한다.** URL은 `localhost` 또는 `127.0.0.1`의 `jiki_test_settlement_*` 이름만 허용하며 운영 설정의 DB를 사용하지 않는다.

```bash
export SETTLEMENT_TEST_DB_URL=jdbc:mariadb://127.0.0.1:3306/jiki_test_settlement_local
export SETTLEMENT_TEST_DB_USER=settlement_test
# SETTLEMENT_TEST_DB_PASSWORD는 전용 테스트 계정 비밀번호로 설정한다.
./gradlew test settlementConcurrencyTest
```

검증 범위:

- InnoDB와 실제 유니크 인덱스 확인 및 중복 거래 INSERT 거절
- 같은 약속에 인증된 HTTP 요청 10개 동시 실행 후 금액 반영 1회 확인
- 같은 계정을 공유하는 서로 다른 약속 동시 정산 후 잔액·거래 내역 일치
- 실제 약속 행 잠금 대기 초과 후 새 영속성 컨텍스트에서 재시도 성공
- 실제 계정 잠금 대기 초과 3회 후 HTTP 503과 변경 없음 확인, 잠금 해제 후 재요청 성공
- 테스트 전용 두 행을 역순으로 잠가 실제 InnoDB 데드락을 재현하고, 피해 트랜잭션의 선행 쓰기가 롤백된 뒤 전체 정산이 재시도되는지 확인

2026-11-16 일반 회귀 테스트 156개와 로컬 MariaDB 10.11.19 테스트 6개가 모두 통과했고, 로그에서 실제 오류 1205와 1213 발생을 확인했다. 비공개 `application.properties`를 읽지 않는 설정으로도 전체 검증을 통과했다. 데드락 테스트의 역순 잠금은 재현용 테스트 코드에만 있으며 실제 정산은 기존 ID 오름차순 잠금을 유지한다. `.github/workflows/backend-tests.yml`에 Java 17과 MariaDB 10.11을 사용하는 PR·main/develop push·수동 실행 검증을 추가했다. [PR #27 CI](https://github.com/CapstoneDesign-timeisgold/Back/actions/runs/43785157081)에서 일반 회귀와 MariaDB 테스트 통과를 확인했다.

### DB 변경과 기존 정산

<a id="settlement-account"></a>

#### 정산 전용 계정

정산 자금 계정은 사용자명 대신 `site_user.system_account_type=SETTLEMENT`로 식별한다. 이 컬럼은 일반 회원에게는 NULL이며, 고유 제약으로 정산 전용 계정이 둘 이상 생기지 않도록 한다. 회원가입에서 `admin`과 대소문자·전각 문자·양끝 공백 변형을 차단한다. 가입 요청으로 전용 계정 표식을 지정할 수 없다.

새 DB에서는 애플리케이션 시작 시 `admin` 전용 계정을 잔액 0으로 자동 생성한다. 알려진 초기 비밀번호는 제공하지 않으며, 전용 계정은 일반 로그인과 JWT 인증이 차단된다. 재시작 시 기존 계정과 잔액을 그대로 사용한다.

기존 DB에서 `admin` 사용자만 있고 전용 계정 표식이 없으면 이름만으로 자동 승격하지 않고 시작을 중단한다. 운영자가 소유권과 잔액·거래 내역을 확인한 계정의 ID를 `settlement.account.existing-user-id` 또는 `SETTLEMENT_ACCOUNT_EXISTING_USER_ID`로 지정해야 한다. 이 설정은 신뢰할 수 있는 서버 설정에서만 관리하며, 일반 회원을 잘못 지정하지 않아야 한다. 지정한 계정은 기존 ID·잔액·거래 내역을 유지하고 전용 계정으로 전환되며, 이후 일반 로그인이 차단된다. 이미 초기화된 전용 계정과 다른 ID를 지정하면 시작을 중단한다.

자동 스키마 변경을 사용하지 않는 환경에서는 [정산 계정 SQL](sql/settlement_account.sql)을 먼저 적용한다. SQL은 기존 회원을 자동 승격하지 않는다. 실험에서 `settlement.account.initialize=false`로 시작 시 생성을 끄는 경우 테스트 데이터에 전용 계정을 직접 준비해야 한다. 표식이 없는 계정으로 정산을 대체하지 않는다.

[MariaDB 스키마 변경 SQL](sql/settlement_integrity.sql)은 `promise.settlement_result`, `money_record.promise_id`와 유일 인덱스를 추가한다. 자동 스키마 변경을 쓰지 않는 환경은 애플리케이션 배포 전에 적용한다. 이 파일은 자동 실행되지 않는다.

기존 정산의 거래 기록은 약속 제목만 있어 약속 ID를 안전하게 역산할 수 없다. 기존 기록의 `promise_id`는 null로 두고, 결과 JSON이 없는 과거 정산의 POST는 409를 유지한다. 과거 결과 GET은 기존 계산 방식으로 조회하므로 확정 스냅샷과 같은 보장을 제공하지 않는다. 과거 누락된 관리자 기록이나 소실된 나머지는 자동 보정하지 않는다.

올림 지급은 앞으로 실행하는 정산부터 적용한다. 이미 저장된 확정 결과와 거래 기록은 다시 계산하지 않으며, 과거 결과 JSON의 양수 `adminAmount`도 그대로 반환한다. 이번 규칙 변경에는 DB 스키마 변경이 없다.

### 재현 및 검증

정산 회귀 테스트는 금액 보존, 모든 거래 시점의 잔액, 관리자 참여, 전체 지각, 대상 상태, 오버플로·유일 제약 위반 롤백, 권한, 재시도, 과거 정산 호환성을 검사한다.

MariaDB 실험에는 Java 17, k6, **비어 있는 로컬 `jiki_perf_*` 전용 스키마**와 해당 DB의 계정이 필요하다. 엔티티 테이블을 생성·삭제하므로 실제 서비스 DB를 사용하지 않는다. DB 계정은 `PERF_DB_USER`, `PERF_DB_PASSWORD` 환경 변수로 전달한다.

```powershell
./docs/settlement-experiment/run.ps1 -Label after-check -JavaHome $env:JAVA_HOME -Database jiki_perf_settlement -DatabasePort 3307 -K6Path (Get-Command k6).Source
```

`before` 라벨만 개선 전 관찰 모드이며 나머지 라벨은 개선 후 불변식을 검사한다. 라벨별 결과는 덮어쓰지 않는다. 기존 코드를 비교할 때는 개선 전 커밋 `e25800b`의 별도 체크아웃에 실험 테스트·실행 태스크·스크립트만 가져와 `before`로 실행한다. 실제 측정에서는 개선 전 실행 후 같은 환경에서 구현을 변경하여 개선 후를 실행했다.

아래는 관리자에게 나머지를 적립하던 이전 정책으로 2026-09-29, 동일 PC의 Java 17.0.10·MariaDB 10.11.19(`REPEATABLE-READ`)·k6 2.3.0에서 확인한 결과다. 현재 올림 지급 정책의 실측 결과는 아니다.

| 항목 | 개선 전 | 개선 후 |
|---|---:|---:|
| 1,000원 / 3명 정산 후 전체 잔액 소실 | 1원 | 0원(관리자 1원 적립) |
| 전원 지각 시 관리자 입금 기록 | 0건 | 1건 |
| 서로 다른 약속 2개 동시 정산, 20회 중 모두 완료·잔액/기록 일치 | 0회 | 20회 |
| 같은 약속에 k6 VU 10 · 100요청 × 3회의 204 응답 | 3 / 300 | 300 / 300 |
| 위 재시도 실험의 실제 금액 반영 | 약속별 1회 | 약속별 1회 |

개선 전 동시 실험은 20회 모두 한 트랜잭션이 데드락으로 실패했다. 두 트랜잭션이 같은 계정 정보를 미리 읽은 뒤 barrier로 진행을 맞춘 **의도적인 충돌 재현**이며, 운영 실패율이 아니다. 재시도에서 개선 전 나머지 297건은 409였으며 중복 차감은 없었다. 따라서 개선 효과는 중복 차감 제거가 아니라 재시도의 정상 처리다.

후속 `after-extended` 실행에서는 위 검증을 다시 통과했고, 관리자를 공유하면서 두 약속에서 지각자·정시 도착자 역할이 뒤바뀌는 동시 정산도 10회 통과했다. 이때 관리자는 회당 2원을 받고 각 약속에 거래 기록 5건이 생성됐다. 전체 거래 기록의 저장 잔액이 해당 시점까지의 입출금 합계와 일치하는지도 검사했다.

위 수치는 로컬 정확성 실험의 결과다. 상세 실행 기록과 원본 결과는 로컬 `docs/settlement-experiment/REPORT.md`, `docs/settlement-experiment/results/`에 생성되며 저장소에는 포함하지 않는다.

<a id="tests"></a>

## 테스트 실행

백엔드 프로젝트의 `back/` 디렉터리에서 전체 단위 테스트를 실행한다.

```bash
./gradlew test
```

Windows에서는 `gradlew.bat`을 사용한다.

성능 테스트는 일반 테스트에서 제외하며, Java 17·k6와 별도 로컬 `jiki_perf_*` MariaDB 스키마를 준비한 뒤 실행한다. DB 계정은 `PERF_DB_USER`, `PERF_DB_PASSWORD` 환경 변수로 전달한다.

```powershell
./docs/performance/run.ps1 -Label after-check -JavaHome $env:JAVA_HOME -K6Path (Get-Command k6).Source -Database jiki_perf_check -DatabasePort 3306
```

성능 테스트는 전용 DB의 엔티티 테이블을 생성·삭제하므로 빈 실험용 스키마를 사용한다. 생성되는 상세 보고서와 원본 결과는 로컬에만 보관한다.

<a id="deployment"></a>

## Nginx 배포

Flutter → Nginx → Spring Boot(127.0.0.1:8080) → MariaDB

웹 브라우저 → Nginx → Flutter Web 빌드 파일

기존 API 경로, 쿼리 문자열, 요청 본문과 Authorization 헤더를 전달하는 구성.
인증과 접근 권한은 기존 Spring Security에서 처리. 사용자별 응답 캐시와 프록시의 자동 재요청은 비활성화.

### 로컬 실행

Spring Boot 실행 후 다음 명령 수행. Nginx 실행 파일 경로는 설치 위치에 맞게 지정.

```powershell
# 저장소 루트에서 실행
cd back/deploy/nginx
$nginxExe = 'C:/path/to/nginx.exe'
$prefix = ((Resolve-Path .).Path.Replace('\', '/')) + '/'
New-Item -ItemType Directory -Force logs | Out-Null
& $nginxExe -p $prefix -c nginx.conf -t
Start-Process -FilePath $nginxExe -ArgumentList @('-p', $prefix, '-c', 'nginx.conf') -WorkingDirectory $prefix -WindowStyle Hidden
```

명령 실행 위치는 `back/deploy/nginx`. 접속 주소는 http://127.0.0.1:8081.

```powershell
# 설정 검사 후 적용
& $nginxExe -p $prefix -c nginx.conf -t
& $nginxExe -p $prefix -c nginx.conf -s reload
# 종료
& $nginxExe -p $prefix -c nginx.conf -s quit
```

로그는 `logs/access.log`, `logs/error.log`에 저장. 요청 시간과 업스트림 응답 시간을 기록하며 쿼리 문자열, 인증 헤더, 요청 본문은 로그에서 제외.

### EC2 적용

[jiki.ec2.conf](../deploy/nginx/jiki.ec2.conf)는 EC2용 HTTP 설정 예시. 실제 서버의 기존 Nginx 설정 및 도메인 확인 후 적용.

1. [proxy.conf](../deploy/nginx/proxy.conf)를 `/etc/nginx/snippets/jiki-proxy.conf`로 복사.
2. `jiki.ec2.conf`의 API와 웹 도메인을 변경하고 `/etc/nginx/conf.d/jiki.conf`로 복사.
3. Spring Boot에 `SERVER_ADDRESS=127.0.0.1`, `SERVER_PORT=8080`, `SERVER_FORWARD_HEADERS_STRATEGY=native` 설정. 같은 서버의 Nginx만 백엔드에 직접 연결하도록 제한.
4. `sudo nginx -t` 성공 후 `sudo systemctl reload nginx` 실행.
5. 외부 API 주소를 Nginx 주소로 변경하고 보안 그룹에서 8080 직접 접근 차단.

Flutter Web은 `flutter build web` 결과인 `build/web`의 내용을 `/srv/jiki/flutter-web`에 배치.
API 도메인은 Spring Boot로 전달하고, 웹 도메인은 빌드 파일을 제공하는 구성.
모바일 앱은 API 도메인에 연결. Flutter Web의 API 주소와 백엔드 CORS 허용 출처는 실제 API 및 웹 도메인에 맞춰 설정.
로컬 [nginx.conf](../deploy/nginx/nginx.conf)는 API 전달용이며, 웹 파일 제공은 `jiki.ec2.conf`에 포함.

이 구성은 Nginx가 직접 요청을 받는 단일 프록시 기준. 앞에 로드 밸런서를 추가할 경우 신뢰할 프록시와 전달 헤더 정책을 함께 조정.
실서비스 인증 요청에는 도메인 인증서를 적용한 HTTPS 설정 필요. 현재 파일에는 인증서 경로나 HTTPS 적용을 가정하지 않음.

참고: [Nginx proxy 모듈](https://nginx.org/en/docs/http/ngx_http_proxy_module.html), [Windows 배포본](https://nginx.org/en/download.html).
