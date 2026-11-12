import { AppBar, Button, Toolbar, Typography } from "@mui/material";
import { NavLink, Route, Routes } from "react-router-dom";
import { AdminCoursePage } from "./pages/AdminCoursePage";
import { CourseDetailPage } from "./pages/CourseDetailPage";
import { CourseListPage } from "./pages/CourseListPage";

/** 화면 경로(D-18): / 과정 목록, /courses/:id 과정 상세, /admin 과정 관리. */
export function App() {
  return (
    <>
      <AppBar position="static" color="default" elevation={0}>
        <Toolbar variant="dense">
          <Typography variant="subtitle1" component="span" sx={{ mr: 2, fontWeight: 600 }}>
            EduHub
          </Typography>
          <Button component={NavLink} to="/" end>
            과정 목록
          </Button>
          <Button component={NavLink} to="/admin">
            과정 관리
          </Button>
        </Toolbar>
      </AppBar>
      <Routes>
        <Route path="/" element={<CourseListPage />} />
        <Route path="/courses/:id" element={<CourseDetailPage />} />
        <Route path="/admin" element={<AdminCoursePage />} />
      </Routes>
    </>
  );
}
