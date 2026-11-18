# 인증(NFR-03, D-11)과 신청·내 신청·명단 화면(FR-04~08 UI) 구현계획

- 설계명세: `docs/design/NFR-03-auth.md`, `docs/design/FR-04-08-ui.md` (둘 다 승인됨). 결정 사항 D-30·D-31.
- 규칙: `CLAUDE.md`, `backend/CLAUDE.md`, `frontend/CLAUDE.md`, `.claude/skills/eduhub-api/SKILL.md`
- 브랜치: `feature/auth-enrollment-ui`(한 PR)
- 진행: 작업마다 implementer 구현 → reviewer 검토. 심각·경고는 implementer가 수정하고, 수정·재검토는 최대 2회.
- **Gradle 동시 실행 금지:** 작업 중에는 각 작업의 완료 명령만 실행한다. 전체 `./gradlew check`와 `npm test`는 작업마다 메인 세션이 한 번 돌린다.
- 하지 않는 것
  - 사내 SSO, 갱신 토큰, 로그아웃 API
  - `application-prod.yml` 수정, `.env`·`.env.example` 수정(개발자가 직접)
  - 사원 정보 API
- **여러 계층 작업(CLAUDE.md 5):** 전체 diff가 1,000줄을 넘는다. 설계 승인 때 알렸고, T2(기존 API 계약 변경)와 T4(생성 클라이언트 재생성)는 시작 전에 다시 알린다.

## 작업 순서

T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → 최종 검토(critical-reviewer)

| 작업 | 영역 | 내용 |
|---|---|---|
| T1 | BE | 보안 기반: 의존성, 키, 필터 체인, 401·403, 토큰 발급, dev 로그인 |
| T2 | BE | 사번 주입 교체: 헤더 제거, `@CurrentAdmin` 삭제, 컨트롤러 테스트를 `jwt()`로, OpenAPI 보안 스킴 |
| T3 | BE | 화면용 응답 필드, SPA 경로 |
| T4 | FE | 클라이언트 재생성, API 모듈과 인터셉터, 인증 상태, 로그인, 경로 보호, 상단 바 |
| T5 | FE | 과정 상세 신청·취소 |
| T6 | FE | 내 신청 내역 |
| T7 | FE | 관리자 명단·수료 |
| T8 | 공통 | 문서, 전체 검증, 앱 실행 확인 |

---

## T1. 보안 기반 (백엔드)

**변경 파일**
- `build.gradle.kts`: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, 테스트 `spring-security-test`. 다운로드가 TLS로 실패하면 `backend/CLAUDE.md`의 빌드 환경 주의를 따른다.
- 신규 `common/AuthProperties.java`: `@ConfigurationProperties("eduhub.auth")` record이고 필드는 `jwtSecret`, `tokenTtl`(Duration, 기본 8h), `devLoginEnabled`(기본 false)다.
- 신규 `common/JwtKeyConfig.java`
  - 키를 만든다. Base64 디코드 결과가 32바이트 이상이어야 한다.
  - 비어 있으면 `devLoginEnabled`일 때만 `SecureRandom` 32바이트로 만들고 WARN 로그를 남긴다. 그 밖에는 `IllegalStateException`으로 기동을 실패시킨다. 32바이트 미만이어도 기동 실패다.
  - `JwtDecoder`(Nimbus HS256)와 `JwtEncoder` 빈을 만든다.
- 신규 `common/SecurityConfig.java`
  - 설계 3.1의 규칙표, `STATELESS`, CSRF·폼·Basic 끔
  - `JwtAuthenticationConverter`: `roles` → `ROLE_*`, 주체 = `sub`
  - `AuthenticationEntryPoint`(401 `UNAUTHORIZED`)와 `AccessDeniedHandler`(403 `FORBIDDEN`)가 `ErrorResponse` JSON을 쓴다.
- 신규 `common/TokenIssuer.java`: 사번 → 역할(`AdminProperties.isAdmin`) → `JwtEncoder`로 HS256 토큰을 만든다(`iss` `eduhub-dev`, `iat`, `exp`).
- 신규 `common/DevAuthController.java`(`@ConditionalOnProperty(eduhub.auth.dev-login-enabled=true)`, tag `DevAuth`), `LoginRequest`, `LoginResponse`
  - `POST /api/v1/dev/login`
  - OpenAPI 설명에 "개발 환경 전용, 비밀번호 없음"
