import type { GetMyEnrollmentsStatusEnum, PageResponseMyEnrollmentResponse } from "@eduhub/api-client";
import { useCallback, useEffect, useRef, useState } from "react";
import { enrollmentApi } from "../api/courseApi";
import { useAuth } from "../auth/AuthContext";

export interface UseMyEnrollmentsResult {
  data: PageResponseMyEnrollmentResponse | null;
  loading: boolean;
  error: Error | null;
  /** 같은 조건으로 다시 불러온다. */
  reload: () => void;
}

/** 내 신청 내역(FR-07). 비로그인이면 호출하지 않고 빈 결과를 돌려준다. page는 0부터다. */
export function useMyEnrollments(
  status?: GetMyEnrollmentsStatusEnum,
  page = 0,
  size = 20,
): UseMyEnrollmentsResult {
  const { token } = useAuth();
  const loggedIn = token !== null;
  const [data, setData] = useState<PageResponseMyEnrollmentResponse | null>(null);
  const [loading, setLoading] = useState(loggedIn);
  const [error, setError] = useState<Error | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  const lastToken = useRef(token);

  useEffect(() => {
    // 로그인 사용자가 바뀌면(로그아웃, A에서 B로 바로 전환) 이전 사용자의 결과를 비운다.
    if (lastToken.current !== token) {
      lastToken.current = token;
      setData(null);
      setError(null);
    }
    if (!loggedIn) {
      setData(null);
      setError(null);
      setLoading(false);
      return;
    }
    // 조건이 바뀐 뒤 늦게 도착한 이전 응답은 무시한다.
    let ignore = false;
    setLoading(true);
    enrollmentApi
      .getMyEnrollments(status, page, size)
      .then((response) => {
        if (ignore) return;
        setData(response.data);
        setError(null);
      })
      .catch((cause: unknown) => {
        // 다시 불러오기가 실패해도 기존 data는 유지하고 error만 알린다.
        if (ignore) return;
        setError(cause instanceof Error ? cause : new Error(String(cause)));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [token, loggedIn, status, page, size, reloadKey]);

  if (!loggedIn) {
    return { data: { content: [], page, size, totalElements: 0 }, loading: false, error: null, reload };
  }
  return { data, loading, error, reload };
}
