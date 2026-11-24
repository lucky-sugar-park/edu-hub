import { federation } from "@module-federation/vite";
import react from "@vitejs/plugin-react";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";

/** shell과 같은 목록이어야 한다(apps/shell/vite.config.ts). 인증 상태를 나누지 않으려고 api-client도 한 벌만 쓴다. */
const SINGLETONS = [
  "react",
  "react-dom",
  "react-router-dom",
  "@mui/material",
  "@emotion/react",
  "@eduhub/api-client",
];

export default defineConfig(({ mode }) => {
  // 다른 포트의 백엔드로 확인할 때만 API_PROXY_TARGET 환경 변수로 바꾼다. 기본은 8085다.
  const { API_PROXY_TARGET } = loadEnv(mode, ".", "API_");
  const proxy = { "/api": API_PROXY_TARGET || "http://localhost:8085" };

  return {
    // 한 주소로 배포할 때(npm run build:deploy) 파일은 /mfe/enrollment/ 아래에 놓인다(scripts/deploy-frontend.ps1).
    base: mode === "deploy" ? "/mfe/enrollment/" : "/",
    plugins: [
      react(),
      // 테스트는 노출 모듈을 직접 불러오므로 federation이 필요 없다.
      ...(mode === "test"
        ? []
        : [
            federation({
              name: "enrollmentMfe",
              filename: "remoteEntry.js",
              // shell의 src/remotes.d.ts 계약과 맞춘다(모두 default export).
              exposes: {
                "./EnrollButton": "./src/components/EnrollButton.tsx",
                "./MyEnrollmentsPage": "./src/pages/MyEnrollmentsPage.tsx",
                "./RosterPage": "./src/pages/RosterPage.tsx",
              },
              shared: Object.fromEntries(SINGLETONS.map((name) => [name, { singleton: true }])),
              dts: false,
            }),
          ]),
    ],
    server: { port: 3002, strictPort: true, proxy },
    preview: { port: 3002, strictPort: true, proxy },
    build: { target: "esnext" },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test-setup.ts",
    },
  };
});
