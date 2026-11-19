# frontend

- UI는 MUI로 만든다.
- API 호출은 OpenAPI 스펙에서 생성한 Axios 클라이언트로만 한다(NFR-04).
- API 인스턴스(`CourseApi`, `CourseAdminApi`, `EnrollmentApi`, `EnrollmentAdminApi`, `DevAuthApi`)는 `apps/course-mfe/src/api/courseApi.ts` 한 곳에서 같은 Axios 인스턴스로만 만들고, 화면은 커스텀 훅을 통해서만 API를 쓴다. 화면은 axios를 직접 알지 않는다.
- 화면 이동은 `react-router-dom`으로 한다. 경로는 `apps/course-mfe/src/App.tsx`에 모은다(D-18, D-31).
  - `/` 과정 목록, `/courses/:id` 과정 상세(신청·취소), `/login` 로그인
  - `/me/enrollments` 내 신청 내역(`RequireAuth`)
  - `/admin` 과정 관리, `/admin/sessions/:sessionId/roster` 명단·수료(`RequireAdmin`)
- 경로를 추가하면 백엔드의 `common/SpaForwardController`에도 추가해야 백엔드 배포본에서 주소를 직접 열 수 있다.
- 상세 화면의 날짜·시간은 `apps/course-mfe/src/format.ts`의 `formatDateTime`으로 보여 준다. API의 UTC 값을 PC 시간대와 상관없이 한국 시간(Asia/Seoul)으로 바꾼다.
- 관리 화면의 회차 시작 시각 입력은 PC의 로컬 시간이며, API의 ISO Instant(UTC)와의 변환은 `apps/course-mfe/src/dateTime.ts`를 쓴다.
- 404·403을 따로 보여 줘야 하면 훅에서 전용 에러(예: `CourseNotFoundError`, `SessionForbiddenError`)로 바꿔 돌려준다.
- 쓰기 요청의 에러는 훅(`useCourseAdmin`, `useEnrollmentActions`, `useCompletion`)이 백엔드 `ErrorResponse.message`를 꺼내 `error`로 돌려준다. 화면은 이를 대화 상자 안이나 화면 위 `Alert`로 보여 준다.

## 인증 (D-30)

- 로그인 상태는 `auth/AuthContext.tsx`의 `useAuth()`(`token`, `employeeId`, `roles`, `isAdmin`, `login`, `logout`)로 쓴다. 토큰은 sessionStorage `eduhub.auth`에 두고, 콘솔·화면·URL에 내보내지 않는다. 오류 객체를 렌더링하지 않는다(요청 config에 토큰이 있다).
- `courseApi.ts`의 인터셉터가 만료되지 않은 토큰만 붙인다(로그인 요청 제외). 401이면 보낸 토큰이 현재 토큰일 때만 로그아웃하고, 공개 GET(`/api/v1/courses/**`, `/api/health`)은 토큰 없이 한 번 다시 요청하며, 보호 화면(`/me/**`, `/admin/**`)에서만 `/login`으로 보낸다. 백엔드 공개 경로가 바뀌면 `isPublicGet`도 함께 고친다.
- 로그인 화면은 개발 환경 전용(dev 로그인, 비밀번호 없음)이다. 404면 SSO 안내를 보여 준다.
- 목록 훅(`useMyEnrollments`, `useSessionRoster`)은 조건이나 토큰이 바뀐 렌더에서 낡은 데이터 대신 로딩을 돌려주고, `reload` 실패 시에는 기존 데이터를 유지한다. 새 목록 훅도 같은 규칙을 따른다.
- 화면 테스트는 `test-utils.tsx`의 `renderWithProviders`(`MemoryRouter`와 `AuthProvider`, 로그인 상태는 `fakeAuth`)로 감싼다.

## 명령

`frontend/`에서 실행한다.

```bash
npm run dev                  # course-mfe 실행 (포트 3001, /api는 localhost:8085로 프록시)
npm test -w apps/course-mfe  # 테스트
npm run generate:api         # 실행 중인 백엔드의 스펙으로 클라이언트 생성
```

다른 포트의 백엔드로 확인할 때는 커밋되는 설정을 고치지 않고 실행 인자와 환경 변수만 바꾼다.

```bash
cd apps/course-mfe && API_PROXY_TARGET=http://localhost:8088 npx vite --port 3003 --strictPort
```

## 주의

- `packages/api-client/src/generated/`는 생성 코드이므로 직접 고치지 않는다. 스펙이 바뀌면 다시 생성해 커밋한다. 새 타입이나 API 클래스를 쓰려면 `packages/api-client/src/index.ts`에서 내보낸다.
- 다른 포트의 백엔드로 생성했다면 `generated/base.ts`의 `BASE_PATH`가 그 포트로 바뀐다. 커밋 전에 8085로 되돌린다(앱은 `basePath: ""`를 쓰므로 동작에는 영향이 없다).
- Node 22.11 기준으로 Vite 6, Vitest 3, `@openapitools/openapi-generator-cli` 2.32.0, react-router-dom 7에 고정했다. 그보다 새 버전은 Node 22.12 이상이 필요할 수 있다.
- `generate:api`를 처음 실행하면 생성기 jar를 내려받는다. 사내망의 TLS 검사로 "self-signed certificate in certificate chain"이 나면 사내 루트 인증서 파일을 `NODE_EXTRA_CA_CERTS`로 지정한다. 인증서는 PowerShell에서 `Cert:\LocalMachine\Root`의 "Somansa Root CA"를 PEM으로 내보내 쓸 수 있다. jar를 받은 뒤에는 필요 없다.
- 생성기는 `openapitools.json`을 실행 위치에서 찾으므로 `frontend/`와 `packages/api-client/` 두 곳에 있다. 버전을 바꿀 때 둘 다 고친다.
- `apps/course-mfe/src/categories.ts`의 분류 값은 임시 값이다.
