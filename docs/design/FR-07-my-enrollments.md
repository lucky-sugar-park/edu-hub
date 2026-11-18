# FR-07 내 신청 내역 조회 설계명세

- 상태: **승인됨 (2026-11-18)**. 1절의 결정 3개와 8절의 질문은 모두 제안대로 확정했고 결정 사항 D-27로 기록했다. 구현계획은 `docs/plans/fr-07.md`.
- 대상: FR-07 "내 신청 내역(확정, 대기, 취소, 수료)을 조회한다"
- 선행: FR-04~06(`FR-04-enrollment.md`, `FR-05-waiting.md`, `FR-06-cancel-promotion.md`, D-19~D-26)
- 범위: 로그인한 사원의 신청 목록 API(상태 필터, 페이지). 화면, FR-08(명단·수료 처리), Spring Security 도입, 프런트 API 클라이언트 재생성은 하지 않는다.
- 브랜치: `feature/fr-07-fr-08`

## 1. 요구사항 정리와 결정

| 구분 | 내용 | 이 설계에서의 처리 |
|---|---|---|
| FR-07 | 내 신청 내역을 상태별로 조회 | `GET /api/v1/me/enrollments?status=` + 페이지 |
| NFR-02 | 성능, N+1 금지 | DTO 프로젝션 1회 + count 1회. 대기 순번도 같은 SELECT의 서브쿼리 |
| NFR-03 | 사번은 요청 파라미터가 아니라 인증 정보에서 | 아래 결정 1 |
| NFR-04 | 모든 API에 OpenAPI 문서 | `@Operation`, `@ApiResponse` 200·400 |
| D-10 | 수료하지 못한 확정자는 확정 상태 | 아래 결정 3 |
| D-23 | 대기 순번 = 키가 더 작은 대기자 수 + 1, 시작 후에도 보여 줌 | 서브쿼리로 같은 식을 계산. 회차 시작 여부와 관계없이 계산 |

**결정 (2026-11-18, 개발자 승인)**

1. **사번 출처:** 인증(D-11)이 아직 없다. 사번 추출을 `common/CurrentEmployee`(파라미터 어노테이션)와 `CurrentEmployeeArgumentResolver` 한 곳에 둔다. 컨트롤러는 `@CurrentEmployee String employeeId`만 받는다. 지금 리졸버는 D-21의 임시 헤더 `X-Employee-Id`를 읽으므로, **헤더 위조 위험(FR-04 최종 검토 W2)은 그대로 남는다.** 인증을 도입할 때 리졸버만 바꾼다.
2. **경로:** 기존 규칙(`/api/v1/...`)에 맞춰 `GET /api/v1/me/enrollments`로 둔다.
3. **수료 상태:** `EnrollmentStatus`에 `COMPLETED`가 없고, 수료로 바꾸는 처리는 FR-08이다. 이번에는 `CONFIRMED`, `WAITING`, `CANCELLED`만 받는다. `COMPLETED`는 FR-08에서 enum에 추가하면 이 API가 별도 수정 없이 필터로 받는다. `status`가 varchar라 마이그레이션도 필요 없다.

## 2. API

### 2.1 `GET /api/v1/me/enrollments` (신규)

| 요청 | 내용 |
|---|---|
| 헤더 `X-Employee-Id` | 필수, 공백 불가, 최대 20자(D-21, 임시) |
| `status` | 선택. `CONFIRMED`, `WAITING`, `CANCELLED`. 생략하면 전체 |
| `page`, `size` | 선택. 기본 `page=0`, `size=20`(과정 목록과 같음) |

- 정렬은 **신청 일시 내림차순, 같으면 id 내림차순**으로 고정하고 클라이언트의 `sort`는 무시한다(과정 목록 `CourseQueryService`와 같은 방식).
- 응답 200: `PageResponse<MyEnrollmentResponse>`(`content`, `page`, `size`, `totalElements`). 신청이 없으면 빈 `content`와 `totalElements=0`이다(404 아님).

`MyEnrollmentResponse`

| 필드 | 타입 | 내용 |
|---|---|---|
| `id` | Long | 신청 ID |
| `courseTitle` | String | 과정 제목 |
| `sessionId` | Long | 회차 ID. 화면에서 취소 API(`/sessions/{sessionId}/enrollments/me/cancel`)를 부를 때 쓴다 |
| `sessionStartAt` | Instant | 회차 시작 일시(UTC, 화면이 한국 시간으로 표시) |
| `sessionPlace` | String | 회차 장소 |
| `status` | EnrollmentStatus | 신청 상태 |
| `waitingNumber` | Integer | `WAITING`일 때만 현재 대기 순번, 그 밖에는 null(`EnrollmentResponse`와 같은 규칙) |
| `appliedAt` | Instant | 신청 일시 |

