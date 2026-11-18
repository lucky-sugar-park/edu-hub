# FR-08 회차 명단 조회와 수료 처리 설계명세

- 상태: **승인됨 (2026-11-18)**. 1절의 결정 4개와 9절의 질문은 모두 확정했고 결정 사항 D-28·D-29로 기록했다. 구현계획은 `docs/plans/fr-08.md`.
- 대상: FR-08 "관리자는 회차별 신청자·대기자 명단을 보고 수료 처리한다"
- 선행: FR-04~07(D-19~D-27)
- 범위: 관리자용 회차 명단 API, 수료 처리 API, 관리자 사번 검사. 화면, 알림(FR-09), 수료 취소, Spring Security 도입, 기존 관리자 API의 권한 검사는 하지 않는다.
- 브랜치: `feature/fr-07-fr-08`

## 1. 요구사항 정리와 결정

| 구분 | 내용 | 이 설계에서의 처리 |
|---|---|---|
| FR-08 | 회차별 신청자·대기자 명단, 수료 처리 | 명단 GET, 수료 POST |
| D-04, D-16 | 회차 종료는 교육 완료를 뜻하고, 시작 후에만 종료할 수 있다 | 수료는 종료된 회차에서만(결정 2) |
| D-09 | 시작 후 남은 대기자는 대기로 둔다 | 수료 처리는 대기자를 바꾸지 않는다 |
| D-10 | 수료는 회차 종료 후에만. 수료 못 한 확정자는 확정으로 두고 미수료 상태는 없다 | `CONFIRMED → COMPLETED`만 있다 |
| D-18 | 관리자 API는 `/api/v1/admin` 아래 | 결정 4 |
| NFR-03 | 관리자만 | 결정 1, 403 |
| CLAUDE.md 6 | 수량 변경에는 락 | 수료도 회차 행 락을 첫 쿼리로(6절) |

**결정 (2026-11-18, 개발자 선택)**

1. **관리자 권한:** 설정의 관리자 사번 목록으로 검사한다.
   - 새 파라미터 어노테이션 `@CurrentAdmin String employeeId`를 둔다. 리졸버가 `@CurrentEmployee`와 같은 헤더 검사(400)를 한 뒤, 사번이 관리자 목록에 없으면 403 `FORBIDDEN`을 낸다.
   - 목록은 `eduhub.admin.employee-ids`이고, 값은 환경 변수 `EDUHUB_ADMIN_EMPLOYEE_IDS`(쉼표 구분)에서 받는다. 기본값이 비어 있으면 아무도 관리자가 아니다(안전한 기본값).
   - **FR-08의 새 엔드포인트에만 적용한다.** 기존 관리자 API(과정·회차 관리)와 관리자 화면은 그대로 둔다.
   - 사번은 여전히 헤더에서 오므로 **관리자 사번을 아는 사람은 헤더로 관리자 행세를 할 수 있다(W2).** 인증(D-11) 도입 때 리졸버를 역할 검사로 바꾼다.
2. **수료 시점:** D-10대로 회차가 **종료(CLOSED)된 뒤에만** 수료할 수 있다.
   - 시작 전이면 409 `SESSION_NOT_STARTED`를 낸다. 요청의 "시작일 이전이면 409"는 이것으로 충족한다.
   - 시작 후이지만 아직 종료하지 않았으면 409 `SESSION_NOT_CLOSED`(신규 code)를 낸다.
3. **중복 수료:** 이미 `COMPLETED`이면 실패 사유 `ALREADY_COMPLETED`로 돌려주고, 상태와 수료 시각은 바꾸지 않는다.
4. **경로:** `/api/v1/admin/sessions/{sessionId}/roster`, `/api/v1/admin/sessions/{sessionId}/completions`

## 2. 상태 전이

```
CONFIRMED ──complete(now)──▶ COMPLETED     (회차 CLOSED일 때만)
WAITING, CANCELLED, COMPLETED ──▶ 변화 없음, 실패 사유로 응답
```

- `Enrollment.complete(Instant now)`: `CONFIRMED`가 아니면 `IllegalStateException`이다. 상태는 `COMPLETED`, `completedAt = now`가 된다. `confirmedAt`은 그대로 남긴다.
- **수료 취소(되돌리기)는 두지 않는다**(9절 Q3).
- `COMPLETED`는 활성 상태(`CONFIRMED`, `WAITING`)가 아니므로, 생성 컬럼 `active_session_id`가 NULL이 되어 중복 신청 유일 제약에서 빠진다. 종료된 회차에는 신청할 수 없으므로(D-15, `ensureEnrollable`) 재신청 문제는 없다.
- FR-07 내 신청 목록에는 `COMPLETED`가 자동으로 나오고 `status=COMPLETED` 필터도 쓸 수 있다(D-27 예고대로).
- 회차의 내 신청 조회(`GET /sessions/{id}/enrollments/me`)는 활성 신청만 보므로, 수료한 사람은 404다. 회차가 종료된 뒤라 화면 흐름에 영향이 없다(9절 Q5).

