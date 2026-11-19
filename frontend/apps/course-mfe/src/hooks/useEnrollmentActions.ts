import type { EnrollmentResponse } from "@eduhub/api-client";
import { useCallback, useMemo, useState } from "react";
import { enrollmentApi } from "../api/courseApi";
import { toErrorMessage } from "./useCourseAdmin";

export interface UseEnrollmentActionsResult {
  submitting: boolean;
  /** 마지막 요청의 에러 메시지. 성공하거나 clearError를 부르면 null이 된다. */
  error: string | null;
  clearError: () => void;
  /** 성공하면 신청 결과를, 실패하면 null을 돌려준다. */
  enroll: (sessionId: number) => Promise<EnrollmentResponse | null>;
  cancel: (sessionId: number) => Promise<EnrollmentResponse | null>;
}

/** 회차 신청과 취소(FR-04~06). */
export function useEnrollmentActions(): UseEnrollmentActionsResult {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const clearError = useCallback(() => setError(null), []);

  const run = useCallback(
    async (call: () => Promise<{ data: EnrollmentResponse }>): Promise<EnrollmentResponse | null> => {
      setSubmitting(true);
      setError(null);
      try {
        return (await call()).data;
      } catch (cause) {
        setError(toErrorMessage(cause));
        return null;
      } finally {
        setSubmitting(false);
      }
    },
    [],
  );

  const actions = useMemo(
    () => ({
      enroll: (sessionId: number) => run(() => enrollmentApi.enroll(sessionId)),
      cancel: (sessionId: number) => run(() => enrollmentApi.cancel(sessionId)),
    }),
    [run],
  );

  return { submitting, error, clearError, ...actions };
}
