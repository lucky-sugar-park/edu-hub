import { AppBar, Box, Button, Toolbar, Typography } from "@mui/material";
import { lazy, startTransition } from "react";
import {
  Navigate,
  NavLink,
  Link as RouterLink,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import type { SessionActionContext } from "courseMfe/CourseDetailPage";
import { useAuth } from "./auth/AuthContext";
import { RequireAdmin } from "./auth/RequireAdmin";
import { RequireAuth } from "./auth/RequireAuth";
import { RemoteBoundary } from "./components/RemoteBoundary";
import { LoginPage } from "./pages/LoginPage";

// remote 화면은 처음 쓸 때 불러온다. remote가 꺼져 있으면 RemoteBoundary가 안내를 보여 준다.
const CourseListPage = lazy(() =>
  import("courseMfe/CourseListPage").then((m) => ({ default: m.CourseListPage })),
);
const CourseDetailPage = lazy(() => import("courseMfe/CourseDetailPage"));
const CourseFormPage = lazy(() => import("courseMfe/CourseFormPage"));
const EnrollButton = lazy(() => import("enrollmentMfe/EnrollButton"));
const MyEnrollmentsPage = lazy(() => import("enrollmentMfe/MyEnrollmentsPage"));
const RosterPage = lazy(() => import("enrollmentMfe/RosterPage"));

const COURSE = "과정 화면";
const ENROLLMENT = "신청 화면";

/** 과정 상세의 회차 행에 신청 버튼을 끼운다. 신청 remote만 꺼져도 상세는 그대로 보인다. */
function renderSessionAction({ course, session, reload }: SessionActionContext) {
  return (
    <RemoteBoundary name="신청 버튼" inline>
      <EnrollButton course={course} session={session} onChanged={reload} />
    </RemoteBoundary>
  );
}

/**
 * 화면 경로(D-18, D-31): / 과정 목록, /courses/:courseId 과정 상세, /login 로그인,
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
      {/* 경로가 바뀌면 오류 경계를 새로 만들어, 한 화면의 실패가 다른 화면에 남지 않게 한다. */}
      <Routes key={location.pathname}>
        {/* <Route path="/" element={ <RemoteBoundary name={COURSE}><CourseListPage /> </RemoteBoundary> } /> */}
        <Route path="/" element={<Navigate to="/courses" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/courses"
          element={
            <RemoteBoundary name={COURSE}>
              <CourseListPage />
            </RemoteBoundary>
          }
        />
        <Route
          path="/courses/:courseId"
          element={
            <RemoteBoundary name={COURSE}>
              <CourseDetailPage renderSessionAction={renderSessionAction} />
            </RemoteBoundary>
          }
        />
        <Route
          path="/me/enrollments"
          element={
            <RequireAuth>
              <RemoteBoundary name={ENROLLMENT}>
                <MyEnrollmentsPage />
              </RemoteBoundary>
            </RequireAuth>
          }
        />
        <Route
          path="/admin"
          element={
            <RequireAdmin>
              <RemoteBoundary name={COURSE}>
                <CourseFormPage />
              </RemoteBoundary>
            </RequireAdmin>
          }
        />
        <Route
          path="/admin/courses/new"
          element={
            <RequireAdmin>
              <RemoteBoundary name={COURSE}>
                <CourseFormPage />
              </RemoteBoundary>
            </RequireAdmin>
          }
        />
        <Route
          path="/admin/sessions/:sessionId/roster"
          element={
            <RequireAdmin>
              <RemoteBoundary name={ENROLLMENT}>
                <RosterPage />
              </RemoteBoundary>
            </RequireAdmin>
          }
        />
      </Routes>
    </>
  );
}
