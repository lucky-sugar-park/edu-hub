# NFR-03 인증 재구성: 사원 계정과 비밀번호 로그인 — 설계명세(백엔드)

- 상태: **승인됨 (2026-11-25)**. 9절 질문은 모두 제안대로 확정했고 결정 사항 D-33으로 기록했다. 구현계획은 `docs/plans/nfr-03-password-login.md`.
- 대상: NFR-03 "로그인한 사용자만 신청 가능, 본인 신청만 취소 가능, 비밀 정보는 저장소에 두지 않음"
- 바꾸는 결정: **D-30**(dev 로그인·사번 `sub`·`roles` 클레임·8시간·관리자 사번 목록)을 대체한다. D-11(Spring Security + JWT 검증, 사내 SSO는 나중)은 유지한다.
- 브랜치: `feature/nfr-03-password-login` (`main` 94a578c에서 분기)
- 하지 않는 것: 회원가입, 비밀번호 변경, 리프레시 토큰, FR-07·08 API 변경, 프런트 화면

## 1. 지금과 달라지는 점

| 항목 | 지금 (D-30) | 바뀐 뒤 |
|---|---|---|
| 사용자 저장 | 없음(사번 문자열만) | `employee` 테이블(V7) |
| 로그인 | `POST /api/v1/dev/login {employeeId}`(dev·test만, 비밀번호 없음) | `POST /api/auth/login {employeeNo, password}`(모든 프로필) |
| 현재 사용자 API | 없음 | `GET /api/me` |
| `sub` | 사번 | 사원 ID(`employee.id`, 문자열) |
| 역할 클레임 | `roles: ["EMPLOYEE","ADMIN"]` | `role: "EMPLOYEE"` 또는 `"ADMIN"` |
| 만료 | 8시간 | 60분 |
| 관리자 판단 | `EDUHUB_ADMIN_EMPLOYEE_IDS` 목록 | `employee.role` |
| 사용자 꺼내기 | `@CurrentEmployee String` 리졸버 | `@AuthenticationPrincipal Jwt` + `CurrentUser.from(jwt)` |
| 패키지 | `common` | 새 패키지 `auth` |

유지하는 것: HS256, `EDUHUB_JWT_SECRET`(Base64 32바이트 이상), 키가 비었을 때의 처리(dev·test 무작위 키, 그 밖은 기동 실패), STATELESS, CSRF·폼 로그인·Basic 끔, 401 `UNAUTHORIZED`·403 `FORBIDDEN` 응답 형식, 직접 만든 필터 없음(리소스 서버의 `BearerTokenAuthenticationFilter`만 씀).

## 2. 스키마 (Flyway `V7__create_employee.sql`)

`employee` 테이블은 아직 없으므로 "컬럼 추가"가 아니라 새로 만든다.

```sql
CREATE TABLE employee (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    employee_no   VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_employee_no UNIQUE (employee_no),
    CONSTRAINT ck_employee_role CHECK (role IN ('EMPLOYEE', 'ADMIN'))
);
```

- `employee_no` 길이는 기존 `enrollment.employee_id`·`notification.employee_id`(VARCHAR(20))와 맞춘다.
- `password_hash`는 BCrypt(`{bcrypt}` 접두사 포함, 약 68자). `DelegatingPasswordEncoder`를 써서 나중에 알고리즘을 바꿀 수 있게 한다.
- `enrollment`·`notification`에는 외래 키를 걸지 않는다(질문 Q1). 그래서 새 외래 키 인덱스 규칙도 해당 없다.
- 마이그레이션은 모든 프로필에서 돈다. 계정 데이터는 넣지 않는다(4절).

## 3. API

### 3.1 `POST /api/auth/login` (공개)

요청 `LoginRequest`

| 필드 | 검증 |
|---|---|
| `employeeNo` | `@NotBlank @Size(max = 20)` |
| `password` | `@NotBlank @MaxUtf8Bytes(72)` (BCrypt 입력 한계는 UTF-8 **바이트** 기준. 구현 중 개정: 문자 수 `@Size`로는 한글 등이 72바이트를 넘고, `matches`가 72바이트 뒤를 조용히 무시한다) |

