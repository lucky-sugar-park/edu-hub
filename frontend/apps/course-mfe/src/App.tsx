import { AppBar, Box, Button, Container, Toolbar, Typography } from "@mui/material";
import { startTransition } from "react";
import {
  NavLink,
  Link as RouterLink,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { RequireAdmin } from "./auth/RequireAdmin";
import { RequireAuth } from "./auth/RequireAuth";
import { AdminCoursePage } from "./pages/AdminCoursePage";
import { CourseDetailPage } from "./pages/CourseDetailPage";
import { CourseListPage } from "./pages/CourseListPage";
import { LoginPage } from "./pages/LoginPage";

/** T6·T7에서 실제 화면으로 바꾼다. */
function ComingSoon({ title }: { title: string }) {
  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Typography variant="h6" component="h1">
        {title}
      </Typography>
      <Typography color="text.secondary">준비 중입니다.</Typography>
    </Container>
  );
}

/**
 * 화면 경로(D-18, D-31): / 과정 목록, /courses/:id 과정 상세, /login 로그인,
 * /me/enrollments 내 신청(로그인), /admin 과정 관리(관리자), /admin/sessions/:sessionId/roster 명단(관리자).
 */
export function App() {
  const { token, employeeId, isAdmin, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  // 로그아웃과 이동을 한 번에 반영한다. 따로 반영되면 보호 화면의 RequireAuth가 먼저 /login으로 보낸다.
  function handleLogout() {
    startTransition(() => {
      logout();
      navigate("/");
    });
  }

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
          {token && (
            <Button component={NavLink} to="/me/enrollments">
              내 신청
            </Button>
          )}
          {isAdmin && (
            <Button component={NavLink} to="/admin">
              과정 관리
            </Button>
          )}
          <Box sx={{ flexGrow: 1 }} />
          {token ? (
            <>
              <Typography variant="body2" component="span" sx={{ mr: 1 }}>
                {employeeId}
              </Typography>
              <Button onClick={handleLogout}>로그아웃</Button>
            </>
          ) : (
            <Button
              component={RouterLink}
              to="/login"
              state={{ from: `${location.pathname}${location.search}` }}
            >
              로그인
            </Button>
          )}
        </Toolbar>
      </AppBar>
      <Routes>
        <Route path="/" element={<CourseListPage />} />
        <Route path="/courses/:id" element={<CourseDetailPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/me/enrollments"
          element={
            <RequireAuth>
              <ComingSoon title="내 신청 내역" />
            </RequireAuth>
          }
        />
        <Route
          path="/admin"
          element={
            <RequireAdmin>
              <AdminCoursePage />
            </RequireAdmin>
          }
        />
        <Route
          path="/admin/sessions/:sessionId/roster"
          element={
            <RequireAdmin>
              <ComingSoon title="회차 명단" />
            </RequireAdmin>
          }
        />
      </Routes>
    </>
  );
}
