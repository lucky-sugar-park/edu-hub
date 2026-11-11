import type { CourseDetailResponse } from "@eduhub/api-client";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { courseApi } from "../api/courseApi";
import { CourseNotFoundError, useCourse } from "./useCourse";

vi.mock("../api/courseApi", () => ({
  courseApi: { getCourse: vi.fn() },
}));

const getCourse = vi.mocked(courseApi.getCourse);
type GetCourseResponse = Awaited<ReturnType<typeof getCourse>>;

/** axios의 isAxiosError가 확인하는 모양만 갖춘 HTTP 실패. */
function httpError(status: number) {
  return Object.assign(new Error(`HTTP ${status}`), { isAxiosError: true, response: { status } });
}

function course(id: number): CourseDetailResponse {
  return { id, title: `과정 ${id}`, category: "개발", status: "OPEN", sessions: [] };
}

beforeEach(() => {
  getCourse.mockReset();
});

it("과정 상세를 불러온다", async () => {
  getCourse.mockResolvedValue({ data: course(7) } as GetCourseResponse);

  const { result } = renderHook(() => useCourse(7));

  expect(result.current.loading).toBe(true);
  await waitFor(() => expect(result.current.loading).toBe(false));

  expect(result.current.data).toEqual(course(7));
  expect(result.current.error).toBeNull();
  expect(getCourse).toHaveBeenCalledWith(7);
});

it("404면 CourseNotFoundError를 돌려준다", async () => {
  getCourse.mockRejectedValue(httpError(404));

  const { result } = renderHook(() => useCourse(999));

  await waitFor(() => expect(result.current.loading).toBe(false));

  expect(result.current.error).toBeInstanceOf(CourseNotFoundError);
  expect(result.current.data).toBeNull();
});

it("그 밖의 실패는 원래 에러를 돌려준다", async () => {
  const failure = httpError(500);
  getCourse.mockRejectedValue(failure);

  const { result } = renderHook(() => useCourse(1));

  await waitFor(() => expect(result.current.loading).toBe(false));

  expect(result.current.error).toBe(failure);
  expect(result.current.error).not.toBeInstanceOf(CourseNotFoundError);
});

it.each([Number.NaN, 0, -1, 1.5])("id가 %s이면 요청하지 않고 CourseNotFoundError를 돌려준다", (id) => {
  const { result } = renderHook(() => useCourse(id));

  expect(result.current.loading).toBe(false);
  expect(result.current.error).toBeInstanceOf(CourseNotFoundError);
  expect(getCourse).not.toHaveBeenCalled();
});

it("id가 바뀐 뒤 늦게 도착한 이전 응답은 무시한다", async () => {
  let resolveFirst: (value: GetCourseResponse) => void = () => {};
  getCourse
    .mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)))
    .mockResolvedValueOnce({ data: course(2) } as GetCourseResponse);

  const { result, rerender } = renderHook(({ id }) => useCourse(id), { initialProps: { id: 1 } });
  rerender({ id: 2 });
  await waitFor(() => expect(result.current.data).toEqual(course(2)));

  await act(async () => resolveFirst({ data: course(1) } as GetCourseResponse));

  expect(result.current.data).toEqual(course(2));
});