## 3. API

### 3.1 `GET /api/v1/admin/sessions/{sessionId}/roster`

| 요청 | 내용 |
|---|---|
| 헤더 `X-Employee-Id` | 관리자 사번(필수) |

응답 200 `SessionRosterResponse`

| 필드 | 내용 |
|---|---|
| `sessionId`, `capacity`, `sessionStatus` | 회차 정보 |
| `confirmed` | 확정·수료자 목록. 정렬은 `confirmedAt` 오름차순, 같으면 id 오름차순. 각 항목은 `enrollmentId`, `employeeId`, `status`(`CONFIRMED`·`COMPLETED`), `confirmedAt`, `completedAt` |
| `waiting` | 대기자 목록. `waiting_key` 오름차순이고 각 항목은 `enrollmentId`, `employeeId`, `waitingNumber`, `appliedAt` |

- `waitingNumber`는 목록 순서의 1부터 매긴 값이다. 회차의 대기자를 키 순으로 모두 가져오므로 D-23의 "키가 더 작은 대기자 수 + 1"과 같은 값이다.
- 취소자는 넣지 않는다(9절 Q1).
- 페이지는 두지 않는다. 확정자는 정원 이하이고, 대기자 수는 운영상 작다(9절 Q2).
- 쿼리는 2개다. 회차 조회와, 상태가 `CONFIRMED`·`COMPLETED`·`WAITING`인 신청 한 번이다. 서비스에서 둘로 나눈다. 인덱스는 `idx_enrollment_session_status(session_id, status)`를 쓴다.
- 락은 없다(조회).

오류: 400(헤더), 403(관리자 아님), 404(회차 없음).

### 3.2 `POST /api/v1/admin/sessions/{sessionId}/completions`

요청 본문 `CompletionRequest`

```json
{ "employeeIds": ["E1001", "E1002"] }
```

- `employeeIds`는 1~200개이고 각 값은 공백 불가, 최대 20자다(`@NotEmpty @Size(max = 200)`, 요소 `@NotBlank @Size(max = 20)`).
- 요청 안의 중복은 첫 번째만 처리하고 나머지는 무시한다.

응답 200 `CompletionResponse`

```json
{
  "completed": ["E1001"],
  "failed": [{ "employeeId": "E1002", "reason": "NOT_CONFIRMED" }]
}
```

실패 사유(`CompletionFailureReason`)

| reason | 경우 |
|---|---|
| `NOT_CONFIRMED` | 그 회차의 신청이 `WAITING` |
| `ALREADY_COMPLETED` | 이미 `COMPLETED` |
| `CANCELLED` | 그 회차에 취소된 신청만 있다 |
| `NOT_ENROLLED` | 그 회차에 신청이 없다 |

- 일부만 실패해도 성공한 건은 반영한다(한 트랜잭션, 실패 건은 상태를 바꾸지 않을 뿐).
- 사원 한 명이 한 회차에 여러 행(취소 후 재신청)을 가질 수 있다. 판정 순서는 다음과 같다.
  1. 활성 행(`CONFIRMED`·`WAITING`, 최대 1개)
  2. `COMPLETED` 행
  3. `CANCELLED` 행
  4. 없음

오류 응답

| 코드 | code | 경우 |
|---|---|---|
| 400 | `BAD_REQUEST` | 헤더 오류, 본문 검증 실패 |
| 403 | `FORBIDDEN` | 관리자 아님 |
| 404 | `NOT_FOUND` | 회차 없음 |
| 409 | `SESSION_NOT_STARTED` | 교육 시작 전 |
| 409 | `SESSION_NOT_CLOSED` | 시작했지만 회차 미종료 |
| 503 | `LOCK_TIMEOUT` | 회차 락 대기 3초 초과 |

검사 순서는 다음과 같다. 403·400이 먼저 걸리면 서비스는 호출되지 않는다.
1. 헤더 검사(400)
2. 관리자 검사(403)
3. 본문 검증(400)
4. 회차 락과 존재 확인(404)
5. 상태(409)
6. 사원별 판정

### 3.3 OpenAPI