### 2.2 오류

| 코드 | code | 경우 |
|---|---|---|
| 400 | `BAD_REQUEST` | 사번 헤더 누락·공백·20자 초과(리졸버가 `InvalidRequestException`), 알 수 없는 `status` 값(타입 변환 실패, 기존 `handleExceptionInternal` 경로) |

404·409·503은 없다(조회만 하고 락을 잡지 않는다).

### 2.3 OpenAPI 표기

- 리졸버 파라미터는 springdoc이 헤더로 인식하지 못하므로 메서드 파라미터에 `@Parameter(hidden = true)`를 붙이고, 메서드에 `@Parameter(in = ParameterIn.HEADER, name = "X-Employee-Id", required = true, ...)`로 헤더를 문서화한다. 생성 클라이언트에서도 헤더 인자가 나와야 한다.
- `page`, `size`는 과정 목록처럼 `@Parameter`로 문서화하고 `Pageable`은 숨긴다.
- `OpenApiDocsTest`에 `/api/v1/me/enrollments`와 `X-Employee-Id` 헤더 파라미터를 확인하는 단언을 추가한다.

## 3. 쿼리 방식

### 3.1 목록 쿼리 (JPQL DTO 프로젝션, 1회)

```sql
select new com.example.eduhub.enrollment.MyEnrollmentRow(
         e.id, c.title, s.id, s.startAt, s.place, e.status,
         case when e.status = WAITING then
           (select count(w) + 1 from Enrollment w
             where w.session = s and w.status = WAITING
               and w.waitingKey < e.waitingKey)
         end,
         e.appliedAt)
  from Enrollment e join e.session s join s.course c
 where e.employeeId = :employeeId
   and (:status is null or e.status = :status)
 order by e.appliedAt desc, e.id desc
```

- 엔티티를 로드하지 않으므로 지연 로딩이 생기지 않는다(`open-in-view=false`에서도 안전).
- 대기 순번은 `WaitingNumbers.numberOf()`(행마다 count 1회)를 쓰지 않는다. 같은 식(D-23)을 서브쿼리로 계산한다. 두 계산이 같은 값을 내는지 Repository 테스트로 비교한다.
- 서브쿼리 결과는 Long이므로, Repository는 Long을 받는 행 record(`MyEnrollmentRow`)로 받고 서비스에서 `MyEnrollmentResponse`(Integer)로 바꾼다. 구현에서 `select new`에 응답 record를 바로 쓸 수 있으면 행 record를 생략해도 된다.

### 3.2 count 쿼리 (1회)

```sql
select count(e) from Enrollment e
 where e.employeeId = :employeeId and (:status is null or e.status = :status)
```

`select new`가 있는 쿼리는 Spring Data의 count 자동 생성이 실패할 수 있으므로 `countQuery`를 명시한다. 결과가 한 페이지보다 적으면 Spring Data가 count를 생략할 수 있다(그때는 1회).

### 3.3 인덱스 (2026-11-17 dev DB 조회 결과 기준)

| 사용처 | 인덱스 | 상태 |
|---|---|---|
| 사원별 조회 | `idx_enrollment_employee(employee_id)` | 있음 |
| 대기 순번 서브쿼리 | `uk_enrollment_waiting_key(session_id, waiting_key)` | 있음(WAITING만 키를 가짐, D-23) |
| 회차·과정 조인 | 각 PK | 있음 |

**새 마이그레이션은 만들지 않는다.** 정렬(`applied_at desc`)은 filesort가 되지만, 한 사원의 신청은 많아야 수십 건이라 D-12(1만 건, p95 300ms) 기준에 영향이 없다. 실측이 기준을 넘으면 그때 `(employee_id, applied_at)` 인덱스를 새 버전 파일로 추가한다.

## 4. 엔티티·스키마 변경

없다. `Enrollment`, `EnrollmentStatus`, 마이그레이션은 그대로 둔다.

## 5. 계층과 파일

| 파일 | 변경 |
|---|---|
| `common/CurrentEmployee.java` | 신규. 파라미터 어노테이션 |
| `common/CurrentEmployeeArgumentResolver.java` | 신규. `X-Employee-Id`를 읽어 공백·20자 초과면 `InvalidRequestException` |
| `common/WebConfig.java` | 신규. 리졸버 등록(`WebMvcConfigurer`) |
| `enrollment/MyEnrollmentResponse.java` (+ 필요하면 `MyEnrollmentRow`) | 신규 |
| `enrollment/EnrollmentRepository.java` | `findMyEnrollments(employeeId, status, pageable)` 추가 |
| `enrollment/EnrollmentQueryService.java` | `getMyEnrollments(employeeId, status, pageable)` 추가. readOnly, 정렬 없는 `PageRequest`로 바꿔 넘김 |
| `enrollment/EnrollmentController.java` | 엔드포인트 추가 |

