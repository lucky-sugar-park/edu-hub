import {
  type CourseDetailResponse,
  type EnrollmentResponse,
  loadAuth,
  type MyEnrollmentResponse,
  type SessionResponse,
} from "@eduhub/api-client";
import { Alert, Button, Chip, Snackbar, Stack, Tooltip } from "@mui/material";
import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { useEnrollmentActions } from "../hooks/useEnrollmentActions";
import { useMyEnrollments } from "../hooks/useMyEnrollments";
import { ConfirmDialog } from "./ConfirmDialog";

/** shell의 src/remotes.d.ts("enrollmentMfe/EnrollButton")와 맞춘다. */
export interface EnrollButtonProps {
  course: CourseDetailResponse;
  session: SessionResponse;
  /** 신청·취소 뒤에 부른다(과정 상세 다시 불러오기). 실패해도 서버 상태와 맞추려고 부른다. */
  onChanged?: () => void;
}

function hasStarted(session: SessionResponse, now: number): boolean {
  return session.startAt !== undefined && Date.parse(session.startAt) <= now;
}

/** 신청 불가 사유. 신청할 수 있으면 null. 최종 판단은 백엔드(409)다. */
function applyBlockedReason(course: CourseDetailResponse, session: SessionResponse, now: number) {
  if (course.status === "CLOSED") return "종료된 과정입니다";
  if (session.status === "CLOSED") return "종료된 회차입니다";
  if (hasStarted(session, now)) return "이미 시작된 회차입니다";
  return null;
}

/** 내 활성(확정·대기) 신청 중 이 회차의 것. */
function activeEnrollment(
  enrollments: MyEnrollmentResponse[] | undefined,
  sessionId: number | undefined,
): MyEnrollmentResponse | undefined {
  if (sessionId === undefined) return undefined;
  return (enrollments ?? []).find(
    (e) => e.sessionId === sessionId && (e.status === "CONFIRMED" || e.status === "WAITING"),
  );
}

/**
 * 회차 하나의 신청·취소 버튼(FR-04~06). 과정 상세의 회차 행마다 shell이 끼워 넣는다.
 * 로그인 상태는 shell과 공유하는 저장소(@eduhub/api-client의 loadAuth)에서 읽는다.
 */
export default function EnrollButton({ course, session, onChanged }: EnrollButtonProps) {
  const loggedIn = (loadAuth()?.token ?? null) !== null;
  const myEnrollments = useMyEnrollments(undefined, 0, 100);
  const actions = useEnrollmentActions();
  const [notice, setNotice] = useState<string | null>(null);
  const [cancelOpen, setCancelOpen] = useState(false);

  if (session.id === undefined) return <></>;
  const sessionId = session.id;

  if (!loggedIn) {
    return (
      <Button
        size="small"
        component={RouterLink}
        to="/login"
        state={{ from: `/courses/${course.id}` }}
      >
        로그인 후 신청
      </Button>
    );
  }

  const now = Date.now();
  // 내 신청을 받아 오기 전, 다시 받아 오는 중, 받아 오기 실패한 동안은 신청·취소를 막는다(틀린 상태 방지).
  const unavailableReason = myEnrollments.error
    ? "내 신청 내역을 불러오지 못했습니다"
    : myEnrollments.loading || myEnrollments.data === null
      ? "내 신청 내역을 확인하는 중입니다"
      : null;
  const enrollment = activeEnrollment(myEnrollments.data?.content, sessionId);

  function afterChange() {
    onChanged?.();
    myEnrollments.reload();
  }

  async function enroll() {
    setNotice(null);
    const result: EnrollmentResponse | null = await actions.enroll(sessionId);
    // 실패해도 서버 상태와 맞추려고 다시 불러온다(예: 이미 신청된 경우의 409).
    if (result) {
      setNotice(
        result.status === "CONFIRMED"
          ? "신청이 확정되었습니다"
          : `대기 ${result.waitingNumber}번으로 신청되었습니다`,
      );
    }
    afterChange();
  }

  async function confirmCancel() {
    const result = await actions.cancel(sessionId);
    if (result) {
      setCancelOpen(false);
      setNotice("신청이 취소되었습니다");
    }
    afterChange();
  }

  function openCancel() {
    actions.clearError();
    setNotice(null);
    setCancelOpen(true);
  }

  function closeCancel() {
    actions.clearError();
    setCancelOpen(false);
  }

  let control;
  if (enrollment) {
    const cancelReason = hasStarted(session, now)
      ? "이미 시작된 회차는 취소할 수 없습니다"
      : unavailableReason;
    const label = enrollment.status === "WAITING" ? `대기 ${enrollment.waitingNumber}번` : "확정";
    control = (
      <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
        <Chip
          label={label}
          size="small"
          color={enrollment.status === "WAITING" ? "warning" : "success"}
        />
        <Tooltip title={cancelReason ?? ""}>
          <span>
            <Button
              size="small"
              color="error"
              aria-label={cancelReason ? `취소 (${cancelReason})` : undefined}
              disabled={cancelReason !== null || actions.submitting}
              onClick={openCancel}
            >
              취소
            </Button>
          </span>
        </Tooltip>
      </Stack>
    );
  } else {
    const reason = applyBlockedReason(course, session, now) ?? unavailableReason;
    control = (
      <Tooltip title={reason ?? ""}>
        <span>
          <Button
            size="small"
            variant="outlined"
            aria-label={reason ? `신청 (${reason})` : undefined}
            disabled={reason !== null || actions.submitting}
            onClick={() => void enroll()}
          >
            신청
          </Button>
        </span>
      </Tooltip>
    );
  }

  // 대화 상자 밖에서 난 오류(신청 실패)만 여기서 알린다. 취소 실패는 대화 상자 안에 보인다.
  const outsideError = cancelOpen ? null : actions.error;

  return (
    <>
      {control}
      <Snackbar
        open={notice !== null || outsideError !== null}
        autoHideDuration={6000}
        onClose={() => {
          setNotice(null);
          actions.clearError();
        }}
      >
        <Alert
          severity={outsideError ? "error" : "success"}
          onClose={() => {
            setNotice(null);
            actions.clearError();
          }}
        >
          {outsideError ?? notice}
        </Alert>
      </Snackbar>
      <ConfirmDialog
        open={cancelOpen}
        title="신청 취소"
        message={
          enrollment?.status === "CONFIRMED"
            ? "확정자가 취소하면 대기 1순위가 확정됩니다. 신청을 취소할까요?"
            : "대기 신청을 취소할까요?"
        }
        confirmLabel="신청 취소"
        submitting={actions.submitting}
        error={actions.error}
        onConfirm={() => void confirmCancel()}
        onClose={closeCancel}
      />
    </>
  );
}
