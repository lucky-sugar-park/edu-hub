---
name: eduhub-api
description: EduHub 백엔드(backend/)에 REST API를 추가하거나 기존 API를 바꿀 때 쓴다. "API 만들어줘", "엔드포인트 추가", "응답에 필드 추가", "새 기능의 백엔드 구현", 테이블·엔티티 추가처럼 Controller·Service·DTO·마이그레이션이 함께 바뀌는 작업이 대상이다. 화면만 바꾸는 작업에는 쓰지 않는다.
---

# EduHub 백엔드 API 추가 절차

기준 예시는 `backend/src/main/java/com/example/eduhub/course/`다. 규칙의 근거는 `backend/CLAUDE.md`와 `docs/requirements.md`의 결정 사항(D-xx)이다. 새 규칙을 정하면 결정 사항 표에 먼저 적는다.

## 1. Flyway 마이그레이션
- `backend/src/main/resources/db/migration/V{n}__{설명}.sql`로 **새 버전을 추가**한다. 병합된 파일(V1 등)은 고치지 않는다.
- MariaDB 문법(`AUTO_INCREMENT`, `ENGINE = InnoDB`, `utf8mb4`)을 쓴다. 새 외래 키에는 인덱스를 함께 만든다.
- 컬럼 삭제는 두 단계(사용 중지 → 다음 배포에서 삭제)로 나눈다.
- `ddl-auto: validate`이므로 엔티티와 스키마가 다르면 앱이 뜨지 않는다.

## 2. 엔티티
- `@Id @GeneratedValue(IDENTITY)`, JPA용 `protected` 기본 생성자, 필수 값을 받는 public 생성자. setter는 두지 않는다.
- enum은 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`.
- 연관관계는 `@ManyToOne(fetch = LAZY, optional = false)` 단방향을 기본으로 한다.
- 상태 규칙은 엔티티 메서드(`update`, `close`, `ensureOpen` 등)에서 검사한다. 위반하면 `ConflictException(code, message)`, 입력 오류는 `InvalidRequestException`.
- 현재 시각이 필요하면 메서드 인자로 `Instant now`를 받는다(예: `CourseSession.update(..., now)`).

## 3. DTO (record)
- 요청: `XxxRequest` record에 Bean Validation과 `@Schema`를 함께 단다. 메시지는 한국어로 직접 적는다.
  ```java
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
      @NotBlank(message = "필수 값입니다") @Size(max = 200, message = "200자 이하여야 합니다") String place,
  ```
- 숫자 필수 값은 `Integer` + `@NotNull`(기본형은 누락을 0으로 받는다).
- 응답: `XxxResponse` record + 정적 팩터리 `from(entity)` 또는 `of(...)`. 엔티티를 그대로 반환하지 않는다.
- 상태에 따라 달라지는 검증(예: 시작 전에만 과거 시각 금지)은 DTO가 아니라 엔티티·서비스에서 한다.

## 4. Repository
- `JpaRepository<Entity, Long>`. 단순 조회는 파생 쿼리(`findByCourseIdOrderByStartAtAscIdAsc`).
- 선택 조건 검색은 JPQL `@Query` + `(:p is null or ...)`. 입력 정리(빈 값 → null, LIKE 이스케이프)는 `default` 메서드에서 한다(`CourseRepository.search`).
- 목록 API에서 N+1이 생기지 않는지 확인한다(NFR-02).

## 5. Service (@Transactional 규칙)
- 조회와 쓰기를 나눈다.
  - 조회: `XxxQueryService`에 클래스 단위 `@Transactional(readOnly = true)`.
  - 쓰기: `XxxCommandService`에 클래스 단위 `@Transactional`. 수정은 변경 감지로 처리하고 `save`를 다시 부르지 않는다.
- `open-in-view=false`이므로 지연 로딩과 DTO 변환은 서비스(트랜잭션) 안에서 끝낸다.
- 없는 id는 `findById(...).orElseThrow(() -> new XxxNotFoundException(id))`. `XxxNotFoundException`은 `common/NotFoundException`을 상속한다.
- 현재 시각은 `Clock` 빈(`common/ClockConfig`)을 생성자로 받아 `clock.instant()`로 얻는다.
- 미룬 요구사항은 `// TODO(주차, FR-xx): ...`로 남긴다.

## 6. Controller
- 경로는 `/api/v1/...`, 관리자 쓰기 API는 `/api/v1/admin/...`(D-18). Controller는 Service만 호출한다.
- 클래스에 `@Tag(name, description)`, 메서드마다 `@Operation(summary)`와 응답 코드별 `@ApiResponse`. 에러 응답에는 `content = @Content(schema = @Schema(implementation = ErrorResponse.class))`.
- 등록은 `@ResponseStatus(HttpStatus.CREATED)`, 요청 본문은 `@Valid @RequestBody`.
- 예외 → HTTP 상태 (`common/GlobalExceptionHandler`, 본문은 `ErrorResponse(code, message)`):

  | 예외 | 상태 | code |
  |---|---|---|
  | Bean Validation 실패 | 400 | `BAD_REQUEST`, message는 `필드: 메시지, ...`(필드 이름 순) |
  | `InvalidRequestException` | 400 | `BAD_REQUEST` |
  | `NotFoundException` 하위 클래스 | 404 | `NOT_FOUND` |
  | `ConflictException(code, message)` | 409 | 상황별 code(예: `COURSE_CLOSED`, `SESSION_STARTED`) |

- 새 409 code는 Controller의 Javadoc과 `@ApiResponse` 설명, `backend/CLAUDE.md`에 함께 적는다.

## 7. 테스트
- Controller: `@WebMvcTest(XxxController.class)` + `@ActiveProfiles("test")` + `@MockitoBean` 서비스. 성공 응답 JSON, 400 메시지와 `verifyNoInteractions`, 404·409의 `$.code`를 확인한다.
- Service: `@ExtendWith(MockitoExtension.class)` + `@Mock` 리포지토리 + `Clock.fixed(NOW, ZoneOffset.UTC)`. 기능별로 `@Nested`로 묶고 상태 규칙 위반을 모두 검사한다(NFR-05 커버리지 70%).
- Repository: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@ActiveProfiles("test")`(로컬 MariaDB `eduhub_test`). 검색 조건, 정렬, 페이징, LAZY 여부를 확인한다.
- OpenAPI: `OpenApiDocsTest`에 새 경로와 에러 응답 문서가 있는지 추가한다.

## 8. 프론트 API 클라이언트 재생성
1. 백엔드를 띄운다(dev 기본 포트 8085).
2. `frontend/`에서 `npm run generate:api`를 실행한다. `packages/api-client/src/generated/`는 손으로 고치지 않는다.
3. 새 API 클래스와 타입은 `packages/api-client/src/index.ts`에서 내보낸다.
4. 다른 포트로 생성했다면 `generated/base.ts`의 `BASE_PATH`를 8085로 되돌린 뒤 생성 코드를 함께 커밋한다.

## 검증
`backend/`에서 실행해 모두 통과해야 한다.
```bash
./gradlew spotlessApply test
```
마지막에 `./gradlew check`로 포맷 검사까지 확인한다. 화면 경로를 추가했다면 `common/SpaForwardController`에도 추가한다.
