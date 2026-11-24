import { federation } from "@module-federation/vite";
import react from "@vitejs/plugin-react";
import { loadEnv, type Plugin } from "vite";
import { defineConfig } from "vitest/config";

/** 모든 MFE가 한 벌만 쓰는 패키지. 인증 상태(httpClient, 토큰 훅)도 하나여야 하므로 api-client도 넣는다. */
const SINGLETONS = [
  "react",
  "react-dom", // 라우터 상태를 shell과 remote가 함께 쓴다
  "react-router-dom",
  "@mui/material",
  "@emotion/react",
  "@eduhub/api-client", // 토큰 처리(http 인터셉터)를 한 벌만
];

const REMOTE_MODULE = /^(courseMfe|enrollmentMfe)\//;
const STUB_PREFIX = "\0eduhub-remote:";

/**
 * 테스트 전용: remote 모듈 이름을 가상 모듈로 해석만 해 준다. 내용은 테스트의 vi.mock이 채운다.
 * mock 없이 불러오면 바로 실패해서, 실수로 실제 remote에 기대는 테스트를 막는다.
 */
const remoteStubForTest: Plugin = {
  name: "eduhub:remote-stub-for-test",
  enforce: "pre",
  resolveId(id) {
    return REMOTE_MODULE.test(id) ? `${STUB_PREFIX}${id}` : null;
  },
  load(id) {
    if (!id.startsWith(STUB_PREFIX)) return null;
    const name = id.slice(STUB_PREFIX.length);
    return `throw new Error(${JSON.stringify(`테스트에서는 ${name}을(를) vi.mock으로 대신해야 한다`)});`;
  },
};

export default defineConfig(({ mode }) => {
  // 다른 포트의 백엔드로 확인할 때만 API_PROXY_TARGET 환경 변수로 바꾼다. 기본은 8085다.
  const { API_PROXY_TARGET } = loadEnv(mode, ".", "API_");
  const proxy = { "/api": API_PROXY_TARGET || "http://localhost:8085" };

  const deploy = mode === "deploy"; // npm run build:deploy (한 주소로 배포할 때)
  const entry = (port: number, path: string) =>
    deploy ? `/mfe/${path}/remoteEntry.js` : `http://localhost:${port}/remoteEntry.js`;

  return {
    plugins: [
      react(),
      // 테스트에서는 remote를 vi.mock으로 대신하므로 federation 대신 해석용 플러그인만 쓴다.
      mode === "test"
        ? remoteStubForTest
        : federation({
            name: "shell",
            remotes: {
              courseMfe: { type: "module", name: "courseMfe", entry: entry(3001, "course") },
              // courseMfe: { type: "module", name: "courseMfe", entry: "http://localhost:3001/remoteEntry.js", },
              enrollmentMfe: {
                type: "module",
                name: "enrollmentMfe",
                entry: entry(3002, "enrollment"),
              },
              // enrollmentMfe: { type: "module", name: "enrollmentMfe", entry: "http://localhost:3002/remoteEntry.js", },
            },
            shared: Object.fromEntries(SINGLETONS.map((name) => [name, { singleton: true }])),
            // remote 타입은 src/remotes.d.ts에 직접 적는다.
            dts: false,
          }),
    ],
    server: { port: 3000, strictPort: true, proxy },
    preview: { port: 3000, strictPort: true, proxy },
    build: { target: "esnext" },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test-setup.ts"],
    },
  };
});
