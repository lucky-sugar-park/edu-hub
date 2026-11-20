import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { enrollmentApi } from "../api/clients";
import { useAuth } from "../auth/AuthContext";
import { useMyEnrollments } from "./useMyEnrollments";

vi.mock("../api/clients", () => ({
  enrollmentApi: { getMyEnrollments: vi.fn() },
}));
vi.mock("../auth/AuthContext", () => ({ useAuth: vi.fn() }));

const api = vi.mocked(enrollmentApi);
const mockedUseAuth = vi.mocked(useAuth);
type Response<T extends (...args: never[]) => unknown> = Awaited<ReturnType<T>>;

function loggedIn(token: string | null) {
  mockedUseAuth.mockReturnValue({ token } as ReturnType<typeof useAuth>);
}

const page = {
  content: [{ id: 1, sessionId: 10, status: "CONFIRMED" as const }],
  page: 0,
  size: 100,
  totalElements: 1,
};

beforeEach(() => {
  vi.resetAllMocks();
  loggedIn("t");
});

it("조건을 넘겨 불러오고 결과를 돌려준다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);

  const { result } = renderHook(() => useMyEnrollments(undefined, 0, 100));

  expect(result.current.loading).toBe(true);
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.data).toEqual(page);
  expect(api.getMyEnrollments).toHaveBeenCalledWith(undefined, 0, 100);
});

it("실패하면 error를 돌려준다", async () => {
  api.getMyEnrollments.mockRejectedValue(new Error("boom"));

  const { result } = renderHook(() => useMyEnrollments());

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error?.message).toBe("boom");
  expect(result.current.data).toBeNull();
});

it("비로그인이면 호출하지 않고 빈 결과를 돌려준다", () => {
  loggedIn(null);

  const { result } = renderHook(() => useMyEnrollments(undefined, 0, 100));

  expect(api.getMyEnrollments).not.toHaveBeenCalled();
  expect(result.current.loading).toBe(false);
  expect(result.current.data?.content).toEqual([]);
});

it("reload하면 같은 조건으로 다시 부른다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  const { result } = renderHook(() => useMyEnrollments("WAITING", 1, 20));
  await waitFor(() => expect(result.current.loading).toBe(false));

  act(() => result.current.reload());

  await waitFor(() => expect(api.getMyEnrollments).toHaveBeenCalledTimes(2));
  expect(api.getMyEnrollments).toHaveBeenLastCalledWith("WAITING", 1, 20);
});

it("로그아웃하면 data를 비우고, 다시 로그인하면 조회한다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  const { result, rerender } = renderHook(() => useMyEnrollments());
  await waitFor(() => expect(result.current.data).toEqual(page));

  loggedIn(null);
  rerender();
  expect(result.current.data?.content).toEqual([]);
  expect(api.getMyEnrollments).toHaveBeenCalledTimes(1);

  loggedIn("t2");
  rerender();
  await waitFor(() => expect(api.getMyEnrollments).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(result.current.data).toEqual(page));
});

it("reload가 실패해도 data를 유지하고 error를 알리며, 성공하면 error를 지운다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  const { result } = renderHook(() => useMyEnrollments());
  await waitFor(() => expect(result.current.data).toEqual(page));

  api.getMyEnrollments.mockRejectedValue(new Error("boom"));
  act(() => result.current.reload());
  await waitFor(() => expect(result.current.error?.message).toBe("boom"));
  expect(result.current.data).toEqual(page);

  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  act(() => result.current.reload());
  await waitFor(() => expect(result.current.error).toBeNull());
});

it("로그인 상태의 첫 렌더부터 loading이 true다", () => {
  api.getMyEnrollments.mockReturnValue(
    new Promise(() => {}) as ReturnType<typeof api.getMyEnrollments>,
  );

  const { result } = renderHook(() => useMyEnrollments());

  expect(result.current.loading).toBe(true);
  expect(result.current.data).toBeNull();
});

