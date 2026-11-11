import react from "@vitejs/plugin-react";
import { loadEnv } from "vite";
import { defineConfig } from "vitest/config";

export default defineConfig(({ mode }) => {
  // 다른 포트의 백엔드로 확인할 때만 API_PROXY_TARGET 환경 변수로 바꾼다. 기본은 8085다.
  const { API_PROXY_TARGET } = loadEnv(mode, ".", "API_");

  return {
    plugins: [react()],
    server: {
      port: 3001,
      strictPort: true,
      proxy: {
        "/api": API_PROXY_TARGET || "http://localhost:8085",
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test-setup.ts",
    },
  };
});
