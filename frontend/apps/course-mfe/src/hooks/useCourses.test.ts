import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { courseApi } from "../api/courseApi";
import { useCourses } from "./useCourses";

vi.mock("../api/courseApi", () => ({
  courseApi: { searchCourses: vi.fn() },
}));

const searchCourses = vi.mocked(courseApi.searchCourses);

beforeEach(() => {
  searchCourses.mockReset();
});

it("목록을 불러온다", async () => {
  const page = {
    content: [{ id: 1, title: "JPA 입문", category: "개발", status: "OPEN" as const }],
    page: 2,
    size: 10,
    totalElements: 21,
  };
  searchCourses.mockResolvedValue({ data: page } as Awaited<ReturnType<typeof searchCourses>>);

  const { result } = renderHook(() =>
    useCourses({ keyword: "JPA", category: "개발", page: 2, size: 10 }),
  );

  expect(result.current.loading).toBe(true);
  await waitFor(() => expect(result.current.loading).toBe(false));

  expect(result.current.data).toEqual(page);
  expect(result.current.error).toBeNull();
  expect(searchCourses).toHaveBeenCalledWith("JPA", "개발", 2, 10);
});

it("실패하면 error를 돌려준다", async () => {
  const failure = new Error("서버 오류");
  searchCourses.mockRejectedValue(failure);

  const { result } = renderHook(() => useCourses({ keyword: "", category: "", page: 0, size: 20 }));

  await waitFor(() => expect(result.current.loading).toBe(false));

  expect(result.current.error).toBe(failure);
  expect(result.current.data).toBeNull();
  expect(searchCourses).toHaveBeenCalledWith(undefined, undefined, 0, 20);
});
