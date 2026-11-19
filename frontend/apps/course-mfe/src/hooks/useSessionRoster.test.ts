import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { enrollmentAdminApi } from "../api/courseApi";
import { SessionForbiddenError, SessionNotFoundError, useSessionRoster } from "./useSessionRoster";

vi.mock("../api/courseApi", () => ({
  enrollmentAdminApi: { getRoster: vi.fn() },
}));

const getRoster = vi.mocked(enrollmentAdminApi.getRoster);
type Response = Awaited<ReturnType<typeof getRoster>>;

function roster(sessionId: number, title = `과정 ${sessionId}`) {
  return { sessionId, courseTitle: title, confirmed: [], waiting: [] };
}

function httpError(status: number) {
  return Object.assign(new Error(`HTTP ${status}`), { isAxiosError: true, response: { status } });
}

beforeEach(() => {
  getRoster.mockReset();
});

it("명단을 불러온다", async () => {
  getRoster.mockResolvedValue({ data: roster(7) } as unknown as Response);

  const { result } = renderHook(() => useSessionRoster(7));

  expect(result.current.loading).toBe(true);
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.data).toEqual(roster(7));
  expect(result.current.error).toBeNull();
  expect(getRoster).toHaveBeenCalledWith(7);
});

it("404는 SessionNotFoundError로 바꾼다", async () => {
  getRoster.mockRejectedValue(httpError(404));

  const { result } = renderHook(() => useSessionRoster(7));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).toBeInstanceOf(SessionNotFoundError);
  expect(result.current.data).toBeNull();
});

it("숫자가 아닌 id는 요청 없이 SessionNotFoundError다", async () => {
  const { result } = renderHook(() => useSessionRoster(Number.NaN));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).toBeInstanceOf(SessionNotFoundError);
  expect(getRoster).not.toHaveBeenCalled();
});

it("그 밖의 실패는 error를 돌려준다", async () => {
  getRoster.mockRejectedValue(httpError(500));

  const { result } = renderHook(() => useSessionRoster(7));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).not.toBeNull();
  expect(result.current.error).not.toBeInstanceOf(SessionNotFoundError);
});

it("reload가 실패해도 기존 data를 유지하고 error를 알린다", async () => {
  getRoster.mockResolvedValue({ data: roster(7) } as unknown as Response);
  const { result } = renderHook(() => useSessionRoster(7));
  await waitFor(() => expect(result.current.data).toEqual(roster(7)));

  getRoster.mockRejectedValue(httpError(503));
  act(() => result.current.reload());

  await waitFor(() => expect(result.current.error).not.toBeNull());
  expect(result.current.data).toEqual(roster(7));

  getRoster.mockResolvedValue({ data: roster(7, "새 제목") } as unknown as Response);
  act(() => result.current.reload());
  await waitFor(() => expect(result.current.error).toBeNull());
  expect(result.current.data?.courseTitle).toBe("새 제목");
});

it("sessionId가 바뀌면 첫 렌더부터 이전 data를 내보내지 않고 낡은 응답은 무시한다", async () => {
  let resolveOld: (value: Response) => void = () => {};
  getRoster.mockReturnValueOnce(
    new Promise((r) => (resolveOld = r)) as ReturnType<typeof getRoster>,
  );
  const renders: ReturnType<typeof useSessionRoster>[] = [];
  const { result, rerender } = renderHook(
    ({ id }) => {
      const r = useSessionRoster(id);
      renders.push(r);
      return r;
    },
    { initialProps: { id: 7 } },
  );

  getRoster.mockResolvedValue({ data: roster(8) } as unknown as Response);
  const before = renders.length;
  rerender({ id: 8 });
  expect(renders[before].data).toBeNull();
  expect(renders[before].loading).toBe(true);

  await waitFor(() => expect(result.current.data?.sessionId).toBe(8));
  await act(async () => {
    resolveOld({ data: roster(7) } as unknown as Response);
  });
  expect(result.current.data?.sessionId).toBe(8);
});

it("403은 SessionForbiddenError로 바꾼다", async () => {
  getRoster.mockRejectedValue(httpError(403));

  const { result } = renderHook(() => useSessionRoster(7));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).toBeInstanceOf(SessionForbiddenError);
  expect(result.current.data).toBeNull();
});