응답 200 `LoginResponse`

```json
{
  "accessToken": "eyJ...",
  "expiresIn": 3600,
  "user": { "id": 3, "employeeNo": "E1001", "role": "EMPLOYEE" }
}
```

오류

| 상황 | 응답 |
|---|---|
| 본문 검증 실패 | 400 `BAD_REQUEST` (기존 형식) |
| 사번 없음, 비밀번호 틀림 | **둘 다** 401 `UNAUTHORIZED`, 메시지 "사번 또는 비밀번호가 올바르지 않습니다" |

- 사번이 없을 때도 미리 만든 더미 해시로 `matches`를 한 번 돌려, 응답 시간으로 사번 존재 여부가 드러나지 않게 한다.
- 401은 컨트롤러 안에서 나므로 보안 체인의 진입점이 아니라 `GlobalExceptionHandler`가 `InvalidCredentialsException`을 받아 같은 `ErrorResponse`로 응답한다.
- 비밀번호와 해시는 로그·예외 메시지에 쓰지 않는다. 실패 로그는 사번 없이 INFO 한 줄만 남긴다.

### 3.2 `GET /api/me` (인증 필요)

응답 200 `MeResponse`: `{ "id": 3, "employeeNo": "E1001", "role": "EMPLOYEE" }`

- 토큰 클레임만으로 만든다(DB 조회 없음). 질문 Q5.
- 토큰 없음·만료·위조는 401.

### 3.3 기존 API의 사용자 꺼내기

- `EnrollmentController`의 네 엔드포인트(신청, 내 신청 조회, 취소, 내 신청 목록)는 `@AuthenticationPrincipal Jwt jwt`를 받아 `CurrentUser.from(jwt)`로 사용자를 꺼낸다.
- 서비스에는 지금처럼 **사번 문자열**(`user.employeeNo()`)을 넘긴다. `enrollment.employee_id`에 사번이 저장돼 있으므로 데이터 이전이 필요 없다(Q1).
- `CurrentUser`: `record CurrentUser(long id, String employeeNo, EmployeeRole role)`, `static CurrentUser from(Jwt jwt)`. 클레임이 빠졌거나 형식이 틀리면 `AuthenticationCredentialsNotFoundException`(401).
- `common/CurrentEmployee`, `CurrentEmployeeArgumentResolver`, `WebConfig`의 리졸버 등록을 지운다.

## 4. 개발용 계정 (dev 프로필만)

- `auth/DevAccountSeeder`: `@Profile("dev")`인 `ApplicationRunner`. 계정이 없을 때만 만든다(재시작해도 중복 없음, `employee_no` 유일 제약이 최종 방어).

| 사번 | 역할 |
|---|---|
| `E1001` | EMPLOYEE |
| `E1002` | EMPLOYEE |
| `A0001` | ADMIN |

- 비밀번호는 코드와 설정 파일에 쓰지 않고 환경 변수 `EDUHUB_DEV_PASSWORD`(세 계정 공통)로 받는다. 비어 있으면 계정을 만들지 않고 WARN을 남긴다. `.env.example`에 변수 이름만 추가한다. 질문 Q4.
- test 프로필은 시더를 쓰지 않는다. 테스트가 필요한 계정을 직접 만든다.

## 5. 토큰 (`auth/TokenIssuer`)

| 클레임 | 값 |
|---|---|
| `sub` | 사원 ID (`"3"`) |
| `employee_no` | 사번 |
| `role` | `EMPLOYEE` 또는 `ADMIN` |
| `iss` | `eduhub` |
| `iat`, `exp` | 발급 시각, +60분(`eduhub.auth.token-ttl: 60m`) |

- `JwtAuthenticationConverter`: 권한 클레임 이름 `role`, 접두사 `ROLE_`. 주체 이름은 `sub`.
- `JwtKeyConfig`의 키 처리 규칙은 그대로 둔다. 단, "dev 로그인이 켜졌는지" 대신 **dev·test 프로필인지**로 무작위 키 허용을 판단한다(dev 로그인이 없어지므로). 설정 이름 `eduhub.auth.allow-random-key`(dev·test yml에서만 `true`)로 바꾼다.

