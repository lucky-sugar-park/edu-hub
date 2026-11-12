# FR-06 신청 취소와 대기자 자동 승급 설계명세

- 상태: **승인됨 (2026-11-12)**. 8절의 질문은 모두 제안대로 확정했고 결정 사항 D-25·D-26으로 기록했다. 구현계획은 `docs/plans/fr-06.md`.
- 대상: FR-06 "신청을 취소하면 대기 1순위가 자동으로 신청 확정된다"
- 선행: FR-04(`FR-04-enrollment.md`, D-19~D-22), FR-05(`FR-05-waiting.md`, D-23·D-24)
- 범위(제안): 본인 신청 취소 API, 취소 시 승급, 정원 증가 시 승급(D-02). 알림(FR-09), 관리자 취소·명단(FR-08), 내 신청 목록(FR-07)은 하지 않는다.
- 브랜치: `feature/fr-04-fr-06`

## 1. 요구사항 정리

| 구분 | 내용 | 이 설계에서의 처리 |
|---|---|---|
| FR-06 | 취소하면 대기 1순위가 자동 확정 | 취소 API + 빈자리 채우기(승급) |
| D-01 | 대기자 취소는 승급 없이 뒤 순번만 당겨짐 | 키만 지움(D-23). 빈자리가 생기지 않으므로 승급 없음 |
| D-02 | 정원 증가분만큼 대기자가 순번대로 자동 승급 | `CapacityChangeEnrollmentHandler`의 증가 분기(지금 TODO)에서 같은 승급 로직 사용 |
| D-05 | 취소 후 같은 회차 재신청 가능 | 취소 행은 `CANCELLED`로 남고 유일 제약(D-19)에서 빠짐. 이미 FR-04에서 동작 |
| D-07 | 취소와 자동 승급은 교육 시작 전까지만 | 시작 후 취소는 409 `SESSION_STARTED`. 승급도 시작 전에만 |
| D-09 | 시작 후 대기자는 대기로 두고 승급 제외 | 시작 후에는 승급 경로가 생기지 않는다(취소 불가, 정원 변경 불가 D-17) |
| D-21 | 대기자가 있으면 새 신청은 대기 | 승급이 빈자리를 바로 채우므로 "빈자리가 있는데 대기자가 있는" 상태가 사라진다(I6) |
| NFR-01 | 동시성 정합성 | 취소·승급도 회차 행 락을 첫 쿼리로 |
| NFR-03 | 본인 신청만 취소 | 인증 전까지 `X-Employee-Id`로 본인 식별(D-21). 위조 위험은 FR-04 최종 검토 W2와 같다 |
| FR-07 | 내 신청 내역에 "취소" 표시 | 취소는 삭제하지 않고 상태·시각을 남긴다 |

## 2. 승급 규칙: 빈자리 채우기

취소와 정원 증가가 같은 규칙을 쓴다.

```
fillVacancies(session, now):
  if session.hasStarted(now): return                 // D-07, D-09
  vacancies = capacity - count(CONFIRMED)
  if vacancies <= 0: return
  targets = WAITING 중 waiting_key 오름차순 앞에서 vacancies명
  각 target.promote(now)                             // WAITING → CONFIRMED, confirmedAt = now, 키 삭제
```

- 확정 대기자 한 명이 취소하면 빈자리 1 → 대기 1순위가 확정된다.
- 대기자가 취소하면 빈자리가 생기지 않으므로 승급하지 않는다(D-01).
- 정원이 k만큼 늘면 빈자리 k → 앞에서 k명 승급(D-02).
- 승급자의 `confirmed_at`은 승급 시각이다. 나중에 정원이 줄면 승급자가 "가장 늦게 확정된 사람"으로 먼저 강등된다(D-03과 일관).
- 과정이 종료된 회차(D-15, 모집 종료)도 시작 전이면 취소와 승급을 허용한다(Q2). 승급은 신규 신청이 아니기 때문이다.

### 2.1 자기 복구 (최종 검토 W1, 2026-11-12 결정)

