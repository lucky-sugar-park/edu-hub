import { type CourseDetailResponse, isAxiosError } from "@eduhub/api-client";
import { useCallback, useEffect, useRef, useState } from "react";
import { courseApi } from "../api/courseApi";

/** 과정이 없을 때(404, 또는 숫자가 아닌 id) 돌려주는 에러다. 화면은 이것으로 일반 실패와 구분한다. */
export class CourseNotFoundError extends Error {
  constructor() {
    super("과정을 찾을 수 없습니다.");
    this.name = "CourseNotFoundError";
  }
}

export interface UseCourseResult {
  data: CourseDetailResponse | null;
  loading: boolean;
  error: Error | null;
  /** 화면을 그대로 둔 채 다시 불러온다(신청·취소 뒤 남은 자리 갱신). 실패하면 기존 값을 유지한다. */
  reload: () => void;
}

/** 과정 상세와 회차 목록을 불러온다. `id`가 양의 정수가 아니면 요청하지 않고 CourseNotFoundError를 돌려준다. */
export function useCourse(id: number): UseCourseResult {
  const [data, setData] = useState<CourseDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  // id가 바뀌거나 언마운트되면 올라간다. reload 응답이 그 사이에 도착하면 버린다.
  const epoch = useRef(0);

  useEffect(() => {
    epoch.current += 1;
    setData(null);
    if (!Number.isSafeInteger(id) || id <= 0) {
      setError(new CourseNotFoundError());
      setLoading(false);
      return;
    }

    // id가 바뀐 뒤 늦게 도착한 이전 응답은 무시한다.
    let ignore = false;
    setLoading(true);
    setError(null);
    courseApi
      .getCourse(id)
      .then((response) => {
        if (!ignore) setData(response.data);
      })
      .catch((cause: unknown) => {
        if (ignore) return;
        if (isAxiosError(cause) && cause.response?.status === 404) {
          setError(new CourseNotFoundError());
        } else {
          setError(cause instanceof Error ? cause : new Error(String(cause)));
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
      epoch.current += 1;
    };
  }, [id]);

  const reload = useCallback(() => {
    if (!Number.isSafeInteger(id) || id <= 0) return;
    // 호출마다 번호를 올려, 마지막 요청의 응답만 반영한다(응답 순서가 뒤집혀도 안전).
    epoch.current += 1;
    const mine = epoch.current;
    courseApi
      .getCourse(id)
      .then((response) => {
        if (epoch.current !== mine) return;
        setData(response.data);
        setError(null);
      })
      .catch(() => {
        // 다시 불러오기 실패는 기존 화면을 유지한다.
      });
  }, [id]);

  return { data, loading, error, reload };
}
