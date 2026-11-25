# frontend

- UI는 MUI로 만든다.
- API 호출은 OpenAPI 스펙에서 생성한 Axios 클라이언트로만 한다(NFR-04).
- 화면은 Module Federation으로 나눈다(NFR-07). `apps/shell`(3000)이 host이고 `apps/course-mfe`(3001), `apps/enrollment-mfe`(3002)가 remote다.
- API 인스턴스는 `packages/api-client`의 공유 Axios 인스턴스(`src/auth.ts`의 `httpClient`) 하나로, `src/index.ts`에서만 만든다(`authApi`=`AuthApi`, `meApi`=`MeApi`, `courseApi`, `courseAdminApi`, `enrollmentApi`, `adminEnrollmentApi`). 이 패키지는 모든 앱이 singleton으로 공유하므로 토큰·인터셉터도 하나다. 화면은 커스텀 훅을 통해서만 API를 쓰고, axios를 직접 알지 않는다.
- 화면 이동은 `react-router-dom`으로 한다. 경로는 `apps/shell/src/App.tsx`에 모은다(D-18, D-31).
  - `/`(→ `/courses`) 과정 목록, `/courses/:courseId` 과정 상세(신청·취소), `/login` 로그인
  - `/me/enrollments` 내 신청 내역(`RequireAuth`)
  - `/admin` 과정 관리, `/admin/sessions/:sessionId/roster` 명단·수료(`RequireAdmin`)
- remote가 내보내는 모듈 이름·props는 `apps/shell/src/remotes.d.ts`와 각 remote의 `vite.config.ts`(`exposes`)를 함께 맞춘다. remote 화면은 shell의 `RemoteBoundary`로 감싸, remote가 꺼져 있어도 shell은 동작한다.
  - course-mfe: `./CourseListPage`, `./CourseDetailPage`(`renderSessionAction` prop), `./CourseFormPage`
  - enrollment-mfe: `./EnrollButton`, `./MyEnrollmentsPage`, `./RosterPage`
- 경로를 추가하면 백엔드의 화면 경로 forward(`SpaForwardController`)에도 추가해야 배포본에서 주소를 직접 열 수 있다.
- 날짜·시간은 각 앱의 `format.ts`(`formatDateTime`)로 보여 준다. API의 UTC 값을 PC 시간대와 상관없이 한국 시간(Asia/Seoul)으로 바꾼다. 관리 화면의 회차 시작 시각 입력은 PC의 로컬 시간이며, API의 ISO Instant(UTC)와의 변환은 `apps/course-mfe/src/dateTime.ts`를 쓴다.
- 404·403을 따로 보여 줘야 하면 훅에서 전용 에러(예: `CourseNotFoundError`)로 바꿔 돌려준다.
- 쓰기 요청의 에러는 훅(`useCourseAdmin`, `useEnrollmentActions`, `useCompletion`)이 백엔드 `ErrorResponse.message`를 꺼내 `error`로 돌려준다(`toErrorMessage`). 화면은 이를 대화 상자 안이나 화면 위 `Alert`로 보여 준다.

## 소스 구조

```
apps/shell/src/          # host: main.tsx, App.tsx(상단 바·라우팅), theme.ts, remotes.d.ts
  auth/                  # AuthContext(useAuth), RequireAuth, RequireAdmin
  pages/LoginPage.tsx    # 사번·비밀번호 로그인
  components/RemoteBoundary.tsx
apps/course-mfe/src/     # 과정 목록·상세·관리(hooks/, pages/, components/, api/courseApi.ts)
apps/enrollment-mfe/src/ # 신청 버튼, 내 신청 내역, 명단·수료(hooks/, pages/, components/, pages/labels.ts)
packages/api-client/src/ # generated/(생성 코드), auth.ts(토큰 보관·인터셉터·401 처리), errors.ts, index.ts(API 인스턴스)
```

- 신청 상태·수료 실패 사유의 한국어 이름은 `apps/enrollment-mfe/src/pages/labels.ts`에 둔다.
- 각 앱의 화면 테스트는 그 앱의 `test-utils.tsx`(`renderWithProviders`, `fakeAuth`)로 감싼다. shell 테스트에서 remote 모듈은 `vi.mock`으로 대신한다(mock 없이 불러오면 바로 실패한다).

## 인증 (D-33)

