import type { MyEnrollmentResponse } from "@eduhub/api-client";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  type UseEnrollmentActionsResult,
  useEnrollmentActions,
} from "../hooks/useEnrollmentActions";
import { useMyEnrollments } from "../hooks/useMyEnrollments";
import { fakeAuth, renderWithProviders } from "../../../course-mfe/src/test-utils";
import { MyEnrollmentsPage } from "./MyEnrollmentsPage";

vi.mock("../hooks/useMyEnrollments", () => ({ useMyEnrollments: vi.fn() }));
vi.mock("../hooks/useEnrollmentActions", () => ({ useEnrollmentActions: vi.fn() }));

const mockedUseMine = vi.mocked(useMyEnrollments);
const mockedUseActions = vi.mocked(useEnrollmentActions);

const NOW = new Date("2026-11-20T00:00:00Z");
const FUTURE = "2026-12-01T05:00:00Z";
const PAST = "2026-11-10T05:00:00Z";

let reload: ReturnType<typeof vi.fn>;
let actions: UseEnrollmentActionsResult;

function row(overrides: Partial<MyEnrollmentResponse> = {}): MyEnrollmentResponse {
  return {
    id: 1,
    courseId: 3,
    courseTitle: "JPA 입문",
    sessionId: 10,
    sessionStartAt: FUTURE,
    sessionPlace: "본사 3층",
    status: "CONFIRMED",
    appliedAt: "2026-11-01T01:00:00Z",
    ...overrides,
  };
}

function givenRows(content: MyEnrollmentResponse[], totalElements = content.length) {
  mockedUseMine.mockReturnValue({
    data: { content, page: 0, size: 20, totalElements },
    loading: false,
    error: null,
    reload,
  });
}

function Where() {
  const location = useLocation();
  return <div data-testid="where">{location.search}</div>;
}

function renderAt(route = "/me/enrollments") {
  renderWithProviders(
    <>
      <MyEnrollmentsPage />
      <Where />
    </>,
    { route, auth: fakeAuth() },
  );
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
  reload = vi.fn();
  mockedUseMine.mockClear();
  actions = {
    submitting: false,
    error: null,
    clearError: vi.fn(),
    enroll: vi.fn(),
    cancel: vi.fn(),
  };
  mockedUseActions.mockImplementation(() => actions);
  givenRows([row()]);
});

afterEach(() => {
  vi.useRealTimers();
});

it("신청 내역을 표로 보여 주고 과정은 상세 링크다", () => {
  givenRows([
    row(),
    row({
      id: 2,
      courseId: 4,
      courseTitle: "Spring",
      sessionId: 11,
      status: "WAITING",
      waitingNumber: 2,
    }),
  ]);

  renderAt();

  expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 0, 20);
  expect(screen.getByRole("link", { name: "JPA 입문" })).toHaveAttribute("href", "/courses/3");
  expect(screen.getByRole("link", { name: "Spring" })).toHaveAttribute("href", "/courses/4");
  const table = screen.getByRole("table", { name: "내 신청 목록" });
  expect(within(table).getAllByRole("cell", { name: "2026. 12. 1. (화) 14:00" })).toHaveLength(2);
  expect(within(table).getByText("확정")).toBeInTheDocument();
  expect(within(table).getByText("2번")).toBeInTheDocument();
});

it("수료 상태 칩이 나온다", () => {
  givenRows([row({ status: "COMPLETED", sessionStartAt: PAST })]);

  renderAt();

  expect(within(screen.getByRole("table")).getByText("수료")).toBeInTheDocument();
});

// U5
it("탭을 바꾸면 status로 다시 조회하고 첫 페이지로 간다", () => {
  renderAt("/me/enrollments?status=CONFIRMED&page=2");
  expect(mockedUseMine).toHaveBeenLastCalledWith("CONFIRMED", 2, 20);

  fireEvent.click(screen.getByRole("tab", { name: "대기" }));

  expect(mockedUseMine).toHaveBeenLastCalledWith("WAITING", 0, 20);
  expect(screen.getByTestId("where")).toHaveTextContent("?status=WAITING");
  expect(screen.getByTestId("where")).not.toHaveTextContent("page=");

  fireEvent.click(screen.getByRole("tab", { name: "전체" }));

  expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 0, 20);
  expect(screen.getByTestId("where")).toBeEmptyDOMElement();
});

it("알 수 없는 status와 page 쿼리는 무시한다", () => {
  renderAt("/me/enrollments?status=BOGUS&page=-3");

  expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 0, 20);
});

it("페이지를 옮기면 page로 다시 조회한다", () => {
  givenRows([row()], 45);
  renderAt("/me/enrollments?status=WAITING");

  fireEvent.click(screen.getByRole("button", { name: /next page|다음/i }));

  expect(mockedUseMine).toHaveBeenLastCalledWith("WAITING", 1, 20);
  expect(screen.getByTestId("where")).toHaveTextContent("?status=WAITING&page=1");
});

it("비어 있으면 안내와 과정 목록 링크를 보여 준다", () => {
  givenRows([]);

  renderAt();

  expect(screen.getByText("신청 내역이 없습니다")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "과정 목록" })).toHaveAttribute("href", "/");
});

