import { Route, Routes } from "react-router-dom";
import { CourseDetailPage } from "./pages/CourseDetailPage";
import { CourseListPage } from "./pages/CourseListPage";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<CourseListPage />} />
      <Route path="/courses/:id" element={<CourseDetailPage />} />
    </Routes>
  );
}
