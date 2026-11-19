import type { SessionRosterResponse } from "@eduhub/api-client";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { type UseCompletionResult, useCompletion } from "../hooks/useCompletion";
import { SessionForbiddenError, SessionNotFoundError, useSessionRoster } from "../hooks/useSessionRoster";
import { fakeAuth, renderWithProviders } from "../test-utils";
import { SessionRosterPage } from "./SessionRosterPage";

vi.mock("../hooks/useSessionRoster", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../hooks/useSessionRoster")>()),
  useSessionRoster: vi.fn(),
}));
vi.mock("../hooks/useCompletion", () => ({ useCompletion: vi.fn() }));

const mockedRoster = vi.mocked(useSessionRoster);
const mockedCompletion = vi.mocked(useCompletion);

let reload: ReturnType<typeof vi.fn>;
let completion: UseCompletionResult;

function makeRoster(overrides: Partial<SessionRosterResponse> = {}): SessionRosterResponse {
  return {
    sessionId: 10,
    courseId: 3,
    courseTitle: "JPA 입문",
    startAt: "2026-12-01T05:00:00Z",
    place: "본사 3층",
    capacity: 30,
    sessionStatus: "CLOSED",
    confirmed: [
      {
        enrollmentId: 1,
        employeeId: "E1001",
        status: "CONFIRMED",
        confirmedAt: "2026-11-01T01:00:00Z",
      },
      {
        enrollmentId: 2,
        employeeId: "E1002",
        status: "CONFIRMED",
        confirmedAt: "2026-11-01T02:00:00Z",
      },
      {
        enrollmentId: 3,
        employeeId: "E1003",
        status: "COMPLETED",
        confirmedAt: "2026-11-01T03:00:00Z",
        completedAt: "2026-12-02T05:00:00Z",
      },
    ],
    waiting: [
      { enrollmentId: 4, employeeId: "E2001", waitingNumber: 1, appliedAt: "2026-11-02T01:00:00Z" },
    ],
    ...overrides,
  };
}

function given(
  data: SessionRosterResponse | null,
  extra: Partial<ReturnType<typeof useSessionRoster>> = {},
) {
  mockedRoster.mockReturnValue({ data, loading: false, error: null, reload, ...extra });
}

function renderPage(sessionId = "10") {
  renderWithProviders(<SessionRosterPage />, {
    route: `/admin/sessions/${sessionId}/roster`,
    path: "/admin/sessions/:sessionId/roster",
    auth: fakeAuth(["EMPLOYEE", "ADMIN"], "E9001"),
  });
}

const checkbox = (name: string) => screen.getByRole("checkbox", { name }) as HTMLInputElement;
const completeButton = () => screen.getByRole("button", { name: /^수료 처리/ });

beforeEach(() => {
  reload = vi.fn();
  completion = { submitting: false, error: null, clearError: vi.fn(), complete: vi.fn() };
  mockedCompletion.mockImplementation(() => completion);
  given(makeRoster());
});

// U6
it("머리와 확정·수료·대기 표를 보여 준다", () => {
  renderPage();

  expect(mockedRoster).toHaveBeenLastCalledWith(10);
  expect(screen.getByRole("heading", { level: 1, name: "JPA 입문" })).toBeInTheDocument();
  expect(screen.getByText("2026. 12. 1. (화) 14:00")).toBeInTheDocument();
  expect(screen.getByText(/본사 3층/)).toBeInTheDocument();
  expect(screen.getByText(/정원 30명/)).toBeInTheDocument();
  expect(screen.getByText("확정 2명 · 수료 1명 · 대기 1명")).toBeInTheDocument();

  const confirmed = screen.getByRole("table", { name: "확정·수료 명단" });
  expect(within(confirmed).getByText("E1003")).toBeInTheDocument();
  expect(within(confirmed).getByText("수료")).toBeInTheDocument();
  expect(within(confirmed).getByText("2026. 12. 2. (수) 14:00")).toBeInTheDocument();
  const waiting = screen.getByRole("table", { name: "대기 명단" });
  expect(within(waiting).getByText("E2001")).toBeInTheDocument();
});

it("CONFIRMED만 선택할 수 있다", () => {
  renderPage();

  expect(checkbox("E1001 선택")).toBeEnabled();
  expect(checkbox("E1003 선택")).toBeDisabled();
});

it("머리 체크박스는 확정자 전체를 선택하고 해제한다", () => {
  renderPage();

  fireEvent.click(checkbox("확정자 전체 선택"));
  expect(checkbox("E1001 선택").checked).toBe(true);
  expect(checkbox("E1002 선택").checked).toBe(true);
  expect(checkbox("E1003 선택").checked).toBe(false);

  fireEvent.click(checkbox("확정자 전체 선택"));
  expect(checkbox("E1001 선택").checked).toBe(false);
});

it("열린 회차에서는 수료 처리가 꺼지고 안내가 나온다", () => {
  given(makeRoster({ sessionStatus: "OPEN" }));
  renderPage();

  fireEvent.click(checkbox("E1001 선택"));

  expect(completeButton()).toBeDisabled();
  expect(screen.getByText("회차를 종료한 뒤 수료 처리할 수 있습니다")).toBeInTheDocument();
});

