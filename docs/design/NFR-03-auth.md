# NFR-03 인증(D-11) 설계명세 — 백엔드·프런트

- 상태: **승인됨 (2026-11-19)**. 결정과 9절 질문은 모두 제안대로 확정했고 결정 사항 D-30으로 기록했다. 구현계획은 `docs/plans/auth-enrollment-ui.md`.
- 대상: NFR-03 "로그인한 사용자만 신청 가능, 본인 신청만 취소 가능", D-11 "Spring Security와 JWT 검증 구조로 시작하고, 개발 환경에서만 쓰는 로그인을 둔다"
- 함께 진행: 화면 설계 `docs/design/FR-04-08-ui.md`(같은 브랜치, 같은 PR)
- 대체하는 임시 결정: D-21의 `X-Employee-Id` 헤더, D-27의 헤더 읽기, D-29의 `@CurrentAdmin` 사번 목록 검사
- 브랜치: `feature/auth-enrollment-ui`

## 1. 결정 (2026-11-19, 개발자 선택)

1. **토큰:** HS256 JWT를 Spring Security 리소스 서버(`spring-boot-starter-oauth2-resource-server`)로 검증한다. 서명 키는 환경 변수로만 받는다.
2. **발급:** dev·test 프로필에만 켜지는 `POST /api/v1/dev/login`이 사번을 받아 토큰을 발급한다. 관리자 여부는 기존 `EDUHUB_ADMIN_EMPLOYEE_IDS`(D-29) 목록으로 정해 `roles` 클레임에 넣는다. 사내 SSO가 붙으면 발급자만 바꾸고 검증 구조는 유지한다.
3. **임시 헤더 제거:** `X-Employee-Id`를 모든 API에서 없애고 사번은 토큰의 `sub`에서만 읽는다. `/api/v1/admin/**` 전체(기존 과정·회차 관리 포함)에 `ADMIN` 역할을 요구한다. 헤더 위조 위험(W2)이 해소된다.
4. **프런트 토큰 보관:** `sessionStorage`. Axios 인터셉터가 `Authorization: Bearer`를 붙이고, 401이면 로그아웃하고 로그인 화면으로 보낸다.

## 2. 토큰

| 항목 | 값 |
|---|---|
| 알고리즘 | HS256(대칭 키) |
| 키 | `eduhub.auth.jwt-secret` ← `${EDUHUB_JWT_SECRET:}`, Base64 인코딩 32바이트 이상 |
| 클레임 | `sub`=사번, `roles`=`["EMPLOYEE"]` 또는 `["EMPLOYEE","ADMIN"]`, `iss`=`eduhub-dev`, `iat`, `exp` |
| 유효 시간 | `eduhub.auth.token-ttl` 기본 8시간. 갱신 토큰은 두지 않는다(만료되면 다시 로그인) |

**키가 비어 있을 때**
- dev 로그인이 켜진 프로필(dev, test)에서는 기동 시 무작위 키를 만들고 WARN 로그를 남긴다. 재시작하면 기존 토큰이 무효가 된다(개발용으로 허용).
- dev 로그인이 꺼진 프로필(운영)에서 키가 없거나 32바이트 미만이면 **기동을 실패**시킨다(설정 누락을 조용히 넘기지 않는다).
- 키 값은 어떤 설정 파일에도 쓰지 않는다. `.env.example`에는 변수 이름만 둔다(개발자가 직접 추가).

## 3. 백엔드

### 3.1 보안 필터 체인 (`common/SecurityConfig`)

- 무상태(`SessionCreationPolicy.STATELESS`), CSRF 끔(쿠키를 쓰지 않는 Bearer 토큰), 폼 로그인·HTTP Basic 끔.
- 요청 규칙(위에서부터)

| 경로 | 규칙 |
|---|---|
| `POST /api/v1/dev/login` | 모두 허용(컨트롤러가 없는 프로필에서는 404) |
| `GET /api/health`, `GET /api/v1/courses`, `GET /api/v1/courses/**` | 모두 허용(비로그인 과정 조회) |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | 모두 허용 |
| `/api/v1/admin/**` | `hasRole("ADMIN")` |
| 그 밖의 `/api/**` | 인증 필요 |
| `/api` 밖(정적 파일, SPA 경로) | 모두 허용 |

- `JwtAuthenticationConverter`: `roles` 클레임을 `ROLE_` 권한으로, 주체 이름을 `sub`로 한다.
- 401·403도 기존 `ErrorResponse` 형식으로 응답한다.
  - 401 `UNAUTHORIZED`: 토큰 없음, 서명 불일치, 만료(`AuthenticationEntryPoint`)
  - 403 `FORBIDDEN`: 역할 부족(`AccessDeniedHandler`)

### 3.2 dev 로그인 (`common/DevAuthController`, `common/TokenIssuer`)

