import { federation } from "@module-federation/vite";
import react from "@vitejs/plugin-react";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";

/** shell과 같은 목록이어야 한다(apps/shell/vite.config.ts). 인증 상태가 하나여야 하므로 api-client도 넣는다. */
const SINGLETONS = [
  "react",
  "react-dom",
  "react-router-dom",
  "@mui/material",
  "@emotion/react",
  "@eduhub/api-client",
];

// shell(3000)이 불러가는 remote다. 노출 이름·props는 apps/shell/src/remotes.d.ts와 맞춘다.
export default defineConfig(({ mode }) => {
  // 다른 포트의 백엔드로 확인할 때만 API_PROXY_TARGET 환경 변수로 바꾼다. 기본은 8085다.
  const { API_PROXY_TARGET } = loadEnv(mode, ".", "API_");
  const proxy = { "/api": API_PROXY_TARGET || "http://localhost:8085" };

  return {
    // 한 주소로 배포할 때(npm run build:deploy) 파일은 /mfe/course/ 아래에 놓인다(scripts/deploy-frontend.ps1).
    base: mode === "deploy" ? "/mfe/course/" : "/",
    plugins: [
      react(),
      // 테스트는 화면 모듈을 직접 불러오므로 federation이 필요 없다.
      ...(mode === "test"
        ? []
        : [
            federation({
              name: "courseMfe",
              filename: "remoteEntry.js",
              exposes: {
                "./CourseListPage": "./src/pages/CourseListPage.tsx",
                "./CourseDetailPage": "./src/pages/CourseDetailPage.tsx",
                "./CourseFormPage": "./src/pages/AdminCoursePage.tsx",
              },
              shared: Object.fromEntries(SINGLETONS.map((name) => [name, { singleton: true }])),
              // remote 타입은 shell의 src/remotes.d.ts에 직접 적는다.
              dts: false,
            }),
          ]),
    ],
    server: { port: 3001, strictPort: true, proxy },
    preview: { port: 3001, strictPort: true, proxy },
    build: { target: "esnext" },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test-setup.ts"],
    },
  };
});