FR-05 기간에는 정원을 늘려도 승급이 없어서 "확정 < 정원이고 대기자가 있는" 회차가 남을 수 있다. 이 상태는 확정자 취소나 정원 증가가 다시 일어나기 전에는 풀리지 않는다. 그래서 회차를 다루는 모든 쓰기 경로가 회차 락을 잡은 직후 `fillVacancies`를 먼저 부른다.

| 경로 | 부르는 시점 |
|---|---|
| 신청 `enroll` | 중복 검사 뒤, 확정·대기 판정 전. 채운 뒤의 확정 수·대기 수로 판정한다 |
| 취소 `cancel` | 취소 저장 뒤, 취소한 사람의 상태와 관계없이 항상 |
| 정원 변경 | 정원이 바뀐 모든 경우. 증가면 승급(D-02), 감소면 강등(D-03) 뒤에도 빈자리가 남으면 채운다 |

정상 데이터에서는 이미 빈자리와 대기자가 함께 있지 않으므로 결과가 달라지지 않는다. 비용은 회차당 확정 수 조회 1회다.

한계: 중복 신청(409), 종료·시작 후 같은 예외 경로에서는 트랜잭션이 롤백되어 채우기도 함께 취소된다. 이런 회차는 다음 정상 요청(신청·취소·정원 변경) 때 정리된다.

## 3. API

### 3.1 `POST /api/v1/sessions/{sessionId}/enrollments/me/cancel` (신규, Q1)

본인의 활성 신청(`CONFIRMED`/`WAITING`)을 취소한다. 요청 본문 없음.

- 신청자: `X-Employee-Id`(검증은 기존 API와 같음), `TODO(NFR-03)`
- 응답 `200` `EnrollmentResponse`: 상태 `CANCELLED`, `waitingNumber` null. 누가 승급됐는지는 돌려주지 않는다(Q7).
- 회차 종료와 관리자 종료 API의 `.../close` 경로 형태를 따른다. 취소는 기록이 남는 상태 변경이라 `DELETE`를 쓰지 않는다.

| 상태 | code | 상황 |
|---|---|---|
| 400 | `BAD_REQUEST` | 헤더 누락·공백·20자 초과 |
| 404 | `NOT_FOUND` | 회차 없음, 또는 내 활성 신청 없음(이미 취소 포함, Q8) |
| 409 | `SESSION_STARTED` | 교육 시작 후(D-07). 확정·대기 모두 |
| 409 | `SESSION_CLOSED` | 종료된 회차(시작 후에만 종료되므로 사실상 `SESSION_STARTED`와 같은 경우) |
| 503 | `LOCK_TIMEOUT` | 회차 락 3초 초과 |

### 3.2 기존 API 영향

- `GET .../enrollments/me`: 취소 후에는 활성 신청이 없으므로 404. 응답 형식은 그대로다.
- `EnrollmentResponse.status`에 `CANCELLED` 값이 생긴다(생성 클라이언트의 enum에 추가됨).

## 4. 엔티티·스키마 변경

### 4.1 마이그레이션 `V4__add_enrollment_cancelled_at.sql`

```sql
ALTER TABLE enrollment ADD COLUMN cancelled_at DATETIME(6) NULL;
```

- `status`는 문자열 컬럼이라 `CANCELLED` 추가에 스키마 변경이 필요 없다.
- 생성 컬럼 `active_session_id`(D-19)는 활성 상태만 나열하므로 `CANCELLED`는 자동으로 유일 제약에서 빠진다.

### 4.2 `EnrollmentStatus`, `Enrollment`

- `EnrollmentStatus`에 `CANCELLED` 추가(`COMPLETED`는 FR-08).
- `Enrollment.cancel(Instant now)`: 활성 상태가 아니면 `IllegalStateException`. 상태 `CANCELLED`, `cancelledAt = now`, 키 삭제. `confirmedAt`은 기록으로 남긴다.
- `Enrollment.promote(Instant now)`: `WAITING`이 아니면 `IllegalStateException`. 상태 `CONFIRMED`, `confirmedAt = now`, 키 삭제.

