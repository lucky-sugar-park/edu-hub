import { AppBar, Button, Toolbar, Typography } from "@mui/material";
import { NavLink, Route, Routes } from "react-router-dom";
import { AdminCoursePage } from "./pages/AdminCoursePage";
import { CourseListPage } from "./pages/CourseListPage";

/** 화면 경로. 상세 화면(FR-02)은 /courses/:id로 추가한다. */
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
        <Route path="/admin" element={<AdminCoursePage />} />
      </Routes>
    </>
  );
}
