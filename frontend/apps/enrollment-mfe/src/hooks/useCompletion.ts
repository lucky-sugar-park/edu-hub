import { adminEnrollmentApi, type CompletionResponse, toErrorMessage } from "@eduhub/api-client";
import { useCallback, useMemo, useState } from "react";

export interface UseCompletionResult {
  submitting: boolean;
  /** 마지막 요청의 에러 메시지. 성공하거나 clearError를 부르면 null이 된다. */
  error: string | null;
  clearError: () => void;
  /** 성공하면 응답 본문(수료·실패 목록)을, 요청 자체가 실패하면 null을 돌려준다. */
  complete: (sessionId: number, employeeIds: string[]) => Promise<CompletionResponse | null>;
}

/** 관리자의 수료 처리(FR-08). */
export function useCompletion(): UseCompletionResult {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const clearError = useCallback(() => setError(null), []);

  const complete = useCallback(async (sessionId: number, employeeIds: string[]) => {
    setSubmitting(true);
    setError(null);
    try {
      return (await adminEnrollmentApi.complete(sessionId, { employeeIds })).data;
    } catch (cause) {
      setError(toErrorMessage(cause));
      return null;
    } finally {
      setSubmitting(false);
    }
  }, []);

  return useMemo(
    () => ({ submitting, error, clearError, complete }),
    [submitting, error, clearError, complete],
  );
}
