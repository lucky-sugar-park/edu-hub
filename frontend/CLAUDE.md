# frontend

- UI는 MUI로 만든다.
- API 호출은 OpenAPI 스펙에서 생성한 Axios 클라이언트로만 한다(NFR-04).
- `CourseApi`, `CourseAdminApi` 인스턴스는 `apps/course-mfe/src/api/courseApi.ts` 한 곳에서만 만들고, 화면은 커스텀 훅을 통해서만 API를 쓴다.
- 화면 경로는 react-router로 `src/App.tsx`에 둔다: `/` 과정 목록, `/admin` 과정 관리(D-18).
- 쓰기 요청의 에러는 `useCourseAdmin`이 백엔드 `ErrorResponse.message`를 꺼내 `error`로 돌려준다. 화면은 이를 대화 상자 안의 `Alert`로 보여 준다.
- 회차 시작 시각은 API에서 ISO Instant(UTC)이고 화면에서는 로컬 시간이다. 변환은 `src/dateTime.ts`를 쓴다.

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