- 두 엔드포인트는 tag `EnrollmentAdmin`으로 묶고 `@Operation`과 위 응답 코드를 문서화한다.
- 헤더는 FR-07과 같은 방식으로 표기한다. 파라미터는 `@Parameter(hidden = true)`로 숨기고, 메서드에 `@Parameter(in = HEADER, ...)`를 단다.
- `OpenApiDocsTest`에 경로, 헤더, 응답 코드 단언을 추가한다.

## 4. 엔티티·스키마 변경

### 4.1 마이그레이션 `V5__add_enrollment_completed_at.sql`

```sql
ALTER TABLE enrollment ADD COLUMN completed_at DATETIME(6) NULL;
```

- 기존 V1~V4는 고치지 않는다.
- `status`는 varchar라 `COMPLETED` 추가에 스키마 변경이 필요 없다. 생성 컬럼 식(`status in ('CONFIRMED','WAITING')`)도 그대로 맞다.
- 새 외래 키가 없으므로 인덱스 추가도 없다.

### 4.2 코드

- `EnrollmentStatus`에 `COMPLETED`를 추가한다.
- `Enrollment`에 `completedAt` 필드, getter, `complete(Instant now)`를 추가한다.

### 4.3 남은 자리 계산 (기존 동작 변경)

- `EnrollmentSeatCounter`(D-22)는 지금 `CONFIRMED`만 센다. 그래서 수료 후 종료된 회차의 남은 자리가 다시 늘어난 것처럼 보인다.
- **`CONFIRMED`와 `COMPLETED`를 모두 자리를 차지한 신청으로 센다**(9절 Q4).
- `SessionSeatCounter` Javadoc을 "자리를 차지한 신청(확정·수료) 수"로 고친다.
- 신청(`EnrollmentCommandService`)·빈자리 채우기(`VacancyFiller`)·강등(`CapacityChangeEnrollmentHandler`)의 확정 수 계산은 바꾸지 않는다. 이 셋은 종료 전(대부분 시작 전) 회차에서만 동작하는데, 수료는 종료 후에만 생기므로 `COMPLETED`가 섞이지 않는다.

## 5. 계층과 파일

| 파일 | 변경 |
|---|---|
| `db/migration/V5__add_enrollment_completed_at.sql` | 신규 |
| `enrollment/EnrollmentStatus.java`, `Enrollment.java` | `COMPLETED`, `completedAt`, `complete()` |
| `enrollment/EnrollmentSeatCounter.java`, `EnrollmentRepository.java` | 확정·수료 합산 집계(상태 목록을 받는 쿼리) |
| `course/SessionSeatCounter.java` | Javadoc |
| `course/CourseSession.java` | `ensureCompletable(now)`: 시작 전이면 `SESSION_NOT_STARTED`, OPEN이면 `SESSION_NOT_CLOSED` |
| `common/CurrentAdmin.java`, `common/CurrentAdminArgumentResolver.java`, `common/AdminProperties.java`, `common/ForbiddenException.java` | 신규 |
| `common/WebConfig.java`, `common/GlobalExceptionHandler.java` | 리졸버 등록, 403 처리 |
| `enrollment/EnrollmentAdminController.java` | 신규. `/api/v1/admin` 아래 두 엔드포인트 |
| `enrollment/SessionRosterResponse.java`, `CompletionRequest.java`, `CompletionResponse.java`, `CompletionFailureReason.java` | 신규 DTO |
| `enrollment/EnrollmentQueryService.java` | `getRoster(sessionId)` |
| `enrollment/EnrollmentCommandService.java` (또는 신규 `CompletionService`) | `complete(sessionId, employeeIds)` |
| `application.yml`, `application-test.yml` | `eduhub.admin.employee-ids` |
| `.env.example` | `EDUHUB_ADMIN_EMPLOYEE_IDS` 이름만 추가(**개발자가 직접**. 이 파일은 Claude가 읽을 수 없다) |

- 계층: Controller → Service → Repository. `course`는 `enrollment`를 모르는 규칙을 지킨다. `CourseSession.ensureCompletable`은 회차 상태만 본다.
- 관리자 검사는 웹 계층(리졸버)에서 한다. 서비스는 권한을 모른다.

## 6. 트랜잭션·동시성

