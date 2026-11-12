# FR-05 대기자 등록과 대기 순번 설계명세

- 상태: **승인됨 (2026-11-12)**. 8절의 질문은 모두 제안대로 확정했고 결정 사항 D-23·D-24로 기록했다. 구현계획은 `docs/plans/fr-05.md`.
- 대상: FR-05 "정원이 차면 대기자로 등록되고 대기 순번을 알려 준다"
- 선행: FR-04(`docs/design/FR-04-enrollment.md`, D-19~D-22). FR-04는 이미 `WAITING`으로 저장하지만 순번이 없다.
- 범위(제안, Q2·Q3): 대기 순서 표현, 신청 응답의 순번, 내 순번 조회, 정원 감소 시 강등(D-03). 취소(FR-06)와 승급(D-02, FR-06)은 하지 않는다.
- 브랜치: `feature/fr-04-fr-06`

## 1. 요구사항 정리

| 구분 | 내용 | 이 설계에서의 처리 |
|---|---|---|
| FR-05 | 정원이 차면 대기 등록, 대기 순번을 알려 준다 | 신청 응답에 `waitingNumber`, 내 신청 조회 API |
| D-01 | 대기자가 취소하면 승급 없이 뒤 순번만 당겨진다 | 순번을 저장하지 않고 조회 때 계산 → 취소 시 갱신할 행이 없다(FR-06에서 그대로 성립) |
| D-03 | 정원 감소 시 가장 늦게 확정된 사람부터 대기로, 기존 대기자보다 앞, 먼저 확정된 사람이 앞 | 정원 감소 처리에서 강등하고 정렬 키를 맨 앞에 부여(Q2) |
| D-05 | 취소 후 재신청은 자리가 없으면 대기 맨 뒤 | 새 대기자는 항상 정렬 키 최댓값 + 1 |
| D-09 | 교육 시작 후 대기자는 대기로 두고 승급 대상에서 제외 | 순번은 그대로 보여 준다(Q5). 승급은 FR-06 |
| D-21 | 대기자가 있으면 새 신청은 대기 | 유지 |
| NFR-01 | 동시 신청에도 정합성 | 순번 키 부여와 강등을 모두 회차 행 락 안에서 한다 |
| NFR-02 | N+1 없음 | 순번 계산은 회차 단위 한 번의 쿼리 |
| CLAUDE.md 6 | 수량·대기 순번 변경은 락, 중복은 DB 유일 제약 | 회차 락 + `(session_id, waiting_key)` 유일 제약 |
| 최종 검토 W3 | 관리자 정원 변경이 락을 안 씀 | 정원 변경도 회차 락을 첫 쿼리로 잡는다(Q2) |

## 2. 대기 순서 표현 (Q1)

**정렬 키를 저장하고, 순번은 조회할 때 계산한다.**

- `enrollment.waiting_key BIGINT NULL`: `WAITING`일 때만 값이 있고, 작을수록 앞이다. 확정·취소로 바뀌면 NULL로 지운다.
- 대기 순번 = 같은 회차에서 `WAITING`이면서 `waiting_key`가 나보다 작은 신청 수 + 1.
- 새 대기자(신청·재신청, D-05): 회차의 `max(waiting_key) + 1`(대기자가 없으면 1).
- 강등자(D-03): 회차의 `min(waiting_key)`보다 작은 키를 **먼저 확정된 사람이 더 작게** 부여한다. 강등 n명이면 `min - n … min - 1`(대기자가 없으면 `-n+1 … 0`처럼 연속). 키는 음수가 될 수 있다.
- 대기자 취소(D-01, FR-06): 키만 NULL로 지우면 뒤 사람들의 계산 순번이 자동으로 1씩 당겨진다. 다른 행을 고치지 않는다.

순번 숫자 자체를 저장하는 방식과 비교:

| 방식 | 취소(D-01) | 강등(D-03) | 동시성 위험 |
|---|---|---|---|
| **정렬 키 + 조회 시 계산 (제안)** | 키 하나만 NULL | 새 키 부여만 | 행 1개 갱신 |
| 순번 숫자 저장 | 뒤 대기자 전원 −1 | 기존 대기자 전원 +n | 다수 행 갱신, 누락 시 순번 중복 |