- `application.yml`: `eduhub.auth.jwt-secret: ${EDUHUB_JWT_SECRET:}`, `token-ttl: 8h`, `dev-login-enabled: false`
- `application-dev.yml`, `application-test.yml`: `eduhub.auth.dev-login-enabled: true`

이 작업에서 **기존 컨트롤러와 테스트는 아직 고치지 않는다.** 다만 보안이 켜지면 기존 `@WebMvcTest`와 통합 테스트가 401로 깨질 수 있다. 그래서 T1 완료 명령은 새 테스트만 대상으로 하고, 전체 녹색은 T2 끝에서 맞춘다.

**테스트**
- 신규 `common/SecurityIntegrationTest`(`@SpringBootTest` + MockMvc, test 프로필, 관리자 목록은 `@TestPropertySource`로 고정)
  - A1: 토큰 없이 신청하면 401 `UNAUTHORIZED` JSON
  - A2: 만료 토큰, 다른 키로 서명한 토큰은 401
  - A3·A4: EMPLOYEE·ADMIN 토큰으로 `/api/v1/admin/courses` 호출
  - A5: 비로그인으로 `/api/v1/courses`, `/v3/api-docs`, `/api/health` 호출
  - A6: dev 로그인 → 토큰 → 실제 신청 API 성공. 관리자 사번이면 roles에 ADMIN
- 신규 `common/JwtKeyConfigTest`(`ApplicationContextRunner`)
  - A7: dev 로그인이 꺼지면 `DevAuthController` 빈이 없다
  - A8: 키가 비어 있고 dev 로그인이 꺼지면 컨텍스트가 실패한다. 짧은 키도 실패한다. dev 로그인이 켜지면 무작위 키로 기동한다
- 신규 `common/TokenIssuerTest`: 클레임, 만료, 역할

**완료 명령**
```bash
./gradlew spotlessApply test --tests "*SecurityIntegrationTest" --tests "*JwtKeyConfigTest" --tests "*TokenIssuerTest"
```

**검토 포인트**
- 키 처리(로그와 예외 메시지에 키가 찍히지 않는가)
- dev 로그인 노출 조건
- 규칙표 순서(넓은 규칙이 좁은 규칙을 가리지 않는가)
- 401·403 형식

---

## T2. 사번 주입 교체와 기존 API·테스트 이전 (백엔드) — 시작 전 알림

**변경 파일**
- `common/CurrentEmployeeArgumentResolver.java`
  - `SecurityContextHolder`의 `JwtAuthenticationToken`에서 `getName()`(sub)을 읽는다.
  - 인증이 없으면 `AuthenticationCredentialsNotFoundException`을 던져 401로 처리한다.
  - 헤더 읽기(`readEmployeeId`)는 삭제한다.
- 삭제: `common/CurrentAdmin.java`, `CurrentAdminArgumentResolver.java`, `ForbiddenException.java`와 그 테스트. `GlobalExceptionHandler`의 403 처리도 삭제한다. `WebConfig`에서 등록을 정리한다.
- `enrollment/EnrollmentController.java`
  - 세 엔드포인트의 `@RequestHeader("X-Employee-Id") @NotBlank @Size` → `@Parameter(hidden = true) @CurrentEmployee String employeeId`
  - `/me/enrollments`의 헤더 `@Parameter(in = HEADER)`를 삭제한다.
  - 응답 코드: 400 설명에서 헤더를 빼고 401을 추가한다.
