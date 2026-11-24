import type {
  CourseDetailResponse,
  EnrollmentResponse,
  MyEnrollmentResponse,
  SessionResponse,
} from "@eduhub/api-client";
import { useState } from "react";
import { useAuth } from "../../../shell/src/auth/AuthContext";
import { useEnrollmentActions } from "./useEnrollmentActions";
import { useMyEnrollments } from "./useMyEnrollments";

/** 회차 한 줄의 신청 상태. 화면(SessionEnrollmentCells)이 이것만 보고 그린다. */
export type SessionEnrollmentState =
  | { kind: "login" }
  | { kind: "enrolled"; enrollment: MyEnrollmentResponse; cancelReason: string | null }
  | { kind: "open"; reason: string | null };

export interface CancelTarget {
  session: SessionResponse;
  enrollment: MyEnrollmentResponse;
}

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

/**
 * 과정 상세의 회차별 신청·취소(FR-04~06). 내 신청 매칭, 신청·취소 동작, 알림, 다시 불러오기를 맡는다.
 * `onChanged`는 신청·취소 뒤(실패해도) 과정 정보를 다시 불러오는 데 쓴다. 이 훅 밖에서는 신청 API를 모른다.
 */
export function useSessionEnrollments(course: CourseDetailResponse | null, onChanged: () => void) {
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
    onChanged();
    myEnrollments.reload();
  }

  /** 회차 한 줄이 어떤 상태인지(로그인 필요, 신청 중, 신청 가능 여부와 사유)를 돌려준다. */
  function stateFor(session: SessionResponse): SessionEnrollmentState {
    if (!token) return { kind: "login" };
    const enrollment = session.id === undefined ? undefined : activeBySession.get(session.id);
    if (enrollment) {
      const cancelReason = hasStarted(session, now)
        ? "이미 시작된 회차는 취소할 수 없습니다"
        : enrollmentsUnavailable
          ? enrollmentsReason
          : null;
      return { kind: "enrolled", enrollment, cancelReason };
    }
    const reason = course
      ? (applyBlockedReason(course, session, now) ??
        (enrollmentsUnavailable ? enrollmentsReason : null))
      : null;
    return { kind: "open", reason };
  }

  async function enroll(sessionId: number) {
    setNotice(null);
    const result: EnrollmentResponse | null = await actions.enroll(sessionId);
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

  async function confirmCancel() {
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

  return {
    loggedIn: token !== null,
    submitting: actions.submitting,
    actionError: actions.error,
    clearActionError: actions.clearError,
    notice,
    dismissNotice: () => setNotice(null),
    myEnrollmentsFailed: myEnrollments.error !== null,
    reloadMyEnrollments: myEnrollments.reload,
    cancelTarget,
    stateFor,
    enroll,
    openCancel,
    closeCancel,
    confirmCancel,
  };
}

export type SessionEnrollments = ReturnType<typeof useSessionEnrollments>;
