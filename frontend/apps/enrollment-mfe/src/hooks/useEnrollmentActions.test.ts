import { act, renderHook } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { enrollmentApi } from "@eduhub/api-client";
import { useEnrollmentActions } from "./useEnrollmentActions";

vi.mock("@eduhub/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@eduhub/api-client")>()),
  enrollmentApi: { enroll: vi.fn(), cancel: vi.fn() },
}));

const api = vi.mocked(enrollmentApi);
type Response<T extends (...args: never[]) => unknown> = Awaited<ReturnType<T>>;

beforeEach(() => vi.resetAllMocks());

it("신청하면 결과를 돌려준다", async () => {
  const body = { id: 1, sessionId: 10, status: "CONFIRMED" as const };
  api.enroll.mockResolvedValue({ data: body } as Response<typeof api.enroll>);
  const { result } = renderHook(() => useEnrollmentActions());

  let returned: unknown;
  await act(async () => {
    returned = await result.current.enroll(10);
  });

  expect(returned).toEqual(body);
  expect(api.enroll).toHaveBeenCalledWith(10);
  expect(result.current.error).toBeNull();
  expect(result.current.submitting).toBe(false);
});

it("취소하면 결과를 돌려준다", async () => {
  const body = { id: 1, sessionId: 10, status: "CANCELLED" as const };
  api.cancel.mockResolvedValue({ data: body } as Response<typeof api.cancel>);
  const { result } = renderHook(() => useEnrollmentActions());

  let returned: unknown;
  await act(async () => {
    returned = await result.current.cancel(10);
  });

  expect(returned).toEqual(body);
  expect(api.cancel).toHaveBeenCalledWith(10);
});

it("실패하면 null을 돌려주고 서버 메시지를 error에 담으며 clearError로 지운다", async () => {
  api.enroll.mockRejectedValue({ response: { data: { message: "이미 시작된 회차입니다." } } });
  const { result } = renderHook(() => useEnrollmentActions());

  let returned: unknown = "unset";
  await act(async () => {
    returned = await result.current.enroll(10);
  });

  expect(returned).toBeNull();
  expect(result.current.error).toBe("이미 시작된 회차입니다.");

  act(() => result.current.clearError());
  expect(result.current.error).toBeNull();
});

it("진행 중에는 submitting이 true다", async () => {
  let resolve: (value: Response<typeof api.enroll>) => void = () => {};
  api.enroll.mockReturnValue(new Promise((r) => (resolve = r)) as ReturnType<typeof api.enroll>);
  const { result } = renderHook(() => useEnrollmentActions());

  let pending: Promise<unknown> = Promise.resolve();
  act(() => {
    pending = result.current.enroll(10);
  });
  expect(result.current.submitting).toBe(true);

  await act(async () => {
    resolve({ data: { id: 1 } } as Response<typeof api.enroll>);
    await pending;
  });
  expect(result.current.submitting).toBe(false);
});