it("선택이 없으면 수료 처리가 꺼지고, 선택하면 켜진다", () => {
  renderPage();
  expect(completeButton()).toBeDisabled();

  fireEvent.click(checkbox("E1001 선택"));

  expect(completeButton()).toBeEnabled();
  expect(screen.queryByText("회차를 종료한 뒤 수료 처리할 수 있습니다")).not.toBeInTheDocument();
});

// U7
it("수료 처리 → 결과 대화 상자(성공·실패 사유) → 닫으면 다시 불러오고 선택을 비운다", async () => {
  vi.mocked(completion.complete).mockResolvedValue({
    completed: ["E1001"],
    failed: [{ employeeId: "E1002", reason: "NOT_CONFIRMED" }],
  });
  renderPage();
  fireEvent.click(checkbox("E1001 선택"));
  fireEvent.click(checkbox("E1002 선택"));

  fireEvent.click(completeButton());
  const confirm = screen.getByRole("dialog");
  expect(within(confirm).getByText(/선택한 2명을 수료 처리합니다/)).toBeInTheDocument();
  expect(completion.complete).not.toHaveBeenCalled();
  fireEvent.click(within(confirm).getByRole("button", { name: "수료 처리" }));

  await waitFor(() => expect(completion.complete).toHaveBeenCalledWith(10, ["E1001", "E1002"]));
  const result = await screen.findByRole("dialog", { name: "수료 처리 결과" });
  expect(within(result).getByText("E1001")).toBeInTheDocument();
  expect(within(result).getByText("E1002")).toBeInTheDocument();
  expect(within(result).getByText("대기 중")).toBeInTheDocument();
  expect(reload).not.toHaveBeenCalled();

  fireEvent.click(within(result).getByRole("button", { name: "확인" }));

  expect(reload).toHaveBeenCalledTimes(1);
  // 대화 상자가 닫히는 전환이 끝나면 선택이 비어 있다.
  await waitFor(() => expect(checkbox("E1001 선택").checked).toBe(false));
  expect(checkbox("E1002 선택").checked).toBe(false);
});

it("수료 요청이 실패하면(409 등) 대화 상자 안에 오류를 보이고 다시 불러온다", async () => {
  completion = { ...completion, error: "회차가 아직 열려 있습니다." };
  vi.mocked(completion.complete).mockResolvedValue(null);
  renderPage();
  fireEvent.click(checkbox("E1001 선택"));

  fireEvent.click(completeButton());
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("회차가 아직 열려 있습니다.")).toBeInTheDocument();
  fireEvent.click(within(dialog).getByRole("button", { name: "수료 처리" }));

  await waitFor(() => expect(reload).toHaveBeenCalledTimes(1));
  expect(screen.getByRole("dialog")).toBeInTheDocument();
});

it("다시 불러온 명단에서 수료됐거나 사라진 사번은 선택에서 뺀다", () => {
  const entry = (n: number, status: "CONFIRMED" | "COMPLETED" = "CONFIRMED") => ({
    enrollmentId: n,
    employeeId: `E100${n}`,
    status,
  });
  given(makeRoster({ confirmed: [entry(1), entry(2), entry(4)] }));
  renderPage();
  fireEvent.click(checkbox("확정자 전체 선택"));

  // 명단이 바뀐다: E1001은 사라지고 E1002는 수료됐으며 E1004만 확정으로 남는다.
  given(makeRoster({ confirmed: [entry(2, "COMPLETED"), entry(4)] }));
  fireEvent.click(completeButton()); // 상태 변경으로 다시 그리게 하고 선택 수를 확인한다.

  expect(within(screen.getByRole("dialog")).getByText(/선택한 1명을 수료 처리합니다/)).toBeInTheDocument();
});