- 수료: `@Transactional(isolation = READ_COMMITTED)`이고, 첫 쿼리로 회차 행 `PESSIMISTIC_WRITE`(`findByIdForUpdate`, 3초)를 잡는다. 이후 그 회차에서 요청 사번들의 신청을 한 쿼리로 읽어 판정하고 변경한다.
- 같은 회차에 수료 요청이 두 건 동시에 오면 직렬화된다. 뒤의 요청은 앞에서 수료된 사람을 `ALREADY_COMPLETED`로 받는다.
- 종료된 회차에는 신청·취소·정원 변경이 모두 막혀 있어(`ensureOpen`) 수료와 경쟁하는 쓰기가 없다. 락은 규칙 일관성(수량 변경은 회차 락)을 위해 잡는다.
- 명단 조회는 readOnly이고 락이 없다.

## 7. 불변식 (I1~I8에 추가)

- I9 `COMPLETED`인 신청의 회차는 `CLOSED`이고, `completed_at`이 있다.
- I10 수료는 `CONFIRMED`에서만 일어나며 대기자·취소자의 상태를 바꾸지 않는다.
- I11 남은 자리 = 정원 − (확정 + 수료), 0 미만이면 0.

## 8. 테스트 시나리오

### 8.1 단위

| # | 시나리오 | 기대 |
|---|---|---|
| E1 | `Enrollment.complete` | 확정 → 수료, `completedAt`, `confirmedAt` 유지. 대기·취소·수료 상태에서는 예외 |
| S1 | `CourseSession.ensureCompletable` | 시작 전 → `SESSION_NOT_STARTED`, 시작 후 OPEN → `SESSION_NOT_CLOSED`, CLOSED → 통과 |
| A1 | 관리자 리졸버 | 목록에 있음 통과, 없음 403, 목록이 비어 있으면 모두 403, 헤더 누락·공백·21자 400 |

### 8.2 서비스·Repository (실제 MariaDB)

| # | 시나리오 | 기대 |
|---|---|---|
| C1 | 종료된 회차, 확정 2·대기 1·취소 1·미신청 1, 이미 수료 1 | completed 2건, failed `NOT_CONFIRMED`·`CANCELLED`·`NOT_ENROLLED`·`ALREADY_COMPLETED`, 대기자 상태 그대로 |
| C2 | 취소 후 재신청해 확정된 사원 | 활성 행이 수료되고 취소 행은 그대로 |
| C3 | 요청에 같은 사번 두 번 | 한 번만 처리 |
| C4 | 시작 전 회차 | 409 `SESSION_NOT_STARTED`, 변경 없음 |
| C5 | 시작 후 미종료 회차 | 409 `SESSION_NOT_CLOSED`, 변경 없음 |
| C6 | 없는 회차 | 404 |
| C7 | 락 조회가 첫 쿼리 | `findByIdForUpdate` 호출 |
| R1 | 명단 | 확정·수료는 `confirmedAt` 순, 대기는 키 순(음수 키 포함)이고 순번 1..n, 취소자 제외 |
| T1 | 수료 후 남은 자리 | 정원 − (확정 + 수료) |
| V5 | 마이그레이션 | `completed_at` 컬럼이 생기고 `ddl-auto: validate`가 통과 |

### 8.3 Controller·OpenAPI

- 명단 200, 수료 200(일부 실패 포함)
- 권한 없음 403(서비스 미호출), 헤더 400, 본문 검증 400(빈 목록, 201개, 21자 사번)
- 404, 409 두 가지, 503
- `OpenApiDocsTest`: 두 경로, 헤더, 응답 코드

## 9. 질문 목록 (모두 제안대로 확정)

| # | 질문 | 제안 |
|---|---|---|
| Q1 | 명단에 취소자를 넣는가 | 넣지 않는다. 확정·수료와 대기만 |
| Q2 | 명단에 페이지를 두는가 | 두지 않는다. 확정은 정원 이하이고 대기도 작다. 커지면 그때 추가 |
| Q3 | 수료 취소(되돌리기) | 두지 않는다. 잘못 처리했을 때의 정정은 후속 요구사항으로 남긴다 |
| Q4 | 수료자를 남은 자리 계산에 포함하는가 | 포함한다(4.3). 종료 회차의 남은 자리가 늘어나 보이지 않게 |
| Q5 | 수료한 사람의 회차별 내 신청 조회(`/sessions/{id}/enrollments/me`) | 지금처럼 활성 신청만 보여 수료자는 404. 수료 확인은 FR-07 목록으로 한다 |
| Q6 | 관리자 사번 목록의 dev 값 | `.env`의 `EDUHUB_ADMIN_EMPLOYEE_IDS`로 개발자가 넣는다. test 프로필은 `application-test.yml`에 테스트용 사번을 둔다 |
| Q7 | 결정 사항 기록 | 승인되면 D-28(수료 규칙·실패 사유·남은 자리 포함 범위)과 D-29(관리자 사번 목록 임시 권한)로 남긴다 |