## 6. 보안 필터 체인 (`auth/SecurityConfig`)

```java
http.csrf(AbstractHttpConfigurer::disable)
    .formLogin(AbstractHttpConfigurer::disable)
    .httpBasic(AbstractHttpConfigurer::disable)
    .logout(AbstractHttpConfigurer::disable)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
        .requestMatchers(HttpMethod.GET, "/api/health", "/api/v1/courses", "/api/v1/courses/**").permitAll()
        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
        // 과정 조회(GET) 외의 /api/v1/courses/** 는 관리자. 지금은 이런 API가 없지만 실수로 열리지 않게 막는다
        .requestMatchers("/api/v1/courses", "/api/v1/courses/**").hasRole("ADMIN")
        .requestMatchers("/api/admin/**", "/api/v1/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/**").authenticated()
        .anyRequest().permitAll())   // /api 밖: 화면 파일과 SPA 경로(FrontendResourceConfig)
    .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
    .oauth2ResourceServer(o -> o
        .jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter()))
        .authenticationEntryPoint(entryPoint)
        .accessDeniedHandler(deniedHandler));
```

- 규칙은 위에서부터 처음 맞는 것이 적용된다. `GET /api/v1/courses/**` 공개 규칙이 ADMIN 규칙보다 앞에 있어야 한다.
- 지금 과정·회차 쓰기 API는 `/api/v1/admin/courses/**`, `/api/v1/admin/sessions/**`에 있다(D-18). 요청에 적힌 "`/api/v1/courses/**`의 등록·수정·회차·종료"는 이 경로를 가리키는 것으로 보고 **경로는 옮기지 않는다**(Q2).
- 지금 있는 `requestMatchers(r -> !r.getRequestURI().startsWith("/api/")).permitAll()`은 마지막 `anyRequest().permitAll()`과 겹치므로 지운다.
- 지금의 `POST /api/v1/dev/login` 허용 규칙은 dev 로그인과 함께 지운다(Q3).

## 7. 지우거나 옮기는 것

| 파일 | 처리 |
|---|---|
| `common/DevAuthController`, `common/LoginRequest`, `common/LoginResponse` | 지움 → `auth/AuthController`, `auth/LoginRequest`, `auth/LoginResponse` |
| `common/AdminProperties`(+테스트), `eduhub.admin.employee-ids`, `.env.example`의 `EDUHUB_ADMIN_EMPLOYEE_IDS` | 지움(관리자는 `employee.role`) |
| `common/TokenIssuer`, `JwtKeyConfig`, `AuthProperties`, `SecurityConfig` | `auth`로 옮김 |
| `common/CurrentEmployee`, `CurrentEmployeeArgumentResolver` | 지움 → `auth/CurrentUser` |
| `common/WebMvcSecurityTestConfig`(테스트) | `auth`로 옮기고 `employee()`·`admin()` 도우미가 새 클레임(`sub`=ID, `employee_no`, `role`)을 넣게 고침 |

`OpenApiConfig`의 `bearerAuth` 스킴은 그대로 쓴다. 새 엔드포인트에도 `@Operation`·응답 문서를 붙인다(check-openapi 훅).

## 8. 테스트 시나리오

모두 실제 MariaDB(`eduhub_test`)에서 돈다. 계정은 테스트가 BCrypt로 만들어 넣는다.

| ID | 시나리오 | 기대 |
|---|---|---|
| A1 | 맞는 사번·비밀번호로 로그인 | 200, `expiresIn` 3600, `user.role`, 토큰의 `sub`=ID·`role` 클레임 |
| A2 | 비밀번호 틀림 | 401 `UNAUTHORIZED`, 고정 메시지 |
| A3 | 없는 사번 | 401, A2와 같은 본문 |
| A4 | 빈 사번·비밀번호 | 400 |
| A5 | `GET /api/me` 토큰 없음 / 다른 키로 서명한 토큰 / 만료 토큰 | 각각 401 |
| A6 | `GET /api/me` 정상 토큰 | 200, 토큰의 사용자 |
| A7 | 신청 후 `enrollment.employee_id`가 토큰 사원의 사번 | 일치 |
| A8 | 사원 B가 사원 A만 신청한 회차를 취소 | 404(B에게 활성 신청 없음) |
| A9 | 과정 등록 `POST /api/v1/admin/courses`: 토큰 없음 / 사원 / 관리자 | 401 / 403 / 201 |
| A10 | 관리자 API(명단 조회)에 사원 토큰 | 403 |
| A11 | `GET /api/v1/courses` 토큰 없음 | 200(공개 유지) |
| A12 | dev 시더: 비밀번호 환경 변수 있음 / 없음 / 두 번 실행 | 3개 생성 / 0개·WARN / 중복 없음 |
| A13 | 운영 프로필에서 키 없음 | 기동 실패(기존 `JwtKeyConfigTest` 유지) |