- `@ConditionalOnProperty("eduhub.auth.dev-login-enabled", havingValue = "true")`이고, `application-dev.yml`과 `application-test.yml`에서만 `true`다.
- `POST /api/v1/dev/login` 본문은 `{"employeeId": "E1001"}`(`@NotBlank @Size(max = 20)`)이다.
- 응답 200 `LoginResponse`: `accessToken`, `tokenType`(`Bearer`), `expiresAt`, `employeeId`, `roles`
- 비밀번호는 받지 않는다. **개발 환경 전용**임을 OpenAPI 설명과 Javadoc에 명시한다.

### 3.3 사번 주입 교체

- `@CurrentEmployee String employeeId`는 그대로 쓰고, 리졸버가 헤더 대신 `SecurityContext`의 인증 주체(`sub`)를 읽는다. 인증이 없으면 401이다(보안 체인이 먼저 막으므로 방어용).
- 기존 세 엔드포인트(신청, 회차 내 신청, 취소)의 `@RequestHeader("X-Employee-Id")`를 `@CurrentEmployee`로 바꾼다.
- `@CurrentAdmin`, `CurrentAdminArgumentResolver`, `ForbiddenException`은 삭제한다. 관리자 검사는 URL 규칙이 맡는다. `EnrollmentAdminController`의 인자 순서 제약도 사라진다.
- `AdminProperties`(D-29 목록)는 토큰 발급 때 역할을 정하는 데만 쓴다.
- 서비스 계층은 바뀌지 않는다(사번 문자열을 받는 시그니처 그대로).

### 3.4 OpenAPI

- `@SecurityScheme(name = "bearerAuth", type = HTTP, scheme = "bearer", bearerFormat = "JWT")`를 둔다.
- 인증이 필요한 컨트롤러에 `@SecurityRequirement(name = "bearerAuth")`를 붙인다.
- 헤더 `X-Employee-Id` 문서 표기(`@Parameter(in = HEADER)`)를 모두 지운다.
- 응답 코드에 401(인증 필요 API)과 403(관리자 API)을 추가한다.
- 생성 클라이언트는 `Configuration.accessToken`으로 토큰을 받을 수 있게 된다.

### 3.5 설정

