import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from "@mui/material";
import type { CourseDetailResponse, MyEnrollmentResponse, SessionResponse } from "@eduhub/api-client";
import { useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { formatDateTime } from "../format";
import { CourseNotFoundError, useCourse } from "../hooks/useCourse";
import { useEnrollmentActions } from "../hooks/useEnrollmentActions";
import { useMyEnrollments } from "../hooks/useMyEnrollments";
import { statusLabel } from "../statusLabels";

/** 신청 불가 사유. 신청할 수 있으면 null. 최종 판단은 백엔드(409)다. */
function applyBlockedReason(course: CourseDetailResponse, session: SessionResponse, now: number) {
  if (course.status === "CLOSED") return "종료된 과정입니다";
  if (session.status === "CLOSED") return "종료된 회차입니다";
  if (hasStarted(session, now)) return "이미 시작된 회차입니다";
  return null;
}

function hasStarted(session: SessionResponse, now: number): boolean {
  return session.startAt !== undefined && Date.parse(session.startAt) <= now;
}

interface CancelTarget {
  session: SessionResponse;
  enrollment: MyEnrollmentResponse;
}

export function CourseDetailPage() {
  const { id } = useParams();
  // 숫자가 아니면 NaN이 되고, 훅이 요청 없이 "과정 없음"으로 처리한다.
  const { data: course, loading, error, reload: reloadCourse } = useCourse(Number(id));
  const sessions = course?.sessions ?? [];
  const { token } = useAuth();
  const myEnrollments = useMyEnrollments(undefined, 0, 100);
  const actions = useEnrollmentActions();
  const [notice, setNotice] = useState<string | null>(null);
  const [cancelTarget, setCancelTarget] = useState<CancelTarget | null>(null);

  // 활성(확정·대기) 신청을 회차별로 찾는다.
  const activeBySession = new Map<number, MyEnrollmentResponse>();
  for (const enrollment of myEnrollments.data?.content ?? []) {
    if (
      enrollment.sessionId !== undefined &&
      (enrollment.status === "CONFIRMED" || enrollment.status === "WAITING")
    ) {
      activeBySession.set(enrollment.sessionId, enrollment);
    }
  }
  const now = Date.now();
  // 내 신청을 받아 오기 전, 다시 받아 오는 중, 받아 오기 실패한 동안은 신청·취소를 막는다(틀린 상태 방지).
  const enrollmentsUnavailable =
    token !== null &&
    (myEnrollments.loading || myEnrollments.error !== null || myEnrollments.data === null);
  const enrollmentsReason = myEnrollments.error
    ? "내 신청 내역을 불러오지 못했습니다"
    : "내 신청 내역을 확인하는 중입니다";

  function reloadAll() {
    reloadCourse();
    myEnrollments.reload();
  }

  async function handleEnroll(sessionId: number) {
    setNotice(null);
    const result = await actions.enroll(sessionId);
    // 실패해도 서버 상태와 맞추려고 다시 불러온다(예: 이미 신청된 경우의 409).
    if (!result) {
      reloadAll();
      return;
    }
    setNotice(
      result.status === "CONFIRMED"
        ? "신청이 확정되었습니다"
        : `대기 ${result.waitingNumber}번으로 신청되었습니다`,
    );
    reloadAll();
  }

  async function handleCancel() {
    if (!cancelTarget || cancelTarget.session.id === undefined) return;
    const result = await actions.cancel(cancelTarget.session.id);
    if (!result) {
      reloadAll();
      return;
    }
    setCancelTarget(null);
    setNotice("신청이 취소되었습니다");
    reloadAll();
  }

  function openCancel(target: CancelTarget) {
    actions.clearError();
    setNotice(null);
    setCancelTarget(target);
  }

  function closeCancel() {
    actions.clearError();
    setCancelTarget(null);
  }

  function renderEnrollmentCells(session: SessionResponse) {
    if (!course || session.id === undefined) return null;
    const sessionId = session.id;
    const enrollment = activeBySession.get(sessionId);

    if (!token) {
      return (
        <>
          <TableCell>-</TableCell>
          <TableCell>
            <Button
              size="small"
              component={RouterLink}
              to="/login"
              state={{ from: `/courses/${course.id}` }}
            >
              로그인 후 신청
            </Button>
          </TableCell>
        </>
      );
    }

    if (enrollment) {
      const started = hasStarted(session, now);
      const cancelReason = started
        ? "이미 시작된 회차는 취소할 수 없습니다"
        : enrollmentsUnavailable
          ? enrollmentsReason
          : null;
      const label =
        enrollment.status === "WAITING" ? `대기 ${enrollment.waitingNumber}번` : "확정";
      return (
        <>
          <TableCell>
            <Chip
              label={label}
              size="small"
              color={enrollment.status === "WAITING" ? "warning" : "success"}
            />
          </TableCell>
          <TableCell>
            <Tooltip title={cancelReason ?? ""}>
              <span>
                <Button
                  size="small"
                  color="error"
                  aria-label={cancelReason ? `취소 (${cancelReason})` : undefined}
                  disabled={cancelReason !== null || actions.submitting}
                  onClick={() => openCancel({ session, enrollment })}
                >
                  취소
                </Button>
              </span>
            </Tooltip>
          </TableCell>
        </>
      );
    }

    const reason =
      applyBlockedReason(course, session, now) ?? (enrollmentsUnavailable ? enrollmentsReason : null);
    return (
      <>
        <TableCell>-</TableCell>
        <TableCell>
          <Tooltip title={reason ?? ""}>
            <span>
              <Button
                size="small"
                variant="outlined"
                aria-label={reason ? `신청 (${reason})` : undefined}
                disabled={reason !== null || actions.submitting}
                onClick={() => void handleEnroll(sessionId)}
              >
                신청
              </Button>
            </span>
          </Tooltip>
        </TableCell>
      </>
    );
  }

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Button component={RouterLink} to="/" sx={{ mb: 2 }}>
        ← 목록으로
      </Button>

      {loading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress aria-label="불러오는 중" />
        </Box>
      )}

      {!loading && error instanceof CourseNotFoundError && (
        <Alert severity="warning">과정을 찾을 수 없습니다. 삭제되었거나 주소가 잘못되었습니다.</Alert>
      )}

      {!loading && error && !(error instanceof CourseNotFoundError) && (
        <Alert severity="error">과정 정보를 불러오지 못했습니다.</Alert>
      )}

      {notice && (
        <Alert severity="success" onClose={() => setNotice(null)} sx={{ mb: 2 }}>
          {notice}
        </Alert>
      )}
      {token && myEnrollments.error && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={myEnrollments.reload}>
              다시 시도
            </Button>
          }
        >
          내 신청 내역을 불러오지 못했습니다
        </Alert>
      )}
      {actions.error && !cancelTarget && (
        <Alert severity="error" onClose={actions.clearError} sx={{ mb: 2 }}>
          {actions.error}
        </Alert>
      )}

      {!loading && course && (
        <>
          <Typography variant="h5" component="h1" gutterBottom>
            {course.title}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
            <Chip label={course.category} size="small" variant="outlined" />
            <Chip
              label={statusLabel(course.status)}
              size="small"
              color={course.status === "OPEN" ? "primary" : "default"}
            />
          </Stack>
          <Typography sx={{ whiteSpace: "pre-line", mb: 4 }}>{course.description}</Typography>

          <Typography variant="h6" component="h2" gutterBottom>
            회차
          </Typography>
          <Paper variant="outlined">
            <TableContainer>
              <Table aria-label="회차 목록">
                <TableHead>
                  <TableRow>
                    <TableCell>일정</TableCell>
                    <TableCell>장소</TableCell>
                    <TableCell align="right">정원</TableCell>
                    <TableCell align="right">남은 자리</TableCell>
                    <TableCell>상태</TableCell>
                    <TableCell>내 신청</TableCell>
                    <TableCell>작업</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {sessions.map((session) => (
                    <TableRow key={session.id}>
                      <TableCell>{formatDateTime(session.startAt)}</TableCell>
                      <TableCell>{session.place}</TableCell>
                      <TableCell align="right">{session.capacity}</TableCell>
                      <TableCell align="right">{session.remainingSeats}</TableCell>
                      <TableCell>{statusLabel(session.status)}</TableCell>
                      {renderEnrollmentCells(session)}
                    </TableRow>
                  ))}
                  {sessions.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7} align="center">
                        등록된 회차가 없습니다.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}

      <ConfirmDialog
        open={cancelTarget !== null}
        title="신청 취소"
        message={
          cancelTarget?.enrollment.status === "CONFIRMED"
            ? "확정자가 취소하면 대기 1순위가 확정됩니다. 신청을 취소할까요?"
            : "대기 신청을 취소할까요?"
        }
        confirmLabel="신청 취소"
        submitting={actions.submitting}
        error={actions.error}
        onConfirm={() => void handleCancel()}
        onClose={closeCancel}
      />
    </Container>
  );
}
