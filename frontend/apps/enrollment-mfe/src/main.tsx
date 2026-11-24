import { clearAuth, loadAuth, setAuthHooks } from "@eduhub/api-client";
import { CssBaseline } from "@mui/material";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import MyEnrollmentsPage from "./pages/MyEnrollmentsPage";
import RosterPage from "./pages/RosterPage";

/*
 * 단독 실행용(포트 3002). 실제 화면은 shell이 remote로 불러 쓴다.
 * 로그인 화면은 shell 담당이라 여기에는 없다. 이 출처(sessionStorage)에 로그인 값이 있어야 API가 성공한다.
 */
setAuthHooks({
  getToken: () => loadAuth()?.token ?? null,
  clearToken: clearAuth,
  onUnauthorized: () => {},
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <CssBaseline />
    <BrowserRouter>
      <Routes>
        <Route path="/me/enrollments" element={<MyEnrollmentsPage />} />
        <Route path="/admin/sessions/:sessionId/roster" element={<RosterPage />} />
        <Route path="*" element={<Navigate to="/me/enrollments" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
