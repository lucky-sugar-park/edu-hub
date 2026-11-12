import { act, renderHook } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { courseAdminApi } from "../api/courseApi";
import { toErrorMessage, useCourseAdmin } from "./useCourseAdmin";

vi.mock("../api/courseApi", () => ({
  courseAdminApi: {
    createCourse: vi.fn(),
    updateCourse: vi.fn(),
    closeCourse: vi.fn(),
    createSession: vi.fn(),
    updateSession: vi.fn(),
    closeSession: vi.fn(),
  },
}));

const api = vi.mocked(courseAdminApi);
type Response<T extends (...args: never[]) => unknown> = Awaited<ReturnType<T>>;

beforeEach(() => {
  vi.resetAllMocks();
});

it("과정을 등록하고 응답 본문을 돌려준다", async () => {
  const created = { id: 1, title: "JPA 입문", category: "개발", status: "OPEN" as const };
  api.createCourse.mockResolvedValue({ data: created } as Response<typeof api.createCourse>);
  const { result } = renderHook(() => useCourseAdmin());

  let returned: unknown;
  await act(async () => {
    returned = await result.current.createCourse({ title: "JPA 입문", category: "개발" });
  });

  expect(returned).toEqual(created);
  expect(api.createCourse).toHaveBeenCalledWith({ title: "JPA 입문", category: "개발" });
  expect(result.current.error).toBeNull();
  expect(result.current.submitting).toBe(false);
});

it("실패하면 null을 돌려주고 서버 메시지를 error에 담는다", async () => {
  api.updateSession.mockRejectedValue({
    response: {
      data: { code: "SESSION_STARTED", message: "교육이 시작된 회차는 정원과 시작 시각을 바꿀 수 없습니다: 3" },
    },
  });
  const { result } = renderHook(() => useCourseAdmin());

  let returned: unknown;
  await act(async () => {
    returned = await result.current.updateSession(3, {
      startAt: "2026-12-01T01:00:00Z",
      place: "본사",
      capacity: 30,
    });
  });

  expect(returned).toBeNull();
  expect(result.current.error).toBe("교육이 시작된 회차는 정원과 시작 시각을 바꿀 수 없습니다: 3");

  act(() => result.current.clearError());
  expect(result.current.error).toBeNull();
});

it("각 함수가 알맞은 API를 부른다", async () => {
  const ok = { data: {} };
  api.updateCourse.mockResolvedValue(ok as Response<typeof api.updateCourse>);
  api.closeCourse.mockResolvedValue(ok as Response<typeof api.closeCourse>);
  api.createSession.mockResolvedValue(ok as Response<typeof api.createSession>);
  api.closeSession.mockResolvedValue(ok as Response<typeof api.closeSession>);
  const { result } = renderHook(() => useCourseAdmin());
  const session = { startAt: "2026-12-01T01:00:00Z", place: "본사", capacity: 10 };

  await act(async () => {
    await result.current.updateCourse(1, { title: "t", category: "c" });
    await result.current.closeCourse(1);
    await result.current.createSession(1, session);
    await result.current.closeSession(5);
  });

  expect(api.updateCourse).toHaveBeenCalledWith(1, { title: "t", category: "c" });
  expect(api.closeCourse).toHaveBeenCalledWith(1);
  expect(api.createSession).toHaveBeenCalledWith(1, session);
  expect(api.closeSession).toHaveBeenCalledWith(5);
});

it("응답이 없는 오류는 기본 문구를 쓴다", () => {
  expect(toErrorMessage(new Error("Network Error"))).toBe("요청을 처리하지 못했습니다.");
  expect(toErrorMessage(null)).toBe("요청을 처리하지 못했습니다.");
});
