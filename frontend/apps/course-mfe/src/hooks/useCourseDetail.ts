import type { CourseDetailResponse } from "@eduhub/api-client";
import { useCallback, useEffect, useState } from "react";
import { courseApi } from "../api/courseApi";

export interface UseCourseDetailResult {
  data: CourseDetailResponse | null;
  loading: boolean;
  error: Error | null;
  reload: () => void;
}

/** 과정 상세와 회차 목록을 불러온다. courseId가 null이면 아무것도 하지 않는다. */
export function useCourseDetail(courseId: number | null): UseCourseDetailResult {
  const [data, setData] = useState<CourseDetailResponse | null>(null);
  const [loading, setLoading] = useState(courseId !== null);
  const [error, setError] = useState<Error | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    setData(null);
    setError(null);
    if (courseId === null) {
      setLoading(false);
      return;
    }
    let ignore = false;
    setLoading(true);
    courseApi
      .getCourse(courseId)
      .then((response) => {
        if (!ignore) setData(response.data);
      })
      .catch((cause: unknown) => {
        if (!ignore) setError(cause instanceof Error ? cause : new Error(String(cause)));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [courseId, reloadKey]);

  return { data, loading, error, reload };
}
