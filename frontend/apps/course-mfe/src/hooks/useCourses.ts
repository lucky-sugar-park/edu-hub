import type { PageResponseCourseSummaryResponse } from "@eduhub/api-client";
import { useEffect, useState } from "react";
import { courseApi } from "../api/courseApi";

export interface UseCoursesParams {
  keyword: string;
  category: string;
  /** 0부터 시작한다. 백엔드 Pageable과 MUI TablePagination이 모두 0부터다. */
  page: number;
  size: number;
}

export interface UseCoursesResult {
  data: PageResponseCourseSummaryResponse | null;
  loading: boolean;
  error: Error | null;
}

export function useCourses({ keyword, category, page, size }: UseCoursesParams): UseCoursesResult {
  const [data, setData] = useState<PageResponseCourseSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    // 조건이 바뀐 뒤 늦게 도착한 이전 응답은 무시한다.
    let ignore = false;
    setLoading(true);
    setError(null);
    courseApi
      .searchCourses(keyword || undefined, category || undefined, page, size)
      .then((response) => {
        if (!ignore) setData(response.data);
      })
      .catch((cause: unknown) => {
        if (ignore) return;
        setData(null);
        setError(cause instanceof Error ? cause : new Error(String(cause)));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [keyword, category, page, size]);

  return { data, loading, error };
}