### 4.3 서비스와 Repository

- `EnrollmentRepository`
  - `List<Enrollment> findBySessionIdAndStatusOrderByWaitingKeyAsc(Long sessionId, EnrollmentStatus status, Pageable pageable)`: 승급 대상
- 신규 `enrollment/VacancyFiller.java` (`@Component`, 패키지 전용 사용): 2절의 `fillVacancies(CourseSession, Instant)`. 취소 서비스와 `CapacityChangeEnrollmentHandler`가 함께 쓴다.
- `EnrollmentCommandService.cancel(Long sessionId, String employeeId)`: 4.4의 흐름
- FR-05 검토 제안 반영: `ACTIVE` 상수와 순번 계산이 두 서비스에 중복되어 있으므로 패키지 전용 헬퍼(`WaitingNumbers`)로 모은다. 세 번째 사용처가 생기기 때문이다.

### 4.4 취소 흐름

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public EnrollmentResponse cancel(Long sessionId, String employeeId) {
  CourseSession session = courseSessionRepository.findByIdForUpdate(sessionId)      // ① 첫 쿼리, 회차 락
      .orElseThrow(() -> new SessionNotFoundException(sessionId));
  Instant now = clock.instant();
  session.ensureCancellable(now);                                                   // ② SESSION_CLOSED / SESSION_STARTED (D-07)
  Enrollment enrollment = enrollmentRepository
      .findBySessionIdAndEmployeeIdAndStatusIn(sessionId, employeeId, ACTIVE)      // ③ 없으면 404
      .orElseThrow(() -> new EnrollmentNotFoundException(sessionId));
  enrollment.cancel(now);
  enrollmentRepository.saveAndFlush(enrollment);
  vacancyFiller.fillVacancies(session, now);                                        // ④ 빈자리 채우기(확정자 취소면 대기 1순위 승급, 자기 복구)
  // TODO(FR-09): 승급 알림
  return EnrollmentResponse.of(enrollment, null);
}
```

- `CourseSession.ensureCancellable(now)`는 `ensureEnrollable`과 같은 검사(종료 → 시작)다. 이름을 나눠 규칙 근거(D-07)를 드러낸다.
- ④는 취소한 사람이 확정자든 대기자든 항상 부른다. 대기자 취소는 빈자리를 만들지 않으므로 정상 데이터에서는 승급이 없다(D-01). 항상 부르는 것은 자기 복구를 위해서다(2.1절).

## 5. 트랜잭션·동시성

- 취소, 신청, 정원 변경 모두 **회차 행 락을 첫 쿼리로** 잡고 `READ_COMMITTED`다(D-20, D-24). 같은 회차의 세 작업은 직렬화되고, 잠그는 순서(회차 → 신청 행)가 같아 데드락이 없다.
- 승급 대상 선정과 상태 변경이 같은 락 안이므로 같은 대기자가 두 번 승급되거나 순서가 뒤바뀌지 않는다.
- 같은 사용자의 취소 두 건이 동시에 오면 첫 건이 취소하고, 둘째 건은 락을 기다린 뒤 활성 신청이 없어 404가 된다.
- 정원 증가(D-02)는 `updateSession`의 락 안에서 핸들러가 `fillVacancies`를 부른다.

## 6. 불변식 (FR-05의 I1~I5에 추가)

- I6 시작 전 회차에서 `확정 수 < 정원`이면 대기자가 0명이다(빈자리를 둔 채 대기시키지 않음)
- I7 승급은 대기 키 오름차순으로만 일어난다(뒤 순번이 앞 순번보다 먼저 확정되지 않음)
- I8 취소된 신청은 활성 제약과 순번 계산에서 빠지고, 같은 회차에 다시 신청할 수 있다(D-05)

## 7. 테스트 시나리오

### 7.1 단위

| # | 시나리오 | 기대 |
|---|---|---|
| X1 | 확정자 취소, 대기 3명 | 취소자 `CANCELLED`·`cancelledAt`, 대기 1순위 `CONFIRMED`·`confirmedAt`=now, 나머지 순번 1·2 |
| X2 | 대기자 취소 | 승급 없음, 뒤 순번만 당겨짐(D-01) |
| X3 | 확정자 취소, 대기 0명 | 승급 없음, 남은 자리 +1 |
| X4 | 시작 시각 = now에 취소 | 409 `SESSION_STARTED`, 변경 없음 |
| X5 | 종료된 회차 | 409 `SESSION_CLOSED` |
| X6 | 활성 신청 없음(이미 취소 포함) | `EnrollmentNotFoundException` |
| X7 | 과정 종료(D-15), 시작 전 | 취소·승급 허용 |
| X8 | 락 조회가 첫 쿼리 | `findByIdForUpdate` 호출, `findById` 미호출 |
| P1 | 정원 5→7, 확정 5·대기 3 | 대기 앞 2명 승급, 남은 대기 1명 순번 1 |
| P2 | 정원 증가분이 대기자보다 많음 | 대기 전원 승급 |
| P3 | 시작 후 `fillVacancies` 호출 | 아무것도 하지 않음(D-09) |
| E1 | `Enrollment.cancel`/`promote` | 상태·시각·키 변화, 잘못된 상태에서 예외 |

### 7.2 Repository, Controller

- `findBySessionIdAndStatusOrderByWaitingKeyAsc`가 키 오름차순(음수 키 포함), 페이지 크기만큼
- 취소 후 같은 회차·사번 재신청 저장 가능(D-05, 실제 상태 값으로)
- Controller: 200과 `status=CANCELLED`, 헤더 400, 404, 409, 503. `OpenApiDocsTest`에 `/me/cancel`

### 7.3 동시성 (`EnrollmentConcurrencyTest`, 개발자가 실행)

| # | 시나리오 | 기대 |
|---|---|---|
| C7 | 정원 10·확정 10·대기 20에서 확정자 10명 동시 취소 | 확정 10, 대기 앞 10명이 승급(키 순서), 남은 대기 순번 1~10 |
| C8 | 정원 5·확정 5·대기 10에서 확정자 취소 5건과 신규 신청 20건 동시 | I1~I8 성립, 신규 신청은 모두 대기 맨 뒤 |
| C9 | 정원 증가(5→8)와 확정자 취소 3건, 신규 신청 10건 동시 | I1~I8 성립 |
| C10 | 같은 사번 취소 5건 동시 | 성공 1, 나머지 404, 승급 정확히 1명 |

## 8. 질문 목록 (모두 제안대로 확정)

| # | 질문 | 확정 |
|---|---|---|
| Q1 | 취소 API 형태 | `POST .../enrollments/me/cancel` → 200 + 취소된 신청. 기록이 남는 상태 변경이라 `DELETE`(204) 대신 |
| Q2 | 과정이 종료(D-15 모집 종료)됐지만 시작 전인 회차의 취소·승급 | 허용. 승급은 신규 신청이 아니다 |
| Q3 | D-02(정원 증가 승급)를 이번에 하는가 | 한다. 같은 승급 규칙을 쓰고, FR-05의 TODO를 해소한다 |
| Q4 | 대기자도 교육 시작 후에는 취소할 수 없는가 | D-07을 글자 그대로 적용해 확정·대기 모두 시작 전까지만 |
| Q5 | 승급 알림(FR-09) | 이번에는 `TODO(FR-09)`만. 알림 기록 테이블은 FR-09에서 |
| Q6 | FR-05 검토 제안(ACTIVE·순번 계산 중복)을 이번에 정리하는가 | 정리한다. 취소 서비스가 세 번째 사용처다 |
| Q7 | 취소 응답에 승급자 정보를 넣는가 | 넣지 않는다(다른 사람 정보) |
| Q8 | 이미 취소한 신청을 다시 취소하면 | 404(활성 신청 없음). 409로 구분하지 않는다 |

확정 내용은 `docs/requirements.md`의 D-25(취소 API와 시작 후 취소 금지), D-26(빈자리 채우기 규칙)으로 기록했다.
