import {
  enrollmentApi,
  type GetMyEnrollmentsStatusEnum,
  loadAuth,
  type PageResponseMyEnrollmentResponse,
} from "@eduhub/api-client";
import { useCallback, useEffect, useRef, useState } from "react";

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
  // shell의 AuthProvider는 remote와 공유하지 않는다. 로그인 상태는 공유 저장소(sessionStorage)에서 읽는다.
  const token = loadAuth()?.token ?? null;
  const loggedIn = token !== null;
  const [data, setData] = useState<PageResponseMyEnrollmentResponse | null>(null);
  const [loading, setLoading] = useState(loggedIn);
  const [error, setError] = useState<Error | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  const lastToken = useRef(token);
  const lastQuery = useRef(`${status ?? ""}|${page}|${size}`);

  useEffect(() => {
    // 로그인 사용자가 바뀌면(로그아웃, A에서 B로 바로 전환) 이전 사용자의 결과를 비운다.
    // 조회 조건(status, page, size)이 바뀌어도 이전 조건의 행이 남지 않게 비운다. reload는 조건이 그대로라 비우지 않는다.
    const query = `${status ?? ""}|${page}|${size}`;
    if (lastToken.current !== token || lastQuery.current !== query) {
      lastToken.current = token;
      lastQuery.current = query;
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

  // 조건이나 토큰이 바뀐 직후 첫 렌더에는 아직 이전 조건의 data가 상태에 남아 있다(비우기는 효과에서 한다).
  // 화면이 낡은 값으로 판단하지 않게 이 렌더에서는 "불러오는 중"으로 돌려준다.
  const stale =
    lastToken.current !== token || lastQuery.current !== `${status ?? ""}|${page}|${size}`;

  if (!loggedIn) {
    return {
      data: { content: [], page, size, totalElements: 0 },
      loading: false,
      error: null,
      reload,
    };
  }
  if (stale) return { data: null, loading: true, error: null, reload };
  return { data, loading, error, reload };
}
