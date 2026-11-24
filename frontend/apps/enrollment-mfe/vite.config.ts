import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// 화면 진입점은 apps/shell이다. 여기서는 테스트만 돌린다.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    setupFiles: "./src/test-setup.ts",
  },
});