| 파일 | 추가 |
|---|---|
| `build.gradle.kts` | `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, 테스트 `spring-security-test` |
| `application.yml` | `eduhub.auth.jwt-secret: ${EDUHUB_JWT_SECRET:}`, `eduhub.auth.token-ttl: 8h`, `eduhub.auth.dev-login-enabled: false` |
| `application-dev.yml`, `application-test.yml` | `eduhub.auth.dev-login-enabled: true` |
| 운영 설정 | `application-prod.yml`은 만들거나 고치지 않는다(사람이 PR로). 기본값이 dev 로그인 꺼짐이므로 dev·test가 아닌 프로필은 모두 `EDUHUB_JWT_SECRET`이 있어야 기동한다. PR 본문에 적는다 |

## 4. 프런트

### 4.1 인증 상태 (`src/auth/`)

- `AuthProvider`와 `useAuth()`가 다음을 제공한다.
  - 상태: `token`, `employeeId`, `roles`, `expiresAt`, `isAdmin`
  - 동작: `login(employeeId)`, `logout()`
- 저장 위치는 `sessionStorage` 키 `eduhub.auth`(JSON)다.
  - 시작할 때 읽는다. 만료됐으면 버린다.
  - 읽기와 쓰기는 try/catch로 감싸, 저장소를 쓸 수 없으면 메모리에만 둔다.

### 4.2 API 클라이언트 (`src/api/http.ts`의 인스턴스 하나)

> 구조 변경(2026-11-19) 후: Axios 인스턴스, 인터셉터, 토큰 보관은 `src/api/http.ts`에 있고, API 클래스 인스턴스는 `src/api/courseApi.ts`(과정)와 `src/api/clients.ts`(인증·신청)가 이 인스턴스로 만든다.

- Axios 인스턴스 하나에 요청 인터셉터를 둔다. 토큰이 있으면 `Authorization`을 붙인다.
- 응답 인터셉터는 401이면 `logout()`하고 현재 위치를 기억한 채 `/login`으로 보낸다. 단, 로그인 요청 자체의 오류는 제외한다.
- 모든 API 클래스(`CourseApi`, `CourseAdminApi`, `EnrollmentApi`, `EnrollmentAdminApi`, `DevAuthApi`)를 이 인스턴스로 만든다(frontend/CLAUDE.md의 "한 곳에서 생성" 규칙 유지).

### 4.3 로그인 화면 `/login`

- 사번 입력과 로그인 버튼. 성공하면 원래 가려던 경로(없으면 `/`)로 이동한다.
- 화면에 "개발 환경 전용 로그인(비밀번호 없음)"을 표시한다.
- 로그인 API가 404면(운영처럼 dev 로그인이 꺼진 환경) "이 환경에서는 사내 SSO 연동 전까지 로그인할 수 없습니다"를 보여 준다.

### 4.4 경로 보호와 상단 바

- `RequireAuth`: 로그인하지 않았으면 `/login`으로 보낸다(돌아올 경로 기억).
- `RequireAdmin`: 로그인했지만 ADMIN이 아니면 "관리자만 볼 수 있습니다" 안내를 보여 준다.
- 상단 바
  - 과정 목록, 내 신청(로그인 시)
  - 과정 관리(ADMIN일 때만)
  - 오른쪽에 사번과 로그아웃, 또는 로그인 버튼
- 경로별 보호 범위는 화면 설계 `FR-04-08-ui.md` 2절을 따른다.

## 5. 기존 동작 영향

| 영역 | 변화 |
|---|---|
| 과정 목록·상세 조회 | 그대로 비로그인 허용 |
| 신청·내 신청·취소·내 신청 목록 | 헤더 대신 토큰. 토큰이 없으면 401 |
| 관리자 과정·회차 관리(FR-03) | **새로 ADMIN 필요**. 기존 관리자 화면은 로그인한 관리자만 쓴다 |
| 명단·수료(FR-08) | 403 판단 주체가 리졸버에서 보안 체인으로 바뀐다(응답 형식 동일) |
| Swagger UI | Authorize 버튼으로 토큰을 넣어 호출한다 |

## 6. 트랜잭션·동시성

변화 없다. 인증은 필터에서 끝나고 서비스·락 구조는 그대로다.

## 7. 위험

| 위험 | 대응 |
|---|---|
| dev 로그인이 운영에서 켜짐 | 기본값 `false`, dev·test yml에서만 `true`. 운영 키 누락 시 기동 실패. 컨트롤러 존재 여부 테스트 |
| 대칭 키 유출 | 환경 변수로만. 저장소에 키가 없는지 리뷰에서 확인 |
| `sessionStorage` 토큰의 XSS 노출 | React 기본 이스케이프. `dangerouslySetInnerHTML` 금지. 만료 8시간 |
| 기존 `@WebMvcTest` 다수 수정 | `spring-security-test`의 `jwt()`로 일괄 교체. 보안 설정을 슬라이스에 `@Import` |
| 콜레이션으로 사번 비교가 느슨함(P1) | 이번 범위 밖. 토큰의 `sub`를 그대로 쓰므로 관리자 판단은 정확 일치 |

## 8. 테스트 시나리오

### 8.1 백엔드

| # | 시나리오 | 기대 |
|---|---|---|
| A1 | 토큰 없이 `POST /sessions/1/enrollments` | 401 `UNAUTHORIZED` |
| A2 | 만료·서명 불일치 토큰 | 401 |
| A3 | EMPLOYEE 토큰으로 `/api/v1/admin/**` | 403 `FORBIDDEN` |
| A4 | ADMIN 토큰으로 관리자 API | 통과 |
| A5 | 비로그인 `GET /api/v1/courses`, `/v3/api-docs`, `/api/health` | 200 |
| A6 | dev 로그인 | 일반 사번은 `["EMPLOYEE"]`, 목록 사번은 ADMIN 포함, 토큰으로 신청 성공 |
| A7 | dev 로그인 꺼짐(프로필·속성) | `/api/v1/dev/login` 404, 컨텍스트에 컨트롤러 빈 없음 |
| A8 | 키 없음 + dev 로그인 꺼짐 | 기동 실패 |
| A9 | 토큰의 사번이 서비스에 전달 | 기존 컨트롤러 테스트를 `jwt()`로 바꿔 확인. 다른 사람 신청 취소 불가 |
| A10 | OpenAPI | `bearerAuth` 스킴, `X-Employee-Id` 없음, 401·403 문서화 |

### 8.2 프런트

- `useAuth`: 로그인 성공 시 저장, 새로고침(재마운트) 후 복원, 만료 토큰 무시, 로그아웃 시 삭제
- 인터셉터: 토큰을 붙이는지, 401에서 로그아웃하고 이동하는지
- 로그인 화면: 성공 시 원래 경로로 이동, 404 안내, 오류 메시지
- `RequireAuth`·`RequireAdmin`과 상단 바의 메뉴 노출

## 9. 질문 목록 (모두 제안대로 확정)

| # | 질문 | 제안 |
|---|---|---|
| Q1 | 토큰 유효 시간 | 8시간(근무일 하루). 갱신 토큰 없음 |
| Q2 | 키가 없을 때 dev·test의 무작위 키 | 허용(재시작 시 재로그인). 운영 프로필은 기동 실패 |
| Q3 | 기존 관리자 과정·회차 API에도 ADMIN을 거는가 | 건다(D-18이 예고한 대로) |
| Q4 | 로그아웃 API | 두지 않는다(무상태 토큰). 프런트에서 토큰을 지운다 |
| Q5 | 결정 사항 기록 | D-30(인증 구조·dev 로그인·헤더 제거)으로 남기고 D-21·D-27·D-29의 임시 부분은 "D-30으로 대체"라고 표시한다 |
