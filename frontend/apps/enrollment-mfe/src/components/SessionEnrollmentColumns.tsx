import type { CourseDetailResponse, SessionResponse } from "@eduhub/api-client";
import { Alert, Button, Chip, TableCell, Tooltip } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { ConfirmDialog } from "../../../course-mfe/src/components/ConfirmDialog";
import type { SessionEnrollments } from "../hooks/useSessionEnrollments";

/** 회차 표 머리의 "내 신청", "작업" 열. */
export function SessionEnrollmentHeadCells() {
  return (
    <>
      <TableCell>내 신청</TableCell>
      <TableCell>작업</TableCell>
    </>
  );
}

/** 회차 한 줄의 "내 신청", "작업" 셀. 신청·취소 버튼과 비활성 사유를 보여 준다. */
export function SessionEnrollmentCells({
  enrollments,
  course,
  session,
}: {
  enrollments: SessionEnrollments;
  course: CourseDetailResponse;
  session: SessionResponse;
}) {
  if (session.id === undefined) return null;
  const sessionId = session.id;
  const state = enrollments.stateFor(session);

  if (state.kind === "login") {
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

  if (state.kind === "enrolled") {
    const { enrollment, cancelReason } = state;
    const label = enrollment.status === "WAITING" ? `대기 ${enrollment.waitingNumber}번` : "확정";
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
                disabled={cancelReason !== null || enrollments.submitting}
                onClick={() => enrollments.openCancel({ session, enrollment })}
              >
                취소
              </Button>
            </span>
          </Tooltip>
        </TableCell>
      </>
    );
  }

  return (
    <>
      <TableCell>-</TableCell>
      <TableCell>
        <Tooltip title={state.reason ?? ""}>
          <span>
            <Button
              size="small"
              variant="outlined"
              aria-label={state.reason ? `신청 (${state.reason})` : undefined}
              disabled={state.reason !== null || enrollments.submitting}
              onClick={() => void enrollments.enroll(sessionId)}
            >
              신청
            </Button>
          </span>
        </Tooltip>
      </TableCell>
    </>
  );
}

/** 신청 결과 알림, 내 신청 조회 실패 알림, 신청 오류, 취소 확인 대화 상자. */
export function SessionEnrollmentNotices({ enrollments }: { enrollments: SessionEnrollments }) {
  const { cancelTarget } = enrollments;
  return (
    <>
      {enrollments.notice && (
        <Alert severity="success" onClose={enrollments.dismissNotice} sx={{ mb: 2 }}>
          {enrollments.notice}
        </Alert>
      )}
      {enrollments.loggedIn && enrollments.myEnrollmentsFailed && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={enrollments.reloadMyEnrollments}>
              다시 시도
            </Button>
          }
        >
          내 신청 내역을 불러오지 못했습니다
        </Alert>
      )}
      {enrollments.actionError && !cancelTarget && (
        <Alert severity="error" onClose={enrollments.clearActionError} sx={{ mb: 2 }}>
          {enrollments.actionError}
        </Alert>
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
        submitting={enrollments.submitting}
        error={enrollments.actionError}
        onConfirm={() => void enrollments.confirmCancel()}
        onClose={enrollments.closeCancel}
      />
    </>
  );
}