it("회차가 없으면 찾을 수 없다고 알린다", () => {
  given(null, { error: new SessionNotFoundError() });

  renderPage("999");

  expect(screen.getByRole("alert")).toHaveTextContent("회차를 찾을 수 없습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});

it("숫자가 아닌 sessionId는 NaN으로 훅에 넘긴다", () => {
  given(null, { error: new SessionNotFoundError() });

  renderPage("abc");

  expect(mockedRoster).toHaveBeenLastCalledWith(Number.NaN);
  expect(screen.getByRole("alert")).toHaveTextContent("회차를 찾을 수 없습니다.");
});

it("조회가 실패하면 알리고 '다시 시도'로 다시 불러온다", () => {
  given(null, { error: new Error("boom") });

  renderPage();

  expect(screen.getByRole("alert")).toHaveTextContent("명단을 불러오지 못했습니다");
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(reload).toHaveBeenCalledTimes(1);
});

it("다시 불러오기가 실패해 기존 명단이 남아도 수료 처리는 꺼진다", () => {
  given(makeRoster(), { error: new Error("boom") });

  renderPage();

  expect(screen.getByRole("table", { name: "확정·수료 명단" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: /^수료 처리/ })).toBeDisabled();
});

it("처음 불러오는 중에는 진행 표시를 보여 준다", () => {
  given(null, { loading: true });

  renderPage();

  expect(screen.getByRole("progressbar", { name: "불러오는 중" })).toBeInTheDocument();
});

it("403이면 권한 없음을 보여 주고 다시 시도 버튼은 숨긴다", () => {
  given(null, { error: new SessionForbiddenError() });

  renderPage();

  expect(screen.getByRole("alert")).toHaveTextContent("권한이 없습니다.");
  expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
});

const many = (count: number) =>
  Array.from({ length: count }, (_, i) => ({
    enrollmentId: i + 1,
    employeeId: `M${String(i + 1).padStart(4, "0")}`,
    status: "CONFIRMED" as const,
  }));

it("확정자가 201명이면 머리 체크박스는 200명만 선택하고 201번째 행은 꺼진다", () => {
  given(makeRoster({ confirmed: many(201) }));
  renderPage();

  expect(screen.getByText("한 번에 최대 200명까지 수료 처리할 수 있습니다")).toBeInTheDocument();
  fireEvent.click(checkbox("확정자 전체 선택"));

  const checked = screen.getAllByRole("checkbox").filter((c) => (c as HTMLInputElement).checked);
  // 머리 체크박스(전체 선택 표시) 1개 + 선택된 행 200개
  expect(checked).toHaveLength(201);
  expect(checkbox("M0200 선택").checked).toBe(true);
  expect(checkbox("M0201 선택").checked).toBe(false);
  expect(checkbox("M0201 선택")).toBeDisabled();
  expect(checkbox("M0200 선택")).toBeEnabled();
  expect(checkbox("확정자 전체 선택").checked).toBe(true);

  fireEvent.click(checkbox("M0001 선택"));
  expect(checkbox("M0201 선택")).toBeEnabled();
});

it("확정자가 200명 이하면 상한 안내가 없다", () => {
  given(makeRoster({ confirmed: many(200) }));
  renderPage();

  expect(screen.queryByText(/한 번에 최대 200명/)).not.toBeInTheDocument();
});

it("머리 체크박스는 이미 선택한 사람을 뺀 나머지를 앞에서부터 채운다", () => {
  given(makeRoster({ confirmed: many(250) }));
  renderPage();
  fireEvent.click(checkbox("M0250 선택"));

  fireEvent.click(checkbox("확정자 전체 선택"));

  expect(checkbox("M0250 선택").checked).toBe(true);
  expect(checkbox("M0199 선택").checked).toBe(true);
  expect(checkbox("M0200 선택").checked).toBe(false);
});

it("확인 대화 상자를 연 채 선택이 0명이 되면 대화 상자를 닫는다", async () => {
  renderPage();
  fireEvent.click(checkbox("E1001 선택"));
  fireEvent.click(completeButton());
  expect(screen.getByRole("dialog")).toBeInTheDocument();

  fireEvent.click(screen.getByRole("checkbox", { name: "E1001 선택", hidden: true })); // 선택이 0명이 된다.

  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  // 사용자가 직접 해제한 경우에는 경고를 띄우지 않는다.
  expect(screen.queryByText(/더 이상 수료 처리 대상이 아닙니다/)).not.toBeInTheDocument();
});

it("수료 실패 뒤 다시 불러온 명단으로 선택이 비면 대화 상자를 닫고 경고를 남긴다", async () => {
  completion = { ...completion, error: "회차가 아직 열려 있습니다." };
  renderPage();
  fireEvent.click(checkbox("E1001 선택"));
  fireEvent.click(completeButton());
  expect(screen.getByRole("dialog")).toBeInTheDocument();

  // 명단이 바뀌어 선택한 사번이 모두 대상에서 빠진다. 다시 그리게 하려고 다른 행을 하나 더 선택한다.
  given(makeRoster({ confirmed: [] }));
  fireEvent.click(screen.getByRole("checkbox", { name: "E1002 선택", hidden: true }));

  await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  const notice = screen.getByRole("alert");
  expect(notice).toHaveTextContent("선택한 사번이 더 이상 수료 처리 대상이 아닙니다. 명단을 확인해 주세요");
  expect(notice).toHaveTextContent("회차가 아직 열려 있습니다.");
});

it("진행 중에는 확인 버튼이 꺼진다", () => {
  renderPage();
  fireEvent.click(checkbox("E1001 선택"));
  fireEvent.click(completeButton());
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByRole("button", { name: "수료 처리" })).toBeEnabled();

  completion = { ...completion, submitting: true };
  fireEvent.click(screen.getByRole("checkbox", { name: "E1002 선택", hidden: true })); // 다시 그리게 한다.

  expect(within(screen.getByRole("dialog")).getByRole("button", { name: "수료 처리" })).toBeDisabled();
});

it("열린 회차의 수료 버튼은 안내 문구와 aria-describedby로 연결된다", () => {
  given(makeRoster({ sessionStatus: "OPEN" }));
  renderPage();

  const hint = screen.getByText("회차를 종료한 뒤 수료 처리할 수 있습니다");
  expect(completeButton()).toHaveAttribute("aria-describedby", hint.id);
});