## 3. API

### 3.1 `POST /api/v1/sessions/{sessionId}/enrollments` (FR-04, 응답 필드 추가)

`EnrollmentResponse`에 `waitingNumber`(정수, `WAITING`일 때만, `CONFIRMED`면 `null`)를 추가한다. 기존 필드는 그대로다(하위 호환).

```json
{ "id": 11, "sessionId": 3, "employeeId": "E1002", "status": "WAITING", "waitingNumber": 4, "appliedAt": "..." }
```

### 3.2 `GET /api/v1/sessions/{sessionId}/enrollments/me` (신규, Q4)

순번은 다른 사람의 취소·강등으로 바뀌므로 현재 순번을 다시 볼 수 있어야 한다.

- 신청자: `X-Employee-Id` 헤더(D-21, `TODO(NFR-03)`). 검증은 POST와 같다.
- 응답 `200`: 그 회차의 내 **활성** 신청(`CONFIRMED`/`WAITING`)과 현재 `waitingNumber`.
- 트랜잭션: `@Transactional(readOnly = true)`, 락 없음(조회 시점 값).

| 상태 | code | 상황 |
|---|---|---|
| 400 | `BAD_REQUEST` | 헤더 누락·공백·20자 초과 |
| 404 | `NOT_FOUND` | 회차 없음, 또는 그 회차에 내 활성 신청 없음(`EnrollmentNotFoundException`) |

## 4. 엔티티·스키마 변경

### 4.1 마이그레이션 `V3__add_enrollment_waiting_key.sql` (V2는 수정하지 않음)

```sql
ALTER TABLE enrollment
    ADD COLUMN waiting_key  BIGINT      NULL,
    ADD COLUMN confirmed_at DATETIME(6) NULL;

-- FR-04로 이미 저장된 데이터 보정: 대기자는 신청 순서(id)대로 1부터, 확정자는 신청 시각을 확정 시각으로.
UPDATE enrollment e
    JOIN (SELECT id, ROW_NUMBER() OVER (PARTITION BY session_id ORDER BY id) AS rn
          FROM enrollment WHERE status = 'WAITING') w ON e.id = w.id
SET e.waiting_key = w.rn;
UPDATE enrollment SET confirmed_at = applied_at WHERE status = 'CONFIRMED';

-- 같은 회차 안에서 정렬 키 중복 금지(NULL은 여러 개 허용). 순번 계산과 max/min 조회에도 쓴다.
CREATE UNIQUE INDEX uk_enrollment_waiting_key ON enrollment (session_id, waiting_key);
```

- `confirmed_at`: D-03의 "가장 늦게 확정된 사람"을 판단하는 기준. 지금은 신청 즉시 확정만 있어 `applied_at`과 같지만, FR-06의 승급 확정은 시각이 다르다. 동률이면 `id`가 큰 쪽이 늦은 것으로 본다.
- 유일 인덱스 `(session_id, waiting_key)`는 외래 키 `session_id`의 선두 인덱스 역할도 한다(기존 `idx_enrollment_session_status`와 별개).

### 4.2 `Enrollment` 변경

- 필드 `waitingKey`(`Long`), `confirmedAt`(`Instant`).
- 생성은 정적 팩터리로 나눈다: `confirmed(session, employeeId, now)`(→ `confirmedAt = now`), `waiting(session, employeeId, now, waitingKey)`.
- 도메인 메서드 `demote(long waitingKey)`: `CONFIRMED → WAITING`, `confirmedAt = null`, 키 부여. (`promote`, `cancel`은 FR-06)
- 기존 public 생성자는 위 팩터리로 대체하고 사용처(서비스, 테스트)를 바꾼다.

### 4.3 `EnrollmentRepository` 추가

