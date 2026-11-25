# backend

## 테스트 명령

`backend/`에서 실행한다.

```bash
./gradlew test                                  # 전체 테스트 (로컬 MariaDB 필요)
./gradlew test --tests "*HealthControllerTest"  # 테스트 하나만 실행
./gradlew spotlessApply                         # 포맷 적용
./gradlew check                                 # 테스트 + 포맷 검사
./gradlew bootRun --args="--spring.profiles.active=dev --server.port=8088"  # 다른 포트로 실행
```

`--args`는 `build.gradle.kts`의 기본 인자(`--spring.profiles.active=dev`)를 대체하므로 프로필도 함께 적는다.

`test`에는 포맷 검사가 포함되지 않으므로, 작업을 마칠 때는 `check`를 실행한다.

## 계층 규칙

- Controller → Service → Repository 순서로 호출한다. Controller에서 Repository를 직접 호출하지 않는다.
- 조회 서비스(`CourseQueryService`, readOnly)와 쓰기 서비스(`CourseCommandService`)를 나눈다. 관리자 쓰기 API는 `/api/v1/admin` 아래(`CourseAdminController`)에 둔다(D-18).
- 상태 규칙(종료 여부, 시작 여부)은 엔티티 메서드에서 검사한다. 현재 시각은 `Clock` 빈(`common/ClockConfig`)으로 받아 테스트에서 고정한다.
- 프런트 빌드 결과는 `src/main/resources/static`에 두고 백엔드가 함께 제공한다. 화면 경로(`/admin`, `/courses/{id}`, `/login`, `/me/enrollments`, `/admin/sessions/{sessionId}/roster`)는 `common/SpaForwardController`가 `index.html`로 넘긴다. 프런트에 화면 경로를 추가하면 여기에도 추가한다.
- `course`는 `enrollment`를 참조하지 않는다. 자리를 차지한 신청 수(확정·수료, D-28)는 `course/SessionSeatCounter`로 받는다(D-22). 정원 변경에 따른 강등도 같은 방식으로 `course/SessionCapacityChangeHandler`를 `enrollment`가 구현해 받는다(D-24).
- 패키지는 `com.example.eduhub` 아래 도메인별로 나눈다. `course`는 과정과 회차, `enrollment`는 신청·대기·취소·승급·수료, `notification`은 알림 기록, `auth`는 사원 계정·비밀번호 로그인·토큰 발급과 검증·현재 사용자(D-33), `common`은 공통 코드를 담는다(NFR-07). `course`·`enrollment`는 `auth`의 `CurrentUser`만 쓴다. `notification`은 `enrollment`의 이벤트(`EnrollmentConfirmedEvent`)만 구독하고, `enrollment`는 `notification`을 참조하지 않는다.

## JPA 규칙

- 연관관계는 LAZY로 둔다.
- 엔티티를 API 응답으로 그대로 반환하지 않고 DTO를 반환한다.
- `open-in-view=false`이므로 지연 로딩은 트랜잭션 안에서 끝내야 한다(NFR-02).
- 스키마는 Flyway로만 변경한다. `ddl-auto: validate`이므로 엔티티를 추가할 때 마이그레이션도 함께 추가해야 한다.

## OpenAPI 규칙

- 모든 API는 OpenAPI 문서가 있어야 하고, 프론트 클라이언트는 이 스펙에서 생성한다(NFR-04).
- 문서는 springdoc 기본 경로인 `/v3/api-docs`와 `/swagger-ui.html`에서 제공된다.
- 에러 응답은 `common/ErrorResponse`(`code`, `message`) 형식이며, `common/GlobalExceptionHandler`에서 처리한다.
  - 404: `NotFoundException` 하위 클래스(`CourseNotFoundException`, `SessionNotFoundException`, `EnrollmentNotFoundException` 등), code `NOT_FOUND`.
  - 400: 요청 본문 검증 실패는 code `BAD_REQUEST`, message `필드: 메시지, ...`(필드 이름 순). Bean Validation 메시지는 한국어로 직접 적는다. 그 밖의 입력 오류는 `InvalidRequestException`.
  - 401: 토큰 없음·만료·서명 불일치, code `UNAUTHORIZED`. 403: 역할 부족(`/api/v1/admin/**`, `/api/admin/**`에 ADMIN 없음), code `FORBIDDEN`. 둘 다 `auth/SecurityConfig`의 핸들러가 고정 문구로 응답한다(D-33). 로그인 실패(사번 없음·비밀번호 틀림)의 401은 컨트롤러 안에서 나므로 `GlobalExceptionHandler`가 `auth/InvalidCredentialsException`을 같은 형식으로 응답한다.
  - 인증이 필요한 컨트롤러에는 `@SecurityRequirement(name = "bearerAuth")`와 401(관리자 API는 403도) 응답을 문서화한다. 보안 스킴은 `common/OpenApiConfig`에 있다.
  - 409: `ConflictException(code, message)`. code는 `COURSE_CLOSED`, `SESSION_CLOSED`, `SESSION_STARTED`, `SESSION_NOT_STARTED`, `SESSION_NOT_CLOSED`(종료 전 수료), `DUPLICATE_ENROLLMENT`(같은 회차 활성 신청 중복)처럼 상황별로 둔다.
  - 503: `PessimisticLockingFailureException`(회차 행 락 대기 3초 초과), code `LOCK_TIMEOUT`(D-20).

