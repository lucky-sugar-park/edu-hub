# FR-04 수강 신청 설계명세

- 상태: **승인됨 (2026-11-12)**. 6절의 질문은 모두 제안대로 확정했고 결정 사항 D-19~D-22로 기록했다. 구현계획은 `docs/plans/fr-04.md`.
- 대상: FR-04 "회차에 수강 신청한다. 같은 회차에 중복 신청할 수 없다", NFR-01 "동시에 100명이 마지막 1자리에 신청해도 정원을 넘지 않는다"
- 범위: 신청 API와 동시성 처리. 대기 순번 부여(FR-05), 취소·승급(FR-06), 인증(NFR-03, D-11)은 하지 않는다. 다만 정원이 찼을 때 `WAITING`으로 저장할 자리는 남긴다.
- 위치: `backend/.../enrollment`(신규), `backend/.../course`의 `CourseSession`, `CourseSessionRepository`

## 1. 요구사항 정리

| 구분 | 내용 | 이 설계에서의 처리 |
|---|---|---|
| FR-04 | 회차에 신청, 같은 회차 중복 신청 금지 | 신청 API, DB 유일 제약 + 서비스 검사 |
| NFR-01 | 100명 동시 신청에도 정원 초과 금지 | 회차 행 비관적 락, 실제 MariaDB 동시성 테스트 |
| NFR-03 | 로그인한 사용자만 신청 | 이번에는 하지 않음. `employeeId`를 서비스 인자로 받고 Controller에 `TODO(NFR-03)` |
| NFR-04 | OpenAPI 문서 | `@Tag`, `@Operation`, 오류별 `@ApiResponse` |
| NFR-05 | 서비스 커버리지 70% | 서비스 단위 테스트 + 통합 테스트 |
| NFR-07 | 도메인별 패키지 | 신청은 `enrollment`, 회차 락 조회는 `course` |
| D-05 | 취소 후 재신청 가능 | 유일 제약은 "활성 신청"(`CONFIRMED`, `WAITING`)에만 건다 |
| D-06 | 같은 과정의 다른 회차 동시 신청 가능 | 유일 제약 단위는 회차 |
| D-08 | 교육 시작 후 신규 신청 금지 | 409 `SESSION_STARTED` |
| D-15 | 종료된 과정의 회차에는 신규 신청 금지 | 409 `COURSE_CLOSED` |
| CLAUDE.md 6 | 수량 변경은 락, 중복 방지는 DB 유일 제약 | 회차 행 `PESSIMISTIC_WRITE`, 생성 컬럼 유일 인덱스 |

## 2. API

### `POST /api/v1/sessions/{sessionId}/enrollments`

회차에 수강 신청한다. 요청 본문은 없다.

- 신청자: 임시로 요청 헤더 `X-Employee-Id`(질문 Q3)에서 받는다. Controller에 `// TODO(NFR-03): 인증 도입 후 로그인 사용자에서 꺼낸다` 주석을 남긴다. 헤더가 없거나 비어 있으면 400.
- OpenAPI: `@Tag(name = "Enrollment")`, `@Operation(summary = "회차에 수강 신청한다")`

응답 `201 Created` — `EnrollmentResponse`