- 로그인은 `POST /api/auth/login {employeeNo, password}`(`authApi.login`)이다. 응답 `{accessToken, expiresIn(초), user{id, employeeNo, role}}`를 저장 형식 `StoredAuth`로 바꾼다: `employeeId`=`user.employeeNo`, `roles`=`[user.role]`, `expiresAt`=요청 시각+`expiresIn`. 비밀번호는 요청에만 쓰고 저장·로그·URL에 두지 않는다.
- 로그인 상태는 `apps/shell/src/auth/AuthContext.tsx`의 `useAuth()`(`token`, `employeeId`, `roles`, `isAdmin`, `login(employeeNo, password)`, `logout`)로 쓴다. 토큰은 sessionStorage `eduhub.auth`에 두고(읽기·쓰기는 `packages/api-client/src/auth.ts`), 콘솔·화면·URL에 내보내지 않는다. 오류 객체를 렌더링하지 않는다(요청 config에 토큰이 있다).
- `auth.ts`의 인터셉터가 만료되지 않은 토큰만 붙인다. **로그인 요청에는 토큰을 붙이지 않는다**(만료 토큰이 붙으면 백엔드가 로그인 자체를 401로 막는다). 401이면 보낸 토큰이 현재 토큰일 때만 로그아웃하고, 공개 GET(`/api/v1/courses/**`, `/api/health`)은 토큰 없이 한 번 다시 요청하며, 보호 화면(`/me/**`, `/admin/**`)에서만 `/login`으로 보낸다. 백엔드 공개 경로가 바뀌면 `isPublicGet`도 함께 고친다.
- 로그인 화면은 사번 없음과 비밀번호 틀림을 구분하지 않고 백엔드의 401 메시지를 그대로 보여 주며, 실패하면 비밀번호 입력을 비운다. 비밀번호는 앞뒤 공백을 자르지 않는다.
- 목록 훅(`useMyEnrollments`, `useSessionRoster`)은 조건이나 토큰이 바뀐 렌더에서 낡은 데이터 대신 로딩을 돌려주고, `reload` 실패 시에는 기존 데이터를 유지한다. 새 목록 훅도 같은 규칙을 따른다.

## 명령

`frontend/`에서 실행한다.

```bash
npm run dev                           # shell 실행(3000, /api는 localhost:8085로 프록시)
npm run dev -w apps/course-mfe        # remote(3001). 화면을 보려면 shell과 두 remote를 모두 띄운다
npm run dev -w apps/enrollment-mfe    # remote(3002)
npm test                              # 모든 workspace 테스트
npm run build                         # 모든 workspace 빌드
npm run generate:api                  # 실행 중인 백엔드(8085)의 스펙으로 클라이언트 생성
```

배포본(한 주소)은 앱마다 `npm run build:deploy -w apps/<앱>` 뒤 `scripts/deploy-frontend.ps1 -App <shell|course-mfe|enrollment-mfe>`로 `run/web`에 놓는다.

다른 포트의 백엔드로 확인할 때는 커밋되는 설정을 고치지 않고 실행 인자와 환경 변수만 바꾼다(`.claude/launch.json`의 `backend-8088`, `shell-8088` 참고).

```bash
cd apps/shell && API_PROXY_TARGET=http://localhost:8088 npx vite --port 3003 --strictPort
```

## 주의

- `packages/api-client/src/generated/`는 생성 코드이므로 직접 고치지 않는다. 스펙이 바뀌면 다시 생성해 커밋한다. 새 API 클래스를 쓰려면 `packages/api-client/src/index.ts`에서 인스턴스를 만든다.
- 다른 포트의 백엔드로 생성했다면(예: `npx openapi-generator-cli generate -i http://localhost:8088/v3/api-docs ...`) `generated/base.ts`의 `BASE_PATH`가 그 포트로 바뀐다. 커밋 전에 8085로 되돌린다(앱은 `basePath: ""`를 쓰므로 동작에는 영향이 없다).
- Node 22.11 기준으로 Vite 6, Vitest 3, `@openapitools/openapi-generator-cli` 2.32.0, react-router-dom 7에 고정했다. 그보다 새 버전은 Node 22.12 이상이 필요할 수 있다.
- `generate:api`를 처음 실행하면 생성기 jar를 내려받는다. 사내망의 TLS 검사로 "self-signed certificate in certificate chain"이 나면 사내 루트 인증서 파일을 `NODE_EXTRA_CA_CERTS`로 지정한다. 인증서는 PowerShell에서 `Cert:\LocalMachine\Root`의 "Somansa Root CA"를 PEM으로 내보내 쓸 수 있다. jar를 받은 뒤에는 필요 없다.
- 생성기는 `openapitools.json`을 실행 위치에서 찾으므로 `frontend/`와 `packages/api-client/` 두 곳에 있다. 버전을 바꿀 때 둘 다 고친다.
- `apps/course-mfe/src/categories.ts`의 분류 값은 임시 값이다.