it("토큰이 A에서 B로 바로 바뀌면 다시 조회하고 A의 늦은 응답은 무시한다", async () => {
  const pageA = { ...page, content: [{ id: 1, sessionId: 10, status: "CONFIRMED" as const }] };
  const pageB = { ...page, content: [{ id: 2, sessionId: 20, status: "WAITING" as const }] };
  let resolveA: (value: Response<typeof api.getMyEnrollments>) => void = () => {};
  api.getMyEnrollments.mockReturnValueOnce(
    new Promise((r) => (resolveA = r)) as ReturnType<typeof api.getMyEnrollments>,
  );
  const { result, rerender } = renderHook(() => useMyEnrollments());

  api.getMyEnrollments.mockResolvedValue({ data: pageB } as Response<typeof api.getMyEnrollments>);
  loggedIn("t2");
  rerender();

  await waitFor(() => expect(result.current.data).toEqual(pageB));
  expect(api.getMyEnrollments).toHaveBeenCalledTimes(2);

  await act(async () => {
    resolveA({ data: pageA } as Response<typeof api.getMyEnrollments>);
  });
  expect(result.current.data).toEqual(pageB);
});

it("조건이 바뀌면 data를 비우고 loading이 true가 되며, reload로는 유지한다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  const { result, rerender } = renderHook(({ p }) => useMyEnrollments(undefined, p, 20), {
    initialProps: { p: 0 },
  });
  await waitFor(() => expect(result.current.data).toEqual(page));

  act(() => result.current.reload());
  expect(result.current.data).toEqual(page);
  await waitFor(() => expect(result.current.loading).toBe(false));

  api.getMyEnrollments.mockReturnValue(
    new Promise(() => {}) as ReturnType<typeof api.getMyEnrollments>,
  );
  rerender({ p: 1 });

  await waitFor(() => expect(result.current.data).toBeNull());
  expect(result.current.loading).toBe(true);
});

it("조건이 바뀐 뒤 실패하면 이전 조건의 데이터가 남지 않는다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  const { result, rerender } = renderHook(({ s }) => useMyEnrollments(s, 0, 20), {
    initialProps: { s: undefined as "WAITING" | undefined },
  });
  await waitFor(() => expect(result.current.data).toEqual(page));

  api.getMyEnrollments.mockRejectedValue(new Error("boom"));
  rerender({ s: "WAITING" });

  await waitFor(() => expect(result.current.error?.message).toBe("boom"));
  expect(result.current.data).toBeNull();
});

it("조건이 바뀐 첫 렌더는 이전 data 대신 data null, loading true를 돌려준다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  const renders: ReturnType<typeof useMyEnrollments>[] = [];
  const { result, rerender } = renderHook(
    ({ p }) => {
      const r = useMyEnrollments(undefined, p, 20);
      renders.push(r);
      return r;
    },
    { initialProps: { p: 0 } },
  );
  await waitFor(() => expect(result.current.data).toEqual(page));
  await waitFor(() => expect(result.current.loading).toBe(false));

  const before = renders.length;
  rerender({ p: 1 });

  expect(renders[before].data).toBeNull();
  expect(renders[before].loading).toBe(true);
  expect(renders[before].error).toBeNull();
});

it("토큰이 바뀐 첫 렌더는 이전 사용자의 data 대신 data null, loading true를 돌려준다", async () => {
  api.getMyEnrollments.mockResolvedValue({ data: page } as Response<typeof api.getMyEnrollments>);
  const renders: ReturnType<typeof useMyEnrollments>[] = [];
  const { result, rerender } = renderHook(() => {
    const r = useMyEnrollments();
    renders.push(r);
    return r;
  });
  await waitFor(() => expect(result.current.data).toEqual(page));
  await waitFor(() => expect(result.current.loading).toBe(false));

  const before = renders.length;
  loggedIn("t2");
  rerender();

  expect(renders[before].data).toBeNull();
  expect(renders[before].loading).toBe(true);
});
