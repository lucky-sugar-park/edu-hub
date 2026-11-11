import type { CourseDetailResponse } from "@eduhub/api-client";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { courseApi } from "../api/courseApi";
import { useCourseDetail } from "./useCourseDetail";

vi.mock("../api/courseApi", () => ({
  courseApi: { getCourse: vi.fn() },
}));

const getCourse = vi.mocked(courseApi.getCourse);
const detail: CourseDetailResponse = {
  id: 1,
  title: "JPA 입문",
  category: "개발",
  status: "OPEN",
  sessions: [],
};

beforeEach(() => {
  getCourse.mockReset();
});

it("과정 상세를 불러오고 reload로 다시 불러온다", async () => {
  getCourse.mockResolvedValue({ data: detail } as Awaited<ReturnType<typeof getCourse>>);

  const { result } = renderHook(() => useCourseDetail(1));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.data).toEqual(detail);
  expect(getCourse).toHaveBeenCalledWith(1);

  act(() => result.current.reload());
  await waitFor(() => expect(getCourse).toHaveBeenCalledTimes(2));
});

it("courseId가 null이면 부르지 않는다", () => {
  const { result } = renderHook(() => useCourseDetail(null));

  expect(result.current.loading).toBe(false);
  expect(result.current.data).toBeNull();
  expect(getCourse).not.toHaveBeenCalled();
});

it("실패하면 error를 돌려준다", async () => {
  const failure = new Error("404");
  getCourse.mockRejectedValue(failure);

  const { result } = renderHook(() => useCourseDetail(9));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).toBe(failure);
});