## 프로필과 환경 변수

- `dev`: 로컬 MariaDB의 `eduhub` 스키마에 접속한다. `DB_USERNAME`, `DB_PASSWORD`는 환경 변수 또는 `.env`(`backend/`나 저장소 루트)로 지정하며, 이름은 `.env.example`에 있다.
- `test`: 같은 계정으로 로컬 MariaDB의 `eduhub_test` 스키마에 접속한다. DB가 필요한 테스트는 `@ActiveProfiles("test")`를 붙이고, `@DataJpaTest`에는 `@AutoConfigureTestDatabase(replace = NONE)`도 붙인다.
- `eduhub`와 `eduhub_test` 스키마는 미리 만들어 두어야 한다.
- JWT 서명 키(D-33)는 환경 변수 `EDUHUB_JWT_SECRET`(Base64, 32바이트 이상)로만 준다. `eduhub.auth.allow-random-key=true`인 dev·test 프로필에서 비어 있으면 기동 시 무작위 키를 쓰므로 재시작하면 다시 로그인해야 한다. 그 밖의 프로필은 키가 없으면 기동하지 않는다. 키 값을 설정 파일에 쓰지 않는다.
- dev 프로필에서는 `auth/DevAccountSeeder`가 개발용 계정(E1001·E1002 사원, A0001 관리자)을 만든다. 비밀번호는 환경 변수 `EDUHUB_DEV_PASSWORD`(세 계정 공통, `.env`에 앞뒤 공백 없이, UTF-8 72바이트 이하)로만 받고, 비어 있거나 72바이트를 넘으면 계정을 만들지 않고 경고만 남긴다. 이미 있는 계정은 건드리지 않는다. test 프로필에는 시더가 없어 테스트가 필요한 계정을 직접 만든다. 운영 프로필에 dev·test를 넣지 않는다.

## 구현 시 유의할 점