- 계층 규칙: Controller → QueryService → Repository. `enrollment`가 JPQL에서 `course`의 엔티티를 조인하는 것은 기존 `Enrollment → CourseSession` 방향과 같아 허용된다(`course`는 `enrollment`를 모른다).
- **기존 세 엔드포인트(신청, 내 신청, 취소)는 이번에 리졸버로 바꾸지 않는다**(8절 Q1).

## 6. 트랜잭션·동시성

- `@Transactional(readOnly = true)` 조회 한 번이며 락을 잡지 않는다.
- 목록과 count가 별도 쿼리라, 그 사이 다른 요청이 신청·취소하면 `totalElements`가 한 건 어긋날 수 있다. 조회 화면 용도라 허용한다.
- 대기 순번은 조회 시점의 값이다(FR-05의 `getMyEnrollment`와 같음).

## 7. 테스트 시나리오

### 7.1 Repository (`EnrollmentRepositoryTest`, 실제 MariaDB)

| # | 시나리오 | 기대 |
|---|---|---|
| R1 | 사원 A가 회차 3개에 확정·대기·취소, 사원 B도 같은 회차에 신청 | A의 3건만, 신청 일시 내림차순 |
| R2 | `status=WAITING` | A의 대기만 |
| R3 | 두 회차에서 각각 대기 2·3순위 | `waitingNumber`가 각각 2·3이고 `WaitingNumbers.numberOf()`와 같음 |
| R4 | 강등으로 음수 키를 가진 대기자(D-23) | 음수 키도 순번이 올바름 |
| R5 | 확정·취소 행 | `waitingNumber` null |
| R6 | 같은 `appliedAt` 두 건 | id 내림차순 |
| R7 | 페이지 크기 2, 신청 5건 | 첫 페이지 2건, `totalElements` 5 |

### 7.2 쿼리 수 (`MyEnrollmentsQueryCountTest`, 신규)

- 사원 1명이 과정 3개·회차 4개에 확정·대기·취소로 신청한 데이터를 만든다(대기는 두 회차 이상).
- `EntityManager` flush·clear 후 `Statistics.clear()`, 서비스 `getMyEnrollments` 호출.
- `Statistics.getPrepareStatementCount()`가 **1 이상 2 이하**인지 확인한다. 응답의 과정 제목·장소·순번도 함께 확인한다.
- 통계는 이 테스트에서만 `@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")`로 켠다. 설정 파일과 의존성은 바꾸지 않는다.

### 7.3 Controller (`EnrollmentControllerTest`)

- 200: 헤더의 사번이 서비스에 전달되고, `status`가 없으면 null로 전달됨. 클라이언트 `sort`는 무시됨.
- 400: 헤더 누락, 공백, 21자, `status=UNKNOWN`.
- `OpenApiDocsTest`: 경로와 `X-Employee-Id` 헤더 파라미터, 응답 200·400.

### 7.4 리졸버 단위

- 정상 값, 누락, 공백, 20자(통과), 21자(400).

## 8. 질문 목록 (모두 제안대로 확정)

| # | 질문 | 제안 |
|---|---|---|
| Q1 | 기존 세 엔드포인트도 이번에 `@CurrentEmployee`로 바꾸는가 | **바꾸지 않는다.** 바꾸면 OpenAPI 스펙과 생성 클라이언트, 기존 테스트가 함께 바뀌어 범위가 커진다. 인증 도입(D-11) 때 한꺼번에 바꾼다 |
| Q2 | 신청이 하나도 없을 때 | 200과 빈 목록 |
| Q3 | 응답에 `cancelledAt`, `confirmedAt`을 넣는가 | 넣지 않는다(요청 범위 밖). 화면에서 필요하면 그때 추가 |
| Q4 | 시작 후 대기자의 순번 표시 | 계속 보여 준다(D-23 "교육 시작 후에도 보여 준다") |
| Q5 | 결정 사항 기록 | 승인되면 `docs/requirements.md`에 D-27로 남긴다: "내 신청 목록은 `GET /api/v1/me/enrollments`, 신청 일시 내림차순, 사번은 `@CurrentEmployee`에서(인증 전에는 D-21 헤더)" |