```json
{ "id": 10, "sessionId": 3, "employeeId": "E1001", "status": "CONFIRMED", "appliedAt": "2026-11-12T01:00:00Z" }
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | long | 신청 id |
| `sessionId` | long | 회차 id |
| `employeeId` | string | 사번 |
| `status` | `CONFIRMED` \| `WAITING` | 정원 안이면 확정, 찼으면 대기(Q1) |
| `appliedAt` | Instant(UTC) | 신청 시각 |

대기 순번(`waitingNumber`)은 FR-05에서 응답에 추가한다.

### 오류 (본문은 `ErrorResponse(code, message)`)

| 상태 | code | 상황 | 검사 위치 |
|---|---|---|---|
| 400 | `BAD_REQUEST` | `X-Employee-Id` 누락·공백·20자 초과, `sessionId`가 숫자가 아님 | Controller / `GlobalExceptionHandler` |
| 404 | `NOT_FOUND` | 회차가 없음 (`SessionNotFoundException` 재사용) | 서비스 |
| 409 | `COURSE_CLOSED` | 과정이 종료됨(D-15) | `Course.ensureOpen()` |
| 409 | `SESSION_CLOSED` | 회차가 종료됨 | `CourseSession.ensureEnrollable(now)` |
| 409 | `SESSION_STARTED` | 교육이 시작됨(D-08) | `CourseSession.ensureEnrollable(now)` |
| 409 | `DUPLICATE_ENROLLMENT` | 같은 회차에 활성 신청(`CONFIRMED`, `WAITING`)이 이미 있음 | 서비스 검사 + DB 유일 제약 |
| 503 | `LOCK_TIMEOUT` | 회차 락을 3초 안에 얻지 못함(Q6) | `GlobalExceptionHandler` |

검사 순서: 회차 존재 → 회차 상태(종료, 시작) → 과정 종료 → 중복 → 정원. 상태 오류가 중복보다 먼저 나온다.

## 3. 엔티티·스키마 변경

### 3.1 마이그레이션 `V2__create_enrollment.sql` (신규, V1은 수정하지 않음)

```sql
CREATE TABLE enrollment (
    id                BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id        BIGINT      NOT NULL,
    employee_id       VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL,
    applied_at        DATETIME(6) NOT NULL,
    -- 활성 신청(CONFIRMED, WAITING)일 때만 session_id, 아니면 NULL. 유일 인덱스는 NULL을 여러 개 허용한다.
    active_session_id BIGINT AS (IF(status IN ('CONFIRMED', 'WAITING'), session_id, NULL)) PERSISTENT,
    CONSTRAINT fk_enrollment_session FOREIGN KEY (session_id) REFERENCES course_session (id),
    CONSTRAINT uk_enrollment_active UNIQUE (active_session_id, employee_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 정원 계산(회차별 확정 수)과 외래 키 인덱스를 겸한다.
CREATE INDEX idx_enrollment_session_status ON enrollment (session_id, status);
-- FR-07 내 신청 내역 조회용.
CREATE INDEX idx_enrollment_employee ON enrollment (employee_id);
```

**부분 유일 인덱스 대신 생성 컬럼을 쓰는 이유 (Q2):** MariaDB는 `WHERE` 조건이 붙은 부분 인덱스를 지원하지 않는다. 활성 상태일 때만 값이 있는 생성 컬럼에 유일 인덱스를 걸면 같은 효과가 난다. 취소된 신청은 `active_session_id`가 NULL이 되어 제약에서 빠지므로 재신청(D-05)이 가능하다.

- `CANCELLED`, `COMPLETED` 상태는 FR-06, FR-08에서 쓴다. 생성 컬럼 식은 활성 상태만 나열하므로 상태가 늘어도 바꿀 필요가 없다.
- 대기 순번 컬럼은 FR-05에서 `V3`로 추가한다. D-03(강등자가 기존 대기자보다 앞) 때문에 순번 표현 방식을 FR-05 설계에서 정한다.

### 3.2 `enrollment` 패키지 (신규)

| 파일 | 내용 |
|---|---|
| `EnrollmentStatus` | `CONFIRMED`, `WAITING` (FR-06·FR-08에서 `CANCELLED`, `COMPLETED` 추가) |
| `Enrollment` | `id`, `session`(`@ManyToOne(LAZY, optional = false)` → `CourseSession`), `employeeId`, `status`(STRING + `@JdbcTypeCode(VARCHAR)`), `appliedAt`. `active_session_id`는 매핑하지 않는다. 생성자 `Enrollment(CourseSession, String employeeId, EnrollmentStatus, Instant appliedAt)` |
| `EnrollmentRepository` | `long countBySessionIdAndStatus(Long sessionId, EnrollmentStatus status)`, `boolean existsBySessionIdAndEmployeeIdAndStatusIn(Long sessionId, String employeeId, Collection<EnrollmentStatus> statuses)` |
| `EnrollmentResponse` | record + `from(Enrollment)` |
| `DuplicateEnrollmentException` | `ConflictException("DUPLICATE_ENROLLMENT", ...)` 하위 클래스 |
| `EnrollmentCommandService` | `enroll(Long sessionId, String employeeId)` |
| `EnrollmentController` | 위 API |

### 3.3 `course` 패키지 변경

| 파일 | 변경 |
|---|---|
| `CourseSessionRepository` | `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))` + `@Query("select s from CourseSession s where s.id = :id")` 인 `Optional<CourseSession> findByIdForUpdate(Long id)` |
| `CourseSession` | `ensureEnrollable(Instant now)`: 종료면 409 `SESSION_CLOSED`, 시작했으면(`hasStarted`) 409 `SESSION_STARTED` |

- 락 조회에 `join fetch s.course`를 붙이지 않는다. 붙이면 `FOR UPDATE`가 과정 행까지 잠가 같은 과정의 다른 회차 신청(D-06)까지 직렬화된다.
- 의존 방향은 `enrollment → course` 한 방향만 둔다.

## 4. 트랜잭션·동시성

### 4.1 `EnrollmentCommandService.enroll` 흐름

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public EnrollmentResponse enroll(Long sessionId, String employeeId) {
  CourseSession session = courseSessionRepository.findByIdForUpdate(sessionId)   // ① 트랜잭션의 첫 쿼리
      .orElseThrow(() -> new SessionNotFoundException(sessionId));
  Instant now = clock.instant();
  session.ensureEnrollable(now);                                                  // ② SESSION_CLOSED / STARTED
  session.getCourse().ensureOpen();                                               // ③ COURSE_CLOSED (지연 로딩, 락 없음)
  if (enrollmentRepository.existsBySessionIdAndEmployeeIdAndStatusIn(...ACTIVE)) // ④ DUPLICATE_ENROLLMENT
    throw new DuplicateEnrollmentException(sessionId);                            //   메시지에 사번을 넣지 않는다
  long confirmed = enrollmentRepository.countBySessionIdAndStatus(sessionId, CONFIRMED); // ⑤
  long waiting = enrollmentRepository.countBySessionIdAndStatus(sessionId, WAITING);     //   대기자 앞지르기 방지(D-21)
  EnrollmentStatus status = (confirmed < session.getCapacity() && waiting == 0) ? CONFIRMED : WAITING;
  Enrollment saved = saveAndFlush(new Enrollment(session, employeeId, status, now)); // ⑥ 유일 제약 위반 → DUPLICATE_ENROLLMENT
  return EnrollmentResponse.from(saved);
}                                                                                  // ⑦ 커밋 시 락 해제
```

