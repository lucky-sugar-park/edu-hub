import { act, renderHook } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { enrollmentAdminApi } from "../api/courseApi";
import { useCompletion } from "./useCompletion";

vi.mock("../api/courseApi", () => ({
  enrollmentAdminApi: { complete: vi.fn() },
}));

const api = vi.mocked(enrollmentAdminApi);
type Response = Awaited<ReturnType<typeof api.complete>>;

beforeEach(() => vi.resetAllMocks());

it("수료 처리하고 응답 본문을 돌려준다", async () => {
  const body = { completed: ["E1"], failed: [{ employeeId: "E2", reason: "NOT_CONFIRMED" }] };
  api.complete.mockResolvedValue({ data: body } as Response);
  const { result } = renderHook(() => useCompletion());

  let returned: unknown;
  await act(async () => {
    returned = await result.current.complete(10, ["E1", "E2"]);
  });

  expect(returned).toEqual(body);
  expect(api.complete).toHaveBeenCalledWith(10, { employeeIds: ["E1", "E2"] });
  expect(result.current.error).toBeNull();
  expect(result.current.submitting).toBe(false);
});

it("실패하면 null을 돌려주고 서버 메시지를 error에 담으며 clearError로 지운다", async () => {
  api.complete.mockRejectedValue({ response: { data: { message: "회차가 아직 열려 있습니다." } } });
  const { result } = renderHook(() => useCompletion());

  let returned: unknown = "unset";
  await act(async () => {
    returned = await result.current.complete(10, ["E1"]);
  });

  expect(returned).toBeNull();
  expect(result.current.error).toBe("회차가 아직 열려 있습니다.");
  act(() => result.current.clearError());
  expect(result.current.error).toBeNull();
});

it("진행 중에는 submitting이 true다", async () => {
  let resolve: (value: Response) => void = () => {};
  api.complete.mockReturnValue(
    new Promise((r) => (resolve = r)) as ReturnType<typeof api.complete>,
  );
  const { result } = renderHook(() => useCompletion());

  let pending: Promise<unknown> = Promise.resolve();
  act(() => {
    pending = result.current.complete(10, ["E1"]);
  });
  expect(result.current.submitting).toBe(true);

  await act(async () => {
    resolve({ data: {} } as Response);
    await pending;
  });
  expect(result.current.submitting).toBe(false);
});