기존 테스트 정리: `DevLoginDisabledIntegrationTest`, `AdminPropertiesTest`는 지우고, `SecurityIntegrationTest`·`CurrentEmployeeArgumentResolverTest`·컨트롤러 테스트는 새 구조에 맞게 고친다.

완료 명령: `./gradlew check`(테스트 + 포맷).

확인용 curl (dev 프로필, 8085)

```bash
curl -s -X POST localhost:8085/api/auth/login -H 'Content-Type: application/json' \
  -d '{"employeeNo":"E1001","password":"'"$EDUHUB_DEV_PASSWORD"'"}'
curl -s localhost:8085/api/me -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8085/api/v1/admin/courses \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'   # 사원 → 403
```

## 9. 질문 (괄호 안이 제안)

- **Q1. `sub`와 신청 데이터:** `sub`는 요청대로 사원 ID로 하되, `enrollment`·`notification`은 지금처럼 사번 문자열을 저장하고 외래 키는 걸지 않는다. ID로 바꾸려면 데이터 이전과 두 단계 컬럼 변경이 필요하다. (제안: 사번 유지, FK 없음)
- **Q2. 관리자 경로:** 과정 쓰기 API를 `/api/v1/courses/**`로 옮기지 않고 `/api/v1/admin/**`에 둔다. 옮기면 D-18과 프런트가 함께 바뀐다. `/api/admin/**`도 ADMIN 규칙에 넣어 둔다. (제안: 옮기지 않음)
- **Q3. dev 로그인과 프런트:** `POST /api/v1/dev/login`을 지우면 지금 프런트 로그인 화면(사번만 입력, `roles` 배열을 읽음)이 동작하지 않는다. 프런트는 범위 밖이다. (제안: 지운다. 프런트는 6장에서 `/api/auth/login`과 `role`로 맞춘다. 그 전까지 화면 로그인은 깨진다는 것을 PR에 적는다) 대안: 프런트를 고칠 때까지 dev 로그인을 남긴다.
- **Q4. 개발용 계정 비밀번호:** 환경 변수 `EDUHUB_DEV_PASSWORD` 하나를 세 계정이 같이 쓴다. 저장소에는 값을 두지 않는다. (제안: 그대로)
- **Q5. `GET /api/me`:** 토큰 클레임만으로 응답한다. 계정이 지워져도 토큰 만료(60분)까지는 응답한다. (제안: 토큰만, DB 조회 없음)
- **Q6. 계정 정보 칼럼:** 요청에 없는 이름·부서는 넣지 않는다. (제안: 넣지 않음)

## 10. 위험

- **범위:** `common`의 인증 코드 이동, 컨트롤러·테스트 다수 수정으로 여러 계층에 걸친다. 작업을 (1) 스키마·엔티티 (2) 로그인·토큰 (3) 보안 체인·`CurrentUser` 교체 (4) dev 시더 (5) 테스트 정리로 나눠 계획에 쓴다.
- **프런트 깨짐(Q3):** 이 PR이 병합되면 프런트 로그인이 프런트 작업 전까지 동작하지 않는다.
- **토큰 호환:** 배포 순간 기존 토큰(`roles` 클레임)은 권한이 없어진다. 만료가 짧고 사용자가 개발자뿐이라 다시 로그인으로 처리한다.
- **문서:** 승인 뒤 `docs/requirements.md`에 D-33을 추가하고 D-30에 "D-33으로 대체"를 적는다. `backend/CLAUDE.md`의 인증·프로필 절을 고친다.