- **트랜잭션 경계:** `enroll` 메서드 하나. 락은 ①에서 얻고 커밋·롤백 때 풀린다. Controller는 트랜잭션 밖에서 서비스만 호출한다.
- **락 범위:** 같은 회차 신청만 직렬화된다. 다른 회차 신청은 서로 기다리지 않는다.
- **격리 수준을 `READ_COMMITTED`로 둔다:** MariaDB 기본값인 `REPEATABLE READ`에서는 트랜잭션의 첫 일반 읽기 시점에 스냅숏이 고정된다. 락보다 먼저 일반 읽기가 하나라도 들어가면, ⑤의 확정 수가 앞 트랜잭션이 커밋한 신청을 보지 못해 정원을 넘을 수 있다. `READ_COMMITTED`는 읽을 때마다 최신 커밋을 보므로 이 위험이 없다. ①을 첫 쿼리로 두는 것과 함께 이중으로 막는다.
- **중복 방지:** 같은 회차 신청은 락으로 직렬화되므로 ④에서 걸러진다. ⑥의 DB 유일 제약은 락을 거치지 않는 경로(향후 코드, 수동 입력)에 대한 최종 보장이다. `saveAndFlush`에서 난 `DataIntegrityViolationException` 중 `uk_enrollment_active` 위반만 `DuplicateEnrollmentException`으로 바꾼다.
- **락 대기 시간:** 3초(Q6). 넘으면 Hibernate가 `PessimisticLockException` 또는 `LockTimeoutException`을 던지고, `GlobalExceptionHandler`가 503 `LOCK_TIMEOUT`으로 바꾼다. 100명 동시 신청은 한 건이 수 ms라 3초 안에 끝나는 것을 동시성 테스트로 확인한다.
- **관리자의 정원 변경과의 경합:** FR-03 `updateSession`은 락 없이 회차를 읽고 고친다. 신청 트랜잭션이 행 락을 잡고 있으면 관리자의 `UPDATE`는 커밋까지 기다리므로 정원을 넘지는 않는다. 다만 신청이 읽은 정원이 커밋 직후 줄어들 수 있으며, 이 처리(D-03)는 FR-05·FR-06에서 정원 변경에도 같은 락을 걸 때 다룬다.

