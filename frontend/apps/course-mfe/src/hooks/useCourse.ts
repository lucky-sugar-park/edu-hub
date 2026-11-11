import { type CourseDetailResponse, isAxiosError } from "@eduhub/api-client";
import { useEffect, useState } from "react";
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
}

/** 과정 상세와 회차 목록을 불러온다. `id`가 양의 정수가 아니면 요청하지 않고 CourseNotFoundError를 돌려준다. */
export function useCourse(id: number): UseCourseResult {
  const [data, setData] = useState<CourseDetailResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
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
    };
  }, [id]);

  return { data, loading, error };
}