- `enrollment/EnrollmentAdminController.java`: `@CurrentAdmin` 인자와 헤더 표기, 인자 순서 Javadoc을 삭제한다. 401·403 응답을 문서화한다.
- `course/CourseAdminController.java`: 401·403 응답을 문서화한다.
- OpenAPI: `@SecurityScheme(bearerAuth)`를 설정 클래스에 둔다. 인증이 필요한 컨트롤러(`EnrollmentController`, `EnrollmentAdminController`, `CourseAdminController`)에 `@SecurityRequirement(name = "bearerAuth")`를 붙인다.
- 테스트 이전
  - 모든 `@WebMvcTest`에 `@Import({SecurityConfig.class, JwtKeyConfig.class, ...})`를 붙이거나, 공통 테스트 설정을 만든다.
  - 헤더 대신 `with(jwt().jwt(j -> j.subject("E1001")))`를 쓰고, 관리자는 `.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))`를 더한다.
  - 헤더 누락·공백·21자 400 테스트는 "토큰 없음 401"로 바꾼다.
  - `EnrollmentAdminControllerTest`의 403은 EMPLOYEE 토큰 403으로 바꾼다. 인자 순서 테스트(`adminCheckComesBeforeBodyValidation`)는 "비관리자가 잘못된 본문을 보내면 403"으로 남긴다. 보안 필터가 먼저 막으므로 순서와 상관없이 성립한다.
  - `CourseAdminControllerTest`: ADMIN 토큰 추가, EMPLOYEE 403, 비로그인 401 테스트를 추가한다.
  - `OpenApiDocsTest`(A10): `components.securitySchemes.bearerAuth`가 있고, 모든 경로에 `X-Employee-Id` 파라미터가 없으며, 관리자 API에 401·403 응답이 있다.
  - `AdminPropertiesTest`는 유지한다(토큰 발급에서 사용).
  - 리졸버 단위 테스트는 SecurityContext 기반으로 다시 쓴다.
- `backend/CLAUDE.md`의 헤더·`@CurrentAdmin` 관련 문장은 T8에서 고친다.

**완료 명령**
```bash
./gradlew spotlessApply test
```
이 작업에서만 implementer가 전체 test를 돌린다. 메인 세션은 이 작업 동안 Gradle을 돌리지 않는다.

**검토 포인트**
- 헤더 흔적이 남지 않았는가(`grep -r "X-Employee-Id" backend/src`가 문서 외에는 0건)
- A9: 다른 사원 신청 취소 불가
- 서비스 시그니처가 바뀌지 않았는가
- 테스트가 약해지지 않았는가

---

## T3. 화면용 응답 필드와 SPA 경로 (백엔드)

**변경 파일**
- `MyEnrollmentResponse`, `MyEnrollmentRow`, `EnrollmentRepository.findMyEnrollments`: `courseId`(`c.id`)를 추가한다.
- `SessionRosterResponse`: `courseId`, `courseTitle`, `startAt`, `place`를 추가한다. `EnrollmentQueryService.getRoster`는 회차를 과정과 함께 조회한다.
  - `CourseSessionRepository`에 fetch join 조회를 추가하거나, 같은 트랜잭션에서 지연 로딩 1회를 허용한다.
  - 쿼리 수 "회차 1 + 신청 1"을 유지하는 쪽(fetch join)을 우선한다.
- `common/SpaForwardController`: `/login`, `/me/enrollments`, `/admin/sessions/{sessionId}/roster`를 추가한다.

**테스트**
- `EnrollmentRepositoryTest`와 `MyEnrollmentsQueryCountTest`: `courseId` 값, 쿼리 수가 그대로인지
- `EnrollmentRosterIntegrationTest`: 새 필드
- `SpaForwardControllerTest`: 새 경로 3개가 `index.html`로 넘어가는지
- `OpenApiDocsTest`: 새 필드가 스키마에 있는지

**완료 명령**
```bash
./gradlew spotlessApply test --tests "*EnrollmentRepositoryTest" --tests "*MyEnrollmentsQueryCountTest" --tests "*EnrollmentRosterIntegrationTest" --tests "*EnrollmentQueryServiceTest" --tests "*SpaForwardControllerTest" --tests "*OpenApiDocsTest"
```

---

## T4. 프런트 인증 기반 — 시작 전 알림(클라이언트 재생성)

**클라이언트 재생성**
1. 백엔드를 다른 포트로 띄운다. `cd backend && ./gradlew bootRun --args="--spring.profiles.active=dev --server.port=8088"`를 백그라운드로 실행하고, 끝나면 반드시 종료한다.
2. `npm run generate:api`를 8088 스펙으로 실행한다(frontend/CLAUDE.md). `generated/base.ts`의 `BASE_PATH`는 8085로 되돌린다.
3. `packages/api-client/src/index.ts`에서 `EnrollmentApi`, `EnrollmentAdminApi`, `DevAuthApi`와 새 타입을 내보낸다.
- 메인 세션이 대신 실행할 수도 있다. 재생성 후 `git diff --stat packages/api-client`로 바뀐 범위를 보고한다.