| 메서드 | 용도 |
|---|---|
| `Optional<Long> findMaxWaitingKey(Long sessionId)` / `findMinWaitingKey` (JPQL `max`/`min`, `status = WAITING`) | 키 부여 |
| `long countBySessionIdAndStatusAndWaitingKeyLessThan(Long sessionId, EnrollmentStatus status, Long key)` | 순번 계산(한 건) |
| `List<Enrollment> findBySessionIdAndStatusOrderByConfirmedAtDescIdDesc(Long sessionId, EnrollmentStatus status, Pageable limit)` | 강등 대상(가장 늦게 확정된 n명) |
| `Optional<Enrollment> findBySessionIdAndEmployeeIdAndStatusIn(...)` | 내 활성 신청 |

`EnrollmentResponse.of(Enrollment, Integer waitingNumber)`로 바꾼다.

## 5. 트랜잭션·동시성

### 5.1 신청 (`EnrollmentCommandService.enroll`) 변경

FR-04 흐름 ①~⑤는 그대로. ⑤의 판정 뒤에:
- `CONFIRMED`면 `Enrollment.confirmed(...)`
- `WAITING`이면 `key = findMaxWaitingKey(sessionId).orElse(0) + 1`로 `Enrollment.waiting(...)`
- 저장 후 `WAITING`이면 순번 = `countBy...WaitingKeyLessThan(key) + 1`

모두 회차 락 안이므로 같은 회차의 키 부여가 겹치지 않는다. 유일 인덱스 `uk_enrollment_waiting_key`는 최종 보장이며, 위반되면 버그이므로 그대로 500으로 둔다(정상 경로에서는 발생하지 않음).

### 5.2 정원 감소 시 강등 (D-03, Q2)

- **의존 방향:** `course`의 `CourseCommandService.updateSession`이 강등을 해야 하지만 `course → enrollment` 참조는 금지(D-22). `course`에 `SessionCapacityChangeHandler` 인터페이스(`void onCapacityChanged(CourseSession session, int oldCapacity, int newCapacity)`)를 두고 `enrollment`가 구현한다.
- **`updateSession` 변경 (최종 검토 W3 해소):**
  - `findSession`(락 없음) 대신 `findByIdForUpdate`로 회차 락을 첫 쿼리로 잡는다.
  - `@Transactional(isolation = READ_COMMITTED)`를 메서드에 지정한다.
  - `session.update(...)` 뒤에 정원이 줄었으면 핸들러를 호출한다.
- **핸들러 구현 (`enrollment/CapacityDecreaseDemoter`)**
  1. 확정 수 > 새 정원이면 초과 n명을 `confirmedAt` 내림차순(동률은 `id` 내림차순)으로 고른다.
  2. 이들을 "먼저 확정된 사람이 앞"이 되도록 오름차순으로 다시 정렬한다.
  3. 키를 `min(waiting_key)`(없으면 1) − n부터 차례로 `demote(key)`한다.
  - 정원 증가(D-02 승급)는 FR-06에서 같은 핸들러에 추가한다(Q3). 지금은 아무것도 하지 않는다.
- **락 순서:** 신청과 정원 변경 모두 회차 행 → 신청 행 순서로 잠그므로 둘 사이에 데드락이 없다.
- **시작 후:** D-17이 시작 후 정원 변경을 막으므로 시작 후 강등은 생기지 않는다.

### 5.3 내 순번 조회

읽기 전용, 락 없음. 순번은 조회 시점 값이다. 동시에 강등이 커밋되면 직후 조회에서 바뀐다.

## 6. 불변식 (테스트 기준)

같은 회차에 대해 언제나:
- I1 확정 수 ≤ 정원(정원 감소 직후 포함)
- I2 `WAITING`인 행만 `waiting_key`가 있고, 회차 안에서 서로 다르다
- I3 대기 순번은 1부터 빈틈없이 연속이다(계산 방식이므로 자동)
- I4 강등자는 기존 대기자보다 앞이고, 강등자끼리는 먼저 확정된 사람이 앞이다
- I5 대기자가 있으면 새 신청자는 맨 뒤 대기다(D-21, D-05)

## 7. 테스트 시나리오

### 7.1 단위 (`EnrollmentCommandServiceTest`, 신규 `CapacityDecreaseDemoterTest`, `CourseCommandServiceTest`)

