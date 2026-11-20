import type {
  CourseDetailResponse,
  CourseRequest,
  SessionRequest,
  SessionResponse,
} from "@eduhub/api-client";
import { useCallback, useMemo, useState } from "react";
import { courseAdminApi } from "../api/courseApi";
import { toErrorMessage } from "../api/errors";

export interface UseCourseAdminResult {
  submitting: boolean;
  /** 마지막 요청의 에러 메시지. 성공하거나 clearError를 부르면 null이 된다. */
  error: string | null;
  clearError: () => void;
  // 각 함수는 성공하면 응답 본문을, 실패하면 null을 돌려준다.
  createCourse: (request: CourseRequest) => Promise<CourseDetailResponse | null>;
  updateCourse: (courseId: number, request: CourseRequest) => Promise<CourseDetailResponse | null>;
  closeCourse: (courseId: number) => Promise<CourseDetailResponse | null>;
  createSession: (courseId: number, request: SessionRequest) => Promise<SessionResponse | null>;
  updateSession: (sessionId: number, request: SessionRequest) => Promise<SessionResponse | null>;
  closeSession: (sessionId: number) => Promise<SessionResponse | null>;
}

/** 관리자의 과정·회차 등록, 수정, 종료(FR-03). */
export function useCourseAdmin(): UseCourseAdminResult {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const clearError = useCallback(() => setError(null), []);

  const run = useCallback(async <T>(call: () => Promise<{ data: T }>): Promise<T | null> => {
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
  }, []);

  const actions = useMemo(
    () => ({
      createCourse: (request: CourseRequest) => run(() => courseAdminApi.createCourse(request)),
      updateCourse: (courseId: number, request: CourseRequest) =>
        run(() => courseAdminApi.updateCourse(courseId, request)),
      closeCourse: (courseId: number) => run(() => courseAdminApi.closeCourse(courseId)),
      createSession: (courseId: number, request: SessionRequest) =>
        run(() => courseAdminApi.createSession(courseId, request)),
      updateSession: (sessionId: number, request: SessionRequest) =>
        run(() => courseAdminApi.updateSession(sessionId, request)),
      closeSession: (sessionId: number) => run(() => courseAdminApi.closeSession(sessionId)),
    }),
    [run],
  );

  return { submitting, error, clearError, ...actions };
}
