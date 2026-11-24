# FR-09 신청 확정·대기자 승급 알림 기록 설계명세

- 상태: **승인됨 (2026-11-24), 같은 날 개정**. 8절의 질문은 제안대로 확정했고 결정 사항 D-32로 기록했다. 구현계획은 `docs/plans/fr-09.md`.
- 개정(2026-11-24): T3 뒤 전체 테스트에서 A안(AFTER_COMMIT + REQUIRES_NEW)이 커넥션 풀 교착을 일으켜(동시성 테스트 C4·C7 30초 초과) 개발자 결정으로 **BEFORE_COMMIT(같은 트랜잭션 저장)** 으로 바꿨다. 2절 "개정", 4절, 6절, 7절 참고. Q1의 답도 B 계열로 바뀌었다.
- 대상: FR-09 "신청 확정·대기 승급 시 알림을 남긴다 (처음에는 로그·DB 기록)" (5주차, 선택)
- 선행: FR-04(D-20·D-21), FR-06(D-26 빈자리 채우기·자기 복구), D-24(정원 감소 강등), D-30(인증)
- 범위: 확정·승급 이벤트 발행, 커밋 직전 같은 트랜잭션에서 알림 행 저장과 INFO 로그(개정), `notification` 테이블 마이그레이션.
- 하지 않는 것: 메일·메신저 실제 발송, 알림 조회 API, 프런트 화면, 강등·취소·수료 알림(Q4).
- 브랜치: `feature/fr-09-notification` (main `921e85d`, `feature/mfe-split` 병합(PR #6) 뒤에서 땄다)

## 1. 요구사항 정리

| 구분 | 내용 | 이 설계에서의 처리 |
|---|---|---|
| FR-09 | 신청 확정·대기 승급 시 알림 | 확정 1건(신청 즉시 확정 또는 승급)마다 알림 1행 + INFO 로그 |
| D-26 | 승급은 `VacancyFiller` 한 곳. 신청·취소·정원 변경(증가·감소 후 자기 복구)이 모두 이걸 부른다 | 승급 이벤트는 `VacancyFiller`에서만 발행 → 세 경로가 자동으로 덮인다 |
| D-21 | 정원이 남아도 대기자가 있으면 새 신청은 대기 | 대기로 저장된 신청은 알림 없음. 나중에 승급될 때 `PROMOTED` 알림 |
| D-24 | 정원 감소 강등 | 알림 없음(Q4). 강등 뒤 같은 트랜잭션의 자기 복구 승급은 알림 있음 |
| D-20 | 회차 락 3초, 락 안 작업 최소화 | (개정) 알림 저장은 커밋 직전 락 안에서 하므로 INSERT 1~n건만큼 락 보유 시간이 늘어난다(6절) |
| NFR-07 | 도메인 패키지 분리 | 새 패키지 `notification`. `enrollment`는 `notification`을 모른다(이벤트만 발행) |

코드 확인 결과: 사용자가 말한 `EnrollmentService.java`는 없고, 신청·취소는 `enrollment/EnrollmentCommandService`, 승급은 `enrollment/VacancyFiller`, 정원 변경은 `enrollment/CapacityChangeEnrollmentHandler`(→ `VacancyFiller`)다. `EnrollmentCommandService.cancel`에 `// TODO(FR-09): 승급 알림`이 있다.

## 2. 방식 선택: 이벤트 + 커밋 뒤 저장 (제안 A) vs 같은 트랜잭션 저장 (대안 B)

| | A. `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` (지시안, **제안**) | B. 같은 트랜잭션에서 저장 (이벤트 없이 직접 호출 또는 동기 `@EventListener`) |
|---|---|---|
| 롤백 시 알림 | 저장 안 됨 (이벤트가 버려짐) | 저장 안 됨 (함께 롤백) |
| 알림 저장 실패 시 | 신청은 성공, 알림만 유실(ERROR 로그) | **신청까지 실패**(500) |
| 커밋 후 서버가 죽으면 | 알림 유실 가능 | 유실 없음 |
| 회차 락 보유 시간 | 늘지 않음 | INSERT 1~n건만큼 늘어남 |
| 커넥션 | 커밋 뒤 잠깐 2개(바깥 커넥션 반환 전 `REQUIRES_NEW`) | 1개 |
| 나중의 실제 발송(메일) | 같은 리스너에 붙이면 됨(커밋 뒤라야 하므로 맞는 자리) | 알림 테이블을 아웃박스로 읽는 발송기 필요 |
| 코드량 | 이벤트 record + 발행 2곳 + 리스너 | 저장 호출 2곳 + 저장 서비스 |

**처음 제안: A.** FR-09는 선택 기능이므로 알림 저장 실패가 핵심 기능인 신청을 막으면 안 되고, 회차 락(3초, D-20) 안의 작업을 늘리지 않는 편이 낫다. 유실 가능성은 "처음에는 로그·DB 기록" 단계에서 받아들이고 ERROR 로그로 추적한다. 반대로 "알림이 절대 빠지면 안 된다"가 요구라면 B(아웃박스)가 더 단순하고 정확하다 → Q1.

### 개정: A안의 커넥션 교착과 BEFORE_COMMIT 채택

- 원인: AFTER_COMMIT 리스너는 `afterCompletion`에서 실행되는데(spring-tx 6.2.19 `TransactionalApplicationListenerSynchronization`), 이때 바깥 트랜잭션의 커넥션은 아직 반환되지 않았다(반환은 그 뒤 `doCleanupAfterCompletion`). 그래서 `REQUIRES_NEW`가 커넥션을 하나 더 요청한다. 동시 요청이 풀 크기(Hikari 10) 이상이면 모든 스레드가 커넥션 1개를 쥔 채 두 번째를 기다려 `connectionTimeout`(30초)까지 멈춘다. "지금 규모에서는 문제 없다"는 6절의 처음 판단은 틀렸다.
- 결정: 이벤트 구조(3절)는 유지하고 리스너만 `@TransactionalEventListener(phase = BEFORE_COMMIT)`으로 바꿔 **신청 트랜잭션 안에서** 저장한다(`REQUIRES_NEW` 없음). 커넥션 1개, 커밋 직전에만 실행되므로 롤백·rollback-only 트랜잭션에서는 실행되지 않는다.
- 받아들인 대가: 알림 저장이 실패하면 신청(또는 취소·정원 변경)도 롤백된다. 회차 락 안에서 INSERT 1~n건이 늘어난다. 커밋 후 유실은 없다(아웃박스와 같은 성질). `@Async` + AFTER_COMMIT은 실행기 설정·큐·종료 시 유실·비동기 테스트 부담 때문에 택하지 않았다.

## 3. 이벤트와 발행 지점

```java
// enrollment 패키지 (public). notification이 이 타입에만 의존한다.
public record EnrollmentConfirmedEvent(
    Long enrollmentId, String employeeId, Long sessionId, Reason reason) {
  public enum Reason { APPLIED, PROMOTED }
}
```

| 지점 | 조건 | reason |
|---|---|---|
| `EnrollmentCommandService.enroll` | 저장한 신청이 `CONFIRMED`일 때(대기면 발행 안 함). `saveAndFlush` 뒤라 id가 있다 | `APPLIED` |
| `VacancyFiller.fillVacancies` | `saveAllAndFlush(promoted)` 뒤 승급자마다 1건 | `PROMOTED` |

- 발행은 `ApplicationEventPublisher.publishEvent`. 발행 시점에는 리스너가 실행되지 않고, 트랜잭션이 커밋되기 직전에 같은 트랜잭션에서 실행된다(개정).
- `VacancyFiller`가 `MANDATORY`이고 `enroll`도 트랜잭션 안이므로 발행은 항상 트랜잭션 안에서 일어난다. `fallbackExecution`은 기본값(false)으로 둔다.
- `cancel`의 `TODO(FR-09)` 주석은 지운다(승급 알림은 `VacancyFiller`가 낸다).
- 한 트랜잭션 안에서 승급 → 같은 행 강등은 일어나지 않는다(정원 변경은 강등 뒤 채우기 순서). 따라서 "알림 후 곧바로 대기로 돌아간" 사람은 같은 요청 안에서는 생기지 않는다. 나중 요청의 강등은 Q4.

## 4. 리스너

```java
// notification 패키지
@Component
class NotificationListener {
  @TransactionalEventListener(phase = BEFORE_COMMIT)
  public void on(EnrollmentConfirmedEvent event) {
    // Notification 저장 + log.info("알림 기록(커밋 전): employeeId={}, type={}, enrollmentId={}, sessionId={}")
  }
}
```

- (개정) 리스너에 `@Transactional`을 붙이지 않는다. `repository.save`가 커밋 직전의 신청 트랜잭션에 참여한다(같은 커넥션).
- 실패 처리(개정): 예외를 잡지 않는다. 저장소 프록시가 참여 트랜잭션을 rollback-only로 표시하므로 잡아도 커밋은 실패하고, 잡으면 원인만 가려진다. 예외는 커밋을 호출한 서비스 밖으로 전파되어 신청이 롤백되고 500(또는 해당 예외 응답)이 된다.
- 로그(최종 검토 W2): INFO 로그 `알림 기록(커밋 전)`은 커밋 직전에 남는다. 그 뒤 `doCommit`(남은 변경 flush, 커밋)이 실패하면 신청과 알림이 함께 롤백되어 로그만 남을 수 있다. 알림 존재 여부의 기준은 로그가 아니라 `notification` 테이블이다.
- 동기 실행(같은 요청 스레드)이다. `@Async`는 쓰지 않는다(테스트·운영 단순성, 지연은 INSERT 1건 수준).
- 생성 시각은 `Clock` 빈으로 정한다(backend 규칙).

## 5. 엔티티·스키마 변경

`V6__create_notification.sql`:

```sql
CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    employee_id VARCHAR(20)  NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    message     VARCHAR(500) NOT NULL,
    created_at  DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- 나중의 "내 알림" 조회용.
CREATE INDEX idx_notification_employee ON notification (employee_id);
```

- 외래 키 없음(사원 테이블이 없고, 지시된 컬럼에 신청·회차 id가 없다 → Q5). 그래서 FK 인덱스 규칙은 해당 없음.
- 엔티티 `notification/Notification`(`@Enumerated(STRING) NotificationType type`), `notification/NotificationRepository`.
- `NotificationType`: `ENROLLMENT_CONFIRMED`(APPLIED), `WAITING_PROMOTED`(PROMOTED) (Q3).
- 메시지(Q2, 기본안 — 추가 조회 없음):
  - APPLIED: `회차 {sessionId} 수강 신청이 확정되었습니다.`
  - PROMOTED: `회차 {sessionId} 대기에서 수강 확정으로 승급되었습니다.`

## 6. 트랜잭션·동시성

- 신청·취소·정원 변경 트랜잭션은 바뀌지 않는다(락 순서, `READ_COMMITTED`, `MANDATORY` 그대로). 추가되는 것은 발행 호출뿐이다.
- 알림 INSERT는 수량을 바꾸지 않고 유일 제약도 없으므로 락이 필요 없다(CLAUDE.md 6번 해당 없음).
- 롤백되는 모든 경로(중복 신청 409, 유일 제약 위반, 락 타임아웃 503, 종료·시작 후 409, 예기치 못한 예외)에서 그 트랜잭션에서 발행된 이벤트(자기 복구 승급분 포함)는 버려진다.
- (개정) 리스너는 커밋 직전 같은 트랜잭션·같은 커넥션에서 INSERT하므로 추가 커넥션이 없다. 회차 락은 그 INSERT만큼 더 오래 잡힌다(승급 n명이면 n건). 처음 A안은 커밋 뒤 커넥션을 하나 더 요구해 동시 요청에서 풀 교착을 일으켰다(2절 개정).
- 롤백되는 경로에서는 BEFORE_COMMIT 리스너가 실행되지 않는다(rollback-only면 Spring이 `beforeCommit` 없이 롤백한다).

## 7. 테스트 시나리오 (실제 MariaDB `eduhub_test`)

통합 테스트는 커밋이 일어나야 하므로 클래스에 `@Transactional`을 붙이지 않는다(`VacancySelfHealIntegrationTest`와 같은 방식). `notification`에는 회차 id가 없으므로 테스트 사번에 클래스 고유 접두어(예: `N9-`)를 쓰고 그 사번의 알림만 지운다.

| ID | 시나리오 | 기대 |
|---|---|---|
| N1 | 빈 회차에 신청 → 확정 | 그 사번 알림 1건, type `ENROLLMENT_CONFIRMED` (**완료 기준**) |
| N2 | `TransactionTemplate` 안에서 `enroll`(확정) 후 예외로 롤백 | 신청 0건, 알림 0건 (**완료 기준**) |
| N3 | 정원이 찬 회차에 신청 → 대기 | 알림 0건 |
| N4 | 확정자 취소 → 대기 1순위 승급 | 승급자 알림 1건 `WAITING_PROMOTED`, 취소자 알림 없음 |
| N5 | 정원 증가로 대기 2명 승급 | 2명 각 1건 |
| N6 | (대체) 오래된 상태(확정 1 < 정원 4, 대기 2)에서 `TransactionTemplate` 안의 `enroll`이 자기 복구로 대기자를 승급시킨 뒤 예외로 롤백 | 승급분 알림 0건, 대기자는 그대로 대기 |
| N7 | (개정) 리스너 단위 테스트: 저장소가 예외를 던짐 | 리스너가 예외를 그대로 던진다(삼키지 않음) |
| N8 | `VacancyFillerTest`·`EnrollmentCommandServiceTest`(모의 객체) | 발행 호출 횟수·reason 검증(대기 저장 시 발행 없음) |
| N9 | (개정) 알림 저장 실패 시 함께 롤백: `TransactionTemplate` 안에서 사번 21자 이상 이벤트 발행 | `execute`가 예외를 던지고 그 트랜잭션의 쓰기·알림 0건 |
| N10 | (개정) 기존 `EnrollmentConcurrencyTest` C1~C10 | 교착 없이 통과(A안에서 C4·C7이 30초 초과로 실패했던 회귀 확인) |

N6 대체 이유: 처음에는 중복 신청 409로 롤백시키려 했으나, `enroll`이 중복 검사를 자기 복구(승급)보다 먼저 하므로 409가 나는 시점에는 승급이 일어나지 않아 "승급 뒤 롤백"을 만들 수 없다. 그래서 N2와 같이 `TransactionTemplate`으로 승급 뒤 직접 롤백시켜 확인한다.

테스트 데이터(최종 검토 W1): FR-09 이전부터 있던 커밋형 테스트(`EnrollmentConcurrencyTest`, `VacancySelfHealIntegrationTest`, `CompletionConcurrencyTest`, `SessionLockIntegrationTest`, `SecurityIntegrationTest`)는 이제 생기는 알림 행을 지우지 않아 `eduhub_test`에 쌓인다. 이번 범위에서는 고치지 않고 남은 과제로 둔다(정리 시 회차 id가 든 메시지나 사번 기준 삭제). 알림 전체 행 수에 기대는 테스트는 쓰지 않는다.

기존 테스트: `VacancyFiller`·`EnrollmentCommandService` 생성자에 `ApplicationEventPublisher`가 추가되므로 이들을 직접 생성하는 단위 테스트를 고친다. 마지막에 `./gradlew check`.

## 8. 질문 목록 (모두 제안대로 확정)

- **Q1 방식**: 2절 A(커밋 뒤 별도 트랜잭션, 실패·크래시 시 유실 허용)로 갈까요, B(같은 트랜잭션, 유실 없음·알림 실패 시 신청 실패)로 갈까요? (처음 답 **A** → 개정 후 **B 계열: BEFORE_COMMIT 리스너**, 2절 개정)
- **Q2 메시지**: 회차 id만 넣을까요, 과정명·회차 시작일을 넣을까요? 넣으면 이벤트에 필드를 더하거나(지시된 4개 필드 밖) 리스너가 회차·과정을 1회 조회해야 한다. (**id만**, 화면·발송이 붙을 때 바꿈)
- **Q3 type 값**: `ENROLLMENT_CONFIRMED`/`WAITING_PROMOTED` 대신 `APPLIED`/`PROMOTED`를 그대로 쓸까요? (**ENROLLMENT_CONFIRMED / WAITING_PROMOTED**, 나중에 다른 종류 알림이 붙어도 읽히게)
- **Q4 범위**: 정원 감소 강등(D-24)으로 확정 → 대기가 된 사람에게도 알림을 남길까요? (**이번엔 안 함**. 요구사항이 확정·승급만 말한다. 필요하면 후속)
- **Q5 컬럼**: 지시된 5개 컬럼만 둘까요, 추적용 `enrollment_id`(FK + 인덱스)를 더할까요? (**5개만**. 로그에 enrollmentId를 남긴다)
- 승인되면 결정 사항 D-32로 `docs/requirements.md`에 기록하고 구현계획을 쓴다.
