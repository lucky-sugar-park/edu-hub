// 단독 실행용 진입점(npm run dev, 포트 3001). shell에 붙을 때는 이 파일을 쓰지 않는다.
// 테마·인증·신청 버튼은 shell이 맡으므로 여기서는 목록과 상세만 띄운다.
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { CourseDetailPage } from "./pages/CourseDetailPage";
import { CourseListPage } from "./pages/CourseListPage";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/courses" replace />} />
        <Route path="/courses" element={<CourseListPage />} />
        <Route path="/courses/:courseId" element={<CourseDetailPage />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