- 동시성 테스트(NFR-01)는 락과 인덱스 동작을 확인해야 하므로 H2가 아닌 실제 MariaDB에서 돌린다.
- 취소 후 재신청이 가능하므로(D-05), 중복 신청 방지에 `회차+사용자` 일반 유니크 제약을 쓸 수 없다. MariaDB에는 부분 인덱스도 없으므로 생성 컬럼 `active_session_id`에 유일 제약을 건다(D-19).
- 신청 동시성은 회차 행 `PESSIMISTIC_WRITE`를 첫 쿼리로 잡고 격리 수준은 `READ_COMMITTED`로 한다(D-20). 수량을 바꾸는 코드는 같은 락을 쓴다. 관리자 `updateSession`·`closeSession`과 신청 취소(`cancel`)도 회차 행 락을 첫 쿼리로 잡고 `READ_COMMITTED`로 실행한다(D-24, D-26).
- 대기 순서는 `waiting_key`(작을수록 앞, 회차 안에서 유일)로 표현하고, 순번은 조회할 때 "키가 더 작은 대기자 수 + 1"로 계산한다. 새 대기자는 최댓값 + 1, 정원 감소로 강등된 사람은 기존 대기자보다 앞 순번(D-03)이 되도록 최솟값보다 작은 키를 받는다. 대기에서 벗어나면 키를 지운다(D-23).
- 인증(D-33): `POST /api/auth/login`(사번·비밀번호, 모든 프로필)이 `employee` 테이블(BCrypt, `{bcrypt}` 접두사)로 확인해 HS256 JWT를 발급한다(유효 60분). `sub`는 사원 ID, 클레임은 `employee_no`와 `role`(`EMPLOYEE`/`ADMIN`) 하나다. 사번 없음과 비밀번호 틀림은 같은 401·같은 본문이고, 비밀번호는 UTF-8 72바이트 이하(`auth/MaxUtf8Bytes`)다. Spring Security 리소스 서버가 토큰을 검증하며 규칙표는 `auth/SecurityConfig`에 있다(공개: `POST /api/auth/login`, `GET /api/v1/courses/**`, `/api/health`, API 문서, `/api` 밖 경로. 관리자: `/api/v1/admin/**`, `/api/admin/**`, GET 외의 `/api/v1/courses/**`. 그 밖의 `/api/**`는 인증 필요). 공개 GET 규칙이 ADMIN 규칙보다 앞에 있어야 한다. 공개 GET 아래에 개인정보를 돌려주는 GET을 만들면 규칙을 좁힌다. `GET /api/me`는 토큰 클레임만으로 응답한다(DB 조회 없음).
- 로그인한 사용자는 컨트롤러 파라미터 `@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt`로 받고 `CurrentUser.from(jwt)`로 꺼낸다. 서비스에는 `user.employeeNo()`(사번 문자열)를 넘긴다(`enrollment.employee_id`가 사번을 저장한다). 클레임이 없거나 틀리면 `AuthenticationCredentialsNotFoundException`(401)이다. 사번을 헤더·쿼리·본문으로 받지 않는다. 관리자 판단은 URL 규칙(ADMIN 역할)이 맡는다.
- 컨트롤러 테스트(`@WebMvcTest`)는 `@Import(auth/WebMvcSecurityTestConfig.class)`로 실제 보안 규칙을 가져오고, `with(employee("E1001"))`·`with(admin("A1"))`(`jwt()` 기반, `sub`=사원 ID, `employee_no`·`role` 클레임과 `ROLE_*` 권한)로 인증을 붙인다. 슬라이스에는 `@ActiveProfiles("test")`를 붙이지 않는다(시더·무작위 키 설정이 켜진다). 실제 서명·만료·역할 변환은 `SecurityIntegrationTest`가, 로그인부터 신청·취소까지는 `AuthIntegrationTest`가 확인한다. 이 둘은 커밋형이라 자기 접두어(`S3-`, `T5-`)의 데이터만 지운다.
- 수료(D-28)는 종료(CLOSED)된 회차에서만 하며, 회차 행 락을 첫 쿼리로 잡고 `READ_COMMITTED`로 실행한다(`enrollment/CompletionService`). `CONFIRMED → COMPLETED`만 있고, 대기자는 바꾸지 않는다. 신청·승급·강등의 확정 수 계산은 `CONFIRMED`만 센다(종료 전 회차에서만 동작하므로 수료가 섞이지 않는다).
- 목록 조회에서 대기 순번이 필요하면 행마다 `WaitingNumbers.numberOf()`를 부르지 말고, `EnrollmentRepository.findMyEnrollments`처럼 같은 쿼리 안의 서브쿼리로 계산한다(N+1 방지).
- 신청 취소는 행을 지우지 않고 `CANCELLED`와 `cancelled_at`을 남기며, 교육 시작 후에는 확정·대기 모두 취소할 수 없다(409 `SESSION_STARTED`, D-25).
- 빈자리 채우기(D-26)는 교육 시작 전에만 하고, 대기 키 오름차순으로 빈자리만큼 승급한다. 확정자 취소와 정원 증가(D-02)가 같은 규칙(`enrollment/VacancyFiller`)을 쓰고, 대기자 취소는 승급이 없다. `VacancyFiller`는 `Propagation.MANDATORY`라 트랜잭션 밖에서 부르면 예외가 나며, 회차 행 락은 호출자가 먼저 잡아야 한다.
- 알림(FR-09, D-32): 확정 시 `EnrollmentConfirmedEvent`를 발행하는 곳은 `EnrollmentCommandService.enroll`(APPLIED)과 `VacancyFiller`(PROMOTED) 두 군데다. `notification/NotificationListener`는 `BEFORE_COMMIT`으로 같은 트랜잭션에서 저장하므로 저장이 실패하면 신청도 롤백된다. 커밋 뒤에 DB를 쓰는 리스너(`AFTER_COMMIT` + `REQUIRES_NEW`)는 바깥 커넥션이 반환되기 전에 커넥션을 하나 더 요구해 풀 교착을 일으키므로 쓰지 않는다. 승급 경로를 새로 만들면 `VacancyFiller`를 거쳐야 알림이 빠지지 않는다. 새로 쓰는 커밋형 통합 테스트는 자기가 남긴 알림 행을 사번 접두어로 지운다(`NotificationIntegrationTest`의 `N9-`처럼). FR-09 이전부터 있던 커밋형 테스트(`EnrollmentConcurrencyTest`, `VacancySelfHealIntegrationTest`, `CompletionConcurrencyTest`, `SessionLockIntegrationTest`)는 아직 알림 행을 지우지 않아 `eduhub_test`에 쌓인다(남은 과제). 그래서 알림 전체 행 수에 기대는 테스트를 쓰지 않는다. INFO 로그 "알림 기록(커밋 전)"은 커밋 직전에 남으므로, 그 뒤 커밋이 실패하면 DB에는 알림이 없을 수 있다.

## 빌드 환경 주의

- JDK는 Gradle 툴체인으로 Java 21에 고정되어 있고, 없으면 foojay로 내려받는다.
- 사내망의 TLS 검사 때문에 Gradle 다운로드가 "could not resolve plugin artifact"로만 실패할 수 있다. JDK가 사내 루트 인증서를 신뢰하지 않는 것이 원인이다. `~/.gradle/gradle.properties`에 `systemProp.javax.net.ssl.trustStoreType=Windows-ROOT`를 넣으면 해결된다. 전역 `org.gradle.jvmargs`는 프로젝트별 값을 덮어쓰므로 쓰지 않는다.
- Spotless의 `googleJavaFormat("1.37.0")` 버전 지정은 실패했다. 지금은 버전을 지정하지 않고 기본값을 쓴다.