| # | 시나리오 | 기대 |
|---|---|---|
| W1 | 대기자 없는 회차에 첫 대기 | 키 1, 순번 1 |
| W2 | 키 최댓값 5인 회차에 대기 | 키 6, 순번 = 앞선 대기 수 + 1 |
| W3 | 확정 | `waitingNumber` null, `confirmedAt` = now |
| D1 | 정원 5→3, 확정 5명(확정 시각 t1<…<t5) | t4, t5가 강등, t4가 앞, 기존 대기자보다 앞 |
| D2 | 기존 대기자 키 최솟값 1, 강등 2명 | 강등자 키 −1, 0 |
| D3 | 대기자 없음, 강등 1명 | 순번 1 |
| D4 | 확정 수 ≤ 새 정원 | 강등 없음 |
| D5 | 정원 증가 | 핸들러가 아무것도 하지 않음(FR-06 전) |
| D6 | 확정 시각 동률 | `id`가 큰 쪽이 먼저 강등 |
| U1 | `updateSession`이 `findByIdForUpdate`를 쓰고, 정원 감소 시에만 핸들러 호출 | 호출 검증 |

### 7.2 Repository (`EnrollmentRepositoryTest`, MariaDB)

- `uk_enrollment_waiting_key`: 같은 회차·같은 키 두 건 위반, 다른 회차 같은 키 허용, NULL 여러 건 허용
- `findMax/MinWaitingKey`가 `WAITING`만 보는지, 순번 계산 쿼리, 강등 대상 정렬
- V3 보정: 대기 3건을 V3 이전 형태로 넣은 상태는 만들기 어려우므로, 보정 SQL은 검토와 수동 확인으로 대신한다(Q6)

### 7.3 Controller (`EnrollmentControllerTest`)

- POST 응답에 `waitingNumber`(대기는 정수, 확정은 null)
- GET `/me`: 200 응답, 활성 신청 없음 404, 헤더 검증 400
- `OpenApiDocsTest`: `/me` 경로와 `waitingNumber` 스키마

### 7.4 동시성 (`EnrollmentConcurrencyTest`에 추가, 실제 MariaDB)

| # | 시나리오 | 기대 |
|---|---|---|
| C5 | 정원 1, 100명 동시 신청 | 대기 99명의 순번이 1~99로 중복·빈틈 없음, 키 중복 없음 |
| C6 | 정원 10·확정 10·대기 5인 회차에서 정원을 6으로 줄이는 요청과 신청 50건을 동시에 | 끝난 뒤 I1~I5 모두 성립, 예외 0 |

## 8. 질문 목록 (모두 제안대로 확정)

| # | 질문 | 확정 |
|---|---|---|
| Q1 | 대기 순서를 어떻게 표현하는가 | 정렬 키(`waiting_key`) 저장 + 순번은 조회 때 계산. 결정 사항 D-23으로 기록 |
| Q2 | D-03(정원 감소 시 강등)을 FR-05 범위에 넣는가 | 넣는다. 순서 표현과 바로 맞물리고, 최종 검토 W3(정원 변경 락)도 여기서 해소된다 |
| Q3 | D-02(정원 증가 시 승급)는 | FR-06에서. 승급 확정 시각·알림(FR-09)과 함께 다룬다. 그때까지는 D-21대로 새 신청이 대기한다 |
| Q4 | 내 순번 조회 API를 이번에 만드는가 | 만든다(`GET .../enrollments/me`). 순번은 바뀌므로 신청 응답만으로는 FR-05의 "알려 준다"가 부족하다. 목록형 내 신청 내역은 FR-07 |
| Q5 | 교육 시작 후 대기자(D-09)에게도 순번을 보여 주는가 | 그대로 보여 준다. 승급이 없다는 안내는 화면(FR-07)에서 |
| Q6 | V3에서 FR-04로 쌓인 데이터를 보정하는가 | 보정한다(대기 키는 id 순, 확정 시각은 신청 시각). 개발·테스트 DB에만 데이터가 있으므로 실행 후 수동 확인 |
| Q7 | 응답 필드 이름 | `waitingNumber`(대기만, 확정은 null) |

확정 내용은 `docs/requirements.md`의 D-23(대기 순서 표현), D-24(정원 감소 강등과 정원 변경 락)로 기록했다.