### 4.2 남은 자리 표시 (Q5)

`SessionResponse.remainingSeats`는 지금 정원과 같은 값을 돌려준다. 신청이 생기면 실제와 달라진다. 이번 범위에 넣을지 질문한다. 넣는다면 `EnrollmentRepository`에 회차 id 목록별 확정 수를 한 번에 세는 `group by` 쿼리를 두어 N+1을 피한다(NFR-02). 이 경우 `course → enrollment` 역방향 의존이 생기므로 `course` 패키지에 확정 수 조회용 인터페이스를 두고 `enrollment`가 구현하는 방식을 쓴다.

## 5. 테스트 시나리오

### 5.1 `EnrollmentCommandServiceTest` (단위, Mockito, `Clock.fixed`)

| # | 시나리오 | 기대 |
|---|---|---|
| S1 | 확정 수 < 정원 | `CONFIRMED` 저장, 응답 상태 `CONFIRMED` |
| S2 | 확정 수 = 정원 | `WAITING` 저장 |
| S3 | 정원 1, 확정 0 (경계) | `CONFIRMED` |
| S4 | 회차 없음 | `SessionNotFoundException` |
| S5 | 회차 종료 | 409 `SESSION_CLOSED`, 저장 안 함 |
| S6 | 시작 시각 = 현재 (경계, D-08) | 409 `SESSION_STARTED` |
| S7 | 과정 종료(D-15) | 409 `COURSE_CLOSED` |
| S8 | 같은 회차 활성 신청 있음 | 409 `DUPLICATE_ENROLLMENT`, 저장 안 함 |
| S9 | 저장 중 유일 제약 위반 | `DuplicateEnrollmentException`으로 변환 |
| S10 | 락 조회 메서드(`findByIdForUpdate`)를 쓰는지 | `findById`가 아니라 `findByIdForUpdate` 호출 검증 |
| S12 | 확정 수 < 정원이지만 대기자가 있음(정원 증가 직후, 최종 검토 W1) | `WAITING` 저장 |

### 5.2 `EnrollmentControllerTest` (`@WebMvcTest`)

- 201과 응답 JSON, `X-Employee-Id`가 서비스로 전달되는지
- 헤더 누락·공백 → 400 `BAD_REQUEST`, 서비스 호출 없음
- 서비스 예외별 상태: 404 `NOT_FOUND`, 409 각 code, 503 `LOCK_TIMEOUT`

### 5.3 `EnrollmentRepositoryTest` (`@DataJpaTest`, MariaDB `eduhub_test`)

