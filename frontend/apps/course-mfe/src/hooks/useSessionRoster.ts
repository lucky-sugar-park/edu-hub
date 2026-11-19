import { isAxiosError, type SessionRosterResponse } from "@eduhub/api-client";
import { useCallback, useEffect, useRef, useState } from "react";
import { enrollmentAdminApi } from "../api/courseApi";

/** 회차가 없을 때(404, 또는 숫자가 아닌 id) 돌려주는 에러다. 화면은 이것으로 일반 실패와 구분한다. */
export class SessionNotFoundError extends Error {
  constructor() {
    super("회차를 찾을 수 없습니다.");
    this.name = "SessionNotFoundError";
  }
}

/** 관리자 권한이 없을 때(403, 예: 토큰의 역할이 바뀐 경우) 돌려주는 에러다. 다시 시도해도 소용없다. */
export class SessionForbiddenError extends Error {
  constructor() {
    super("권한이 없습니다.");
    this.name = "SessionForbiddenError";
  }
}

export interface UseSessionRosterResult {
  data: SessionRosterResponse | null;
  loading: boolean;
  error: Error | null;
  /** 같은 회차를 다시 불러온다. 실패해도 기존 data는 유지하고 error만 알린다. */
  reload: () => void;
}

const isValidId = (id: number) => Number.isSafeInteger(id) && id > 0;

/** 관리자의 회차 명단(FR-08). `sessionId`가 양의 정수가 아니면 요청 없이 SessionNotFoundError를 돌려준다. */
export function useSessionRoster(sessionId: number): UseSessionRosterResult {
  const [data, setData] = useState<SessionRosterResponse | null>(null);
  const [loading, setLoading] = useState(isValidId(sessionId));
  const [error, setError] = useState<Error | null>(
    isValidId(sessionId) ? null : new SessionNotFoundError(),
  );
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);
  const lastId = useRef(sessionId);

  useEffect(() => {
    // 회차가 바뀌면 이전 회차의 명단을 비운다. reload는 비우지 않는다.
    if (lastId.current !== sessionId) {
      lastId.current = sessionId;
      setData(null);
      setError(null);
    }
    if (!isValidId(sessionId)) {
      setData(null);
      setError(new SessionNotFoundError());
      setLoading(false);
      return;
    }

    // 회차가 바뀐 뒤 늦게 도착한 이전 응답은 무시한다.
    let ignore = false;
    setLoading(true);
    enrollmentAdminApi
      .getRoster(sessionId)
      .then((response) => {
        if (ignore) return;
        setData(response.data);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (ignore) return;
        if (isAxiosError(cause) && cause.response?.status === 404) {
          setData(null);
          setError(new SessionNotFoundError());
        } else if (isAxiosError(cause) && cause.response?.status === 403) {
          setData(null);
          setError(new SessionForbiddenError());
        } else {
          setError(cause instanceof Error ? cause : new Error(String(cause)));
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [sessionId, reloadKey]);

  // 회차가 바뀐 직후 첫 렌더에는 이전 회차의 data가 남아 있으므로 "불러오는 중"으로 돌려준다.
  if (lastId.current !== sessionId && isValidId(sessionId)) {
    return { data: null, loading: true, error: null, reload };
  }
  return { data, loading, error, reload };
}
