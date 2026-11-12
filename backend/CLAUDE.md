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
- 프런트 빌드 결과는 `src/main/resources/static`에 두고 백엔드가 함께 제공한다. 화면 경로(`/admin`, `/courses/{id}`)는 `common/SpaForwardController`가 `index.html`로 넘긴다. 프런트에 화면 경로를 추가하면 여기에도 추가한다.
- `course`는 `enrollment`를 참조하지 않는다. 확정 수는 `course/SessionSeatCounter`로 받는다(D-22). 정원 변경에 따른 강등도 같은 방식으로 `course/SessionCapacityChangeHandler`를 `enrollment`가 구현해 받는다(D-24).
- 패키지는 `com.example.eduhub` 아래 도메인별로 나눈다. `course`는 과정과 회차, `enrollment`는 신청·대기·취소·승급·수료, `common`은 공통 코드를 담는다(NFR-07).

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
  - 409: `ConflictException(code, message)`. code는 `COURSE_CLOSED`, `SESSION_CLOSED`, `SESSION_STARTED`, `SESSION_NOT_STARTED`, `DUPLICATE_ENROLLMENT`(같은 회차 활성 신청 중복)처럼 상황별로 둔다.
  - 503: `PessimisticLockingFailureException`(회차 행 락 대기 3초 초과), code `LOCK_TIMEOUT`(D-20).

## 프로필과 환경 변수

- `dev`: 로컬 MariaDB의 `eduhub` 스키마에 접속한다. `DB_USERNAME`, `DB_PASSWORD`는 환경 변수 또는 `.env`(`backend/`나 저장소 루트)로 지정하며, 이름은 `.env.example`에 있다.
- `test`: 같은 계정으로 로컬 MariaDB의 `eduhub_test` 스키마에 접속한다. DB가 필요한 테스트는 `@ActiveProfiles("test")`를 붙이고, `@DataJpaTest`에는 `@AutoConfigureTestDatabase(replace = NONE)`도 붙인다.
- `eduhub`와 `eduhub_test` 스키마는 미리 만들어 두어야 한다.

## 구현 시 유의할 점

- 동시성 테스트(NFR-01)는 락과 인덱스 동작을 확인해야 하므로 H2가 아닌 실제 MariaDB에서 돌린다.
- 취소 후 재신청이 가능하므로(D-05), 중복 신청 방지에 `회차+사용자` 일반 유니크 제약을 쓸 수 없다. MariaDB에는 부분 인덱스도 없으므로 생성 컬럼 `active_session_id`에 유일 제약을 건다(D-19).
- 신청 동시성은 회차 행 `PESSIMISTIC_WRITE`를 첫 쿼리로 잡고 격리 수준은 `READ_COMMITTED`로 한다(D-20). 수량을 바꾸는 코드는 같은 락을 쓴다. 관리자 `updateSession`·`closeSession`과 신청 취소(`cancel`)도 회차 행 락을 첫 쿼리로 잡고 `READ_COMMITTED`로 실행한다(D-24, D-26).
- 대기 순서는 `waiting_key`(작을수록 앞, 회차 안에서 유일)로 표현하고, 순번은 조회할 때 "키가 더 작은 대기자 수 + 1"로 계산한다. 새 대기자는 최댓값 + 1, 정원 감소로 강등된 사람은 기존 대기자보다 앞 순번(D-03)이 되도록 최솟값보다 작은 키를 받는다. 대기에서 벗어나면 키를 지운다(D-23).
- 신청 취소는 행을 지우지 않고 `CANCELLED`와 `cancelled_at`을 남기며, 교육 시작 후에는 확정·대기 모두 취소할 수 없다(409 `SESSION_STARTED`, D-25).
- 빈자리 채우기(D-26)는 교육 시작 전에만 하고, 대기 키 오름차순으로 빈자리만큼 승급한다. 확정자 취소와 정원 증가(D-02)가 같은 규칙(`enrollment/VacancyFiller`)을 쓰고, 대기자 취소는 승급이 없다. `VacancyFiller`는 `Propagation.MANDATORY`라 트랜잭션 밖에서 부르면 예외가 나며, 회차 행 락은 호출자가 먼저 잡아야 한다.

## 빌드 환경 주의

- JDK는 Gradle 툴체인으로 Java 21에 고정되어 있고, 없으면 foojay로 내려받는다.
- 사내망의 TLS 검사 때문에 Gradle 다운로드가 "could not resolve plugin artifact"로만 실패할 수 있다. JDK가 사내 루트 인증서를 신뢰하지 않는 것이 원인이다. `~/.gradle/gradle.properties`에 `systemProp.javax.net.ssl.trustStoreType=Windows-ROOT`를 넣으면 해결된다. 전역 `org.gradle.jvmargs`는 프로젝트별 값을 덮어쓰므로 쓰지 않는다.
- Spotless의 `googleJavaFormat("1.37.0")` 버전 지정은 실패했다. 지금은 버전을 지정하지 않고 기본값을 쓴다.