- 같은 회차·같은 사번 `CONFIRMED` 두 건 저장 → 유일 제약 위반
- 같은 회차·같은 사번이라도 한 건이 활성 상태가 아니면 저장 가능(D-05 대비). `CANCELLED` 상태가 아직 없으므로 네이티브 `UPDATE`로 상태를 바꿔 검증한다.
- 같은 사번이 같은 과정의 다른 회차에 신청 가능(D-06)
- `countBySessionIdAndStatus`, `existsBy...StatusIn` 결과

### 5.4 `EnrollmentConcurrencyTest` (NFR-01, `@SpringBootTest`, 실제 MariaDB)

테스트 클래스에 `@Transactional`을 붙이지 않는다(각 스레드가 자기 트랜잭션으로 커밋해야 함). 시작 전에 회차를 만들고, 끝나면 만든 신청·회차·과정을 지운다.

| # | 시나리오 | 기대 |
|---|---|---|
| C1 | 정원 1, 사번이 다른 100명이 `CountDownLatch`로 동시에 신청 | `CONFIRMED` 정확히 1건, `WAITING` 99건, 예외 0건 |
| C2 | 정원 10, 이미 9명 확정, 100명 동시 신청(마지막 1자리) | `CONFIRMED` 정확히 10건 |
| C3 | 같은 사번 10건 동시 신청 | 활성 신청 1건, 나머지 9건은 `DUPLICATE_ENROLLMENT` |
| C4 | 같은 과정의 서로 다른 두 회차에 각각 50명 동시 신청 | 각 회차 정원대로 확정, 서로 영향 없음 |

- 스레드 풀 32, 전체 제한 시간 30초. HikariCP 기본 풀(10)이 병목이 되어도 결과는 같아야 한다.
- 실패 시 원인 파악을 위해 상태별 건수와 예외 종류별 건수를 단언 메시지에 함께 남긴다.

### 5.5 `OpenApiDocsTest`

- `/api/v1/sessions/{sessionId}/enrollments`의 `post`, 응답 `201`, `404`, `409`, `503`

## 6. 질문 목록 (모두 제안대로 확정)

| # | 질문 | 확정 |
|---|---|---|
| Q1 | 정원이 찼을 때 이번 FR-04에서 `WAITING`으로 **저장까지** 하는가, 409 `SESSION_FULL`로 거절하고 저장은 FR-05에서 하는가 | `WAITING`으로 저장. 순번은 FR-05에서 부여하고, 그 전까지는 신청 순서(`id`)로 정렬할 수 있다 |
| Q2 | 부분 유일 인덱스를 MariaDB가 지원하지 않으므로 생성 컬럼(`active_session_id`) + 유일 인덱스로 대신해도 되는가 | 생성 컬럼 방식. 결정 사항 D-19로 기록 |
| Q3 | Controller가 `employeeId`를 어디서 받는가 | 헤더 `X-Employee-Id`. 인증 도입 시 헤더만 걷어 내면 된다. 본문이나 경로에 넣으면 API 계약이 바뀐다 |
| Q4 | 사번 형식(길이, 문자) | 문자열, 최대 20자. 형식 검사는 하지 않음 |
| Q5 | `remainingSeats`를 실제 확정 수로 바꾸는 작업을 이번 범위에 넣는가 | 넣는다. 신청이 생기면 상세 화면의 남은 자리가 틀리기 때문. 대신 4.2의 방식으로 N+1 없이 |
| Q6 | 락 대기 시간과 초과 시 응답 | 3초, 503 `LOCK_TIMEOUT`(재시도 가능). 409는 "상태 때문에 불가"에 쓰고 있어 구분한다 |

확정 내용은 `docs/requirements.md`의 D-19(활성 신청 유일 제약), D-20(신청 트랜잭션의 락·격리 수준), D-21(정원 초과 시 대기 저장과 임시 신청자 식별), D-22(남은 자리 계산)로 기록했다.