it("취소는 확인 뒤 API를 부르고 다시 불러온다", async () => {
  vi.mocked(actions.cancel).mockResolvedValue({ id: 1, sessionId: 10, status: "CANCELLED" });
  renderAt();

  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText(/확정자가 취소하면 대기 1순위가 확정됩니다/)).toBeInTheDocument();
  expect(actions.cancel).not.toHaveBeenCalled();

  fireEvent.click(within(dialog).getByRole("button", { name: "신청 취소" }));

  await waitFor(() => expect(actions.cancel).toHaveBeenCalledWith(10));
  expect(await screen.findByText("신청이 취소되었습니다")).toBeInTheDocument();
  expect(reload).toHaveBeenCalled();
});

it("대기 신청의 확인 문구는 대기 취소를 안내한다", () => {
  givenRows([row({ status: "WAITING", waitingNumber: 1 })]);
  renderAt();

  fireEvent.click(screen.getByRole("button", { name: "취소" }));

  expect(
    within(screen.getByRole("dialog")).getByText("대기 신청을 취소할까요?"),
  ).toBeInTheDocument();
});

it("취소가 실패하면 대화 상자 안에 오류를 보이고 다시 불러온다", async () => {
  actions = { ...actions, error: "이미 시작된 회차입니다." };
  vi.mocked(actions.cancel).mockResolvedValue(null);
  renderAt();

  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("이미 시작된 회차입니다.")).toBeInTheDocument();

  fireEvent.click(within(dialog).getByRole("button", { name: "신청 취소" }));

  await waitFor(() => expect(reload).toHaveBeenCalled());
  expect(screen.getByRole("dialog")).toBeInTheDocument();
});

it("시작한 회차, 취소·수료 건에는 취소 버튼이 없다", () => {
  givenRows([
    row({ id: 1, sessionStartAt: PAST }),
    row({ id: 2, status: "CANCELLED" }),
    row({ id: 3, status: "COMPLETED", sessionStartAt: PAST }),
  ]);

  renderAt();

  expect(screen.queryByRole("button", { name: /^취소/ })).not.toBeInTheDocument();
});

it("받아 오는 중에는 취소 버튼이 꺼진다", () => {
  mockedUseMine.mockReturnValue({
    data: { content: [row()], page: 0, size: 20, totalElements: 1 },
    loading: true,
    error: null,
    reload,
  });

  renderAt();

  expect(screen.getByRole("button", { name: /^취소/ })).toBeDisabled();
});

it("조회가 실패하면 알리고 '다시 시도'로 다시 불러온다", () => {
  mockedUseMine.mockReturnValue({ data: null, loading: false, error: new Error("boom"), reload });

  renderAt();

  expect(screen.getByRole("alert")).toHaveTextContent("내 신청 내역을 불러오지 못했습니다");
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
  expect(reload).toHaveBeenCalledTimes(1);
});

it("처음 불러오는 중에는 진행 표시를 보여 준다", () => {
  mockedUseMine.mockReturnValue({ data: null, loading: true, error: null, reload });

  renderAt();

  expect(screen.getByRole("progressbar", { name: "불러오는 중" })).toBeInTheDocument();
});

it.each(["abc", "1.5", "-1"])("page=%s 쿼리는 0으로 본다", (value) => {
  renderAt(`/me/enrollments?page=${value}`);

  expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 0, 20);
});

it("소문자 status 쿼리는 무시한다", () => {
  renderAt("/me/enrollments?status=waiting");

  expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 0, 20);
});

it.each([
  ["?page=1", 20],
  ["?page=1", 0],
  ["?page=99", 0],
])("%s에서 내용이 비고 totalElements가 %i이면 첫 페이지로 옮긴다", async (query, total) => {
  givenRows([], total);

  renderAt(`/me/enrollments${query}`);

  await waitFor(() => expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 0, 20));
  expect(screen.getByTestId("where")).not.toHaveTextContent("page=");
  // 이동은 한 번이면 충분하다(반복 이동 방지).
  expect(mockedUseMine.mock.calls.length).toBeLessThan(6);
});

it("범위를 벗어난 page는 마지막 유효 페이지로 옮기고 status를 유지한다", async () => {
  givenRows([], 45);

  renderAt("/me/enrollments?status=WAITING&page=9");

  await waitFor(() => expect(mockedUseMine).toHaveBeenLastCalledWith("WAITING", 2, 20));
  expect(screen.getByTestId("where")).toHaveTextContent("?status=WAITING&page=2");
  expect(mockedUseMine.mock.calls.length).toBeLessThan(6);
});

it("목표 페이지가 현재 page 이상이면 옮기지 않는다", async () => {
  givenRows([], 25);

  renderAt("/me/enrollments?page=1");

  await waitFor(() => expect(screen.getByTestId("where")).toHaveTextContent("?page=1"));
  expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 1, 20);
  expect(mockedUseMine.mock.calls.length).toBeLessThan(6);
});

it("이전 페이지로 가면 page 쿼리가 빠진다", () => {
  givenRows([row()], 45);
  renderAt("/me/enrollments?page=1");

  fireEvent.click(screen.getByRole("button", { name: /previous page|이전/i }));

  expect(mockedUseMine).toHaveBeenLastCalledWith(undefined, 0, 20);
  expect(screen.getByTestId("where")).toBeEmptyDOMElement();
});

it("courseId가 없으면 링크 없이 제목만 보여 준다", () => {
  givenRows([row({ courseId: undefined })]);

  renderAt();

  expect(screen.queryByRole("link", { name: "JPA 입문" })).not.toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "JPA 입문" })).toBeInTheDocument();
});

it("submitting이면 취소 버튼이 꺼진다", () => {
  actions = { ...actions, submitting: true };

  renderAt();

  expect(screen.getByRole("button", { name: "취소" })).toBeDisabled();
});