**변경 파일** (`apps/course-mfe/src/`)
- `api/courseApi.ts`
  - Axios 인스턴스 하나를 만든다. 요청 인터셉터는 만료되지 않은 토큰만 붙인다(만료됐으면 보내기 전에 버린다).
  - 응답 인터셉터는 401(로그인 요청 제외)이면 항상 토큰을 지운다(T1 검토 W1). 백엔드는 공개 경로라도 무효 토큰이 붙으면 401을 내기 때문이다.
    - 토큰을 붙였던 공개 GET(`/api/v1/courses`, `/api/v1/courses/**`, `/api/health`)이면 토큰 없이 한 번 다시 요청한다.
    - 그 밖이면 등록된 핸들러를 호출한다. 핸들러는 현재 화면이 `RequireAuth`·`RequireAdmin` 경로일 때만 `/login`으로 보내고, 공개 화면에서는 로그아웃 상태로 그대로 둔다.
  - 모든 API 클래스 인스턴스를 이 인스턴스로 만든다.
  - 토큰 공급자와 401 핸들러는 `setAuthHooks({ getToken, onUnauthorized })`로 주입한다. 모듈 간 순환 참조를 피하기 위해서다.
- 신규 `auth/authStorage.ts`: sessionStorage 키 `eduhub.auth`의 읽기·쓰기·삭제, 만료 검사. 모두 try/catch로 감싼다.
- 신규 `auth/AuthContext.tsx`: `AuthProvider`, `useAuth()`(`token`, `employeeId`, `roles`, `isAdmin`, `login`, `logout`). 마운트할 때 저장소에서 복원하고 `setAuthHooks`를 등록한다.
- 신규 `auth/RequireAuth.tsx`, `auth/RequireAdmin.tsx`
- 신규 `pages/LoginPage.tsx`
  - 사번 입력
  - 성공하면 `location.state.from`(없으면 `/`)으로 이동
  - 404면 SSO 안내, 그 밖에는 `toErrorMessage`
  - "개발 환경 전용" 표시
- `main.tsx`: `AuthProvider`로 감싼다. 401 핸들러에서 쓸 수 있도록 navigate는 Provider 안에서 등록한다.
- `App.tsx`
  - 경로 추가: `/login`, `/me/enrollments`, `/admin/sessions/:sessionId/roster`. 앞의 두 화면은 T6·T7에서 만들므로 이 작업에서는 자리만 둔다.
  - `/admin`을 `RequireAdmin`으로 감싼다.
  - 상단 바: "내 신청"(로그인 시), "과정 관리"(관리자), 오른쪽에 사번과 로그아웃 또는 로그인
- 기존 화면 테스트: Provider와 Router 감싸기를 공통 `test-utils.tsx`(`renderWithProviders`)로 정리한다. 기존 테스트가 깨지지 않게 옮긴다.

**테스트**
- `authStorage`·`AuthContext`: 저장, 복원, 만료 무시, 로그아웃
- `courseApi` 인터셉터: Authorization 헤더, 만료 토큰은 붙이지 않음, 401이면 토큰 삭제, 공개 GET은 토큰 없이 재시도, 보호 화면에서만 `/login` 이동, 로그인 요청 401은 제외
- `LoginPage`: 성공 이동, 404 안내, 오류
- U8: 비로그인 `/me/enrollments` → `/login`, 일반 사원 `/admin` → 권한 안내, 상단 바 메뉴 노출

**완료 명령**(`frontend/`에서)
```bash
npm test -w apps/course-mfe
npm run build -w apps/course-mfe
```

---

## T5. 과정 상세 신청·취소 (FR-04~06 UI)

**변경 파일**
- 신규 `hooks/useMyEnrollments.ts`
  - `(status?, page?, size?)`를 받는다.
  - 비로그인이면 호출하지 않는다.
  - `reload`를 제공한다.
- 신규 `hooks/useEnrollmentActions.ts`: `enroll(sessionId)`, `cancel(sessionId)`, `submitting`, `error`, `clearError`. 형식은 `useCourseAdmin`과 같다.
- `statusLabels.ts`: 신청 상태 이름과 수료 실패 사유 이름
- `pages/CourseDetailPage.tsx`
  - 설계 4절의 "내 신청" 열과 작업 열
  - `useMyEnrollments(undefined, 0, 100)`으로 활성 신청을 `sessionId`에 맞춘다.
  - 신청 결과 알림, 취소 확인(`ConfirmDialog`), 성공 뒤 다시 불러오기
  - 신청 불가 사유 툴팁(종료, 시작 후, 과정 종료)

**테스트**: U1~U4, 훅 U9(성공, 실패, 로딩)

**완료 명령**
```bash
npm test -w apps/course-mfe
```

---

## T6. 내 신청 내역 (FR-07 UI)

**변경 파일**
- 신규 `pages/MyEnrollmentsPage.tsx`
  - 상태 탭(전체, 확정, 대기, 취소, 수료)
  - 표(과정 링크 `/courses/{courseId}`)
  - `TablePagination`(20건)
  - 빈 목록 안내, 취소
- `App.tsx`: `/me/enrollments` 자리에 연결한다.

**테스트**: U5(탭 → status 파라미터, 페이지 이동, 빈 목록, 취소 후 다시 불러오기)

**완료 명령**
```bash
npm test -w apps/course-mfe
```

---

## T7. 관리자 명단·수료 (FR-08 UI)

**변경 파일**
- 신규 `hooks/useSessionRoster.ts`(404는 `SessionNotFoundError`로 바꾼다), `hooks/useCompletion.ts`
- 신규 `pages/SessionRosterPage.tsx`
  - 설계 6절의 머리, 확정·수료 표(체크박스는 CONFIRMED만), 대기 표
  - 수료 버튼 활성 조건과 안내
  - 결과 대화 상자
- `pages/AdminCoursePage.tsx`: 회차 표 작업 열에 "명단" 링크 버튼을 추가한다.
- `App.tsx`: `/admin/sessions/:sessionId/roster`를 `RequireAdmin`으로 연결한다.

**테스트**: U6·U7, `AdminCoursePage`의 "명단" 링크, 훅 U9

**완료 명령**
```bash
npm test -w apps/course-mfe
npm run build -w apps/course-mfe
```

---

## T8. 문서, 전체 검증, 실행 확인

- `backend/CLAUDE.md`
  - 헤더·`@CurrentAdmin`·D-29 관련 문장을 D-30 기준으로 고친다.
  - 보안 규칙표, 테스트에서 `jwt()` 쓰는 법, `EDUHUB_JWT_SECRET`을 적는다.
- `frontend/CLAUDE.md`: 인증(`useAuth`, `RequireAuth`·`RequireAdmin`, 인터셉터), 새 경로, 새 API 클래스, 테스트 도우미 `renderWithProviders`
- 전체 검증
  - `./gradlew check`
  - `npm test`
  - `npm run build`
  - `check-openapi.sh`로 모든 컨트롤러 검사
- 실행 확인(메인 세션, 브라우저 미리보기)
  - 백엔드 dev(8085)와 프런트(3001)를 띄운다.
  - 일반 사원으로 로그인 → 신청 → 내 신청 → 취소
  - 관리자 로그인 → 명단 → (종료된 회차가 있으면) 수료
  - 스크린샷을 남긴다.
- 개발자 요청 사항: `.env`·`.env.example`에 `EDUHUB_JWT_SECRET`을 추가한다. 운영 프로필에는 키가 필요하다(PR 본문에 적는다).

---

## 위험 영역

| 위험 | 대응 |
|---|---|
| 보안 도입으로 기존 테스트 대량 실패 | T1은 새 테스트만, T2에서 한꺼번에 이전하고 전체 test로 녹색 확인 |
| dev 로그인이 운영에 노출 | 기본값 false, 속성 조건부 빈, A7 테스트 |
| 키 누락이 조용히 넘어감 | 운영 프로필 기동 실패(A8) |
| 생성 클라이언트 재생성으로 기존 프런트 코드 깨짐 | T4에서 빌드(`tsc`)로 확인. `BASE_PATH`를 8085로 되돌림 |
| 401 인터셉터의 무한 이동이나 로그인 요청 오판 | 로그인 요청 제외, 테스트 |
| 기존 관리자 화면이 ADMIN 없이 막힘 | 의도된 변경(D-30). 상단 바·`RequireAdmin`으로 안내 |
| 백그라운드 bootRun이 남아 포트 점유 | T4 끝에서 종료 확인 |
