import type { CourseDetailResponse, MyEnrollmentResponse } from "@eduhub/api-client";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CourseNotFoundError, useCourse } from "../hooks/useCourse";
import {
  type UseEnrollmentActionsResult,
  useEnrollmentActions,
} from "../enrollment/useEnrollmentActions";
import { useMyEnrollments } from "../enrollment/useMyEnrollments";
import { fakeAuth, renderWithProviders } from "../test-utils";
import { CourseDetailPage } from "./CourseDetailPage";

vi.mock("../hooks/useCourse", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../hooks/useCourse")>()),
  useCourse: vi.fn(),
}));
vi.mock("../enrollment/useMyEnrollments", () => ({ useMyEnrollments: vi.fn() }));
vi.mock("../enrollment/useEnrollmentActions", () => ({ useEnrollmentActions: vi.fn() }));

const mockedUseCourse = vi.mocked(useCourse);
const mockedUseMyEnrollments = vi.mocked(useMyEnrollments);
const mockedUseEnrollmentActions = vi.mocked(useEnrollmentActions);

const NOW = new Date("2026-11-20T00:00:00Z");
const FUTURE = "2026-12-01T05:00:00Z";
const PAST = "2026-11-10T05:00:00Z";

let reloadCourse: ReturnType<typeof vi.fn>;
let reloadMine: ReturnType<typeof vi.fn>;
let actions: UseEnrollmentActionsResult;

function course(overrides: Partial<CourseDetailResponse> = {}): CourseDetailResponse {
  return {
    id: 3,
    title: "JPA 입문",
    category: "개발",
    description: "엔티티와 연관관계",
    status: "OPEN",
    sessions: [
      {
        id: 10,
        startAt: FUTURE,
        place: "본사 3층 교육장",
        capacity: 30,
        remainingSeats: 12,
        status: "OPEN",
      },
    ],
    ...overrides,
  };
}

function givenCourse(data: CourseDetailResponse) {
  mockedUseCourse.mockReturnValue({ data, loading: false, error: null, reload: reloadCourse });
}

function givenMine(content: MyEnrollmentResponse[]) {
  mockedUseMyEnrollments.mockReturnValue({
    data: { content, page: 0, size: 100, totalElements: content.length },
    loading: false,
    error: null,
    reload: reloadMine,
  });
}

function renderAt(path: string, auth = false) {
  renderWithProviders(<CourseDetailPage />, {
    route: path,
    path: "/courses/:id",
    auth: auth ? fakeAuth() : null,
  });
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(NOW);
  reloadCourse = vi.fn();
  reloadMine = vi.fn();
  actions = {
    submitting: false,
    error: null,
    clearError: vi.fn(),
    enroll: vi.fn(),
    cancel: vi.fn(),
  };
  mockedUseEnrollmentActions.mockImplementation(() => actions);
  givenMine([]);
});

afterEach(() => {
  vi.useRealTimers();
});

it("과정 정보와 회차를 보여 준다", () => {
  givenCourse(course());

  renderAt("/courses/3");

  expect(mockedUseCourse).toHaveBeenCalledWith(3);
  expect(screen.getByRole("heading", { level: 1, name: "JPA 입문" })).toBeInTheDocument();
  expect(screen.getByText("엔티티와 연관관계")).toBeInTheDocument();

  const table = screen.getByRole("table", { name: "회차 목록" });
  // UTC 05:00은 한국 시간 14:00이다.
  expect(within(table).getByRole("cell", { name: "2026. 12. 1. (화) 14:00" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "본사 3층 교육장" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "30" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "12" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "운영 중" })).toBeInTheDocument();
});

it("회차가 없으면 안내 문구를 보여 준다", () => {
  givenCourse(course({ sessions: [] }));

  renderAt("/courses/3");

  expect(screen.getByRole("cell", { name: "등록된 회차가 없습니다." })).toBeInTheDocument();
});

it("로딩 중에는 진행 표시를 보여 준다", () => {
  mockedUseCourse.mockReturnValue({ data: null, loading: true, error: null, reload: reloadCourse });

  renderAt("/courses/3");

  expect(screen.getByRole("progressbar", { name: "불러오는 중" })).toBeInTheDocument();
});

it("과정이 없으면 찾을 수 없다고 알린다", () => {
  mockedUseCourse.mockReturnValue({
    data: null,
    loading: false,
    error: new CourseNotFoundError(),
    reload: reloadCourse,
  });

  renderAt("/courses/999");

  expect(screen.getByRole("alert")).toHaveTextContent("과정을 찾을 수 없습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "← 목록으로" })).toHaveAttribute("href", "/");
});

it("그 밖의 실패는 불러오지 못했다고 알린다", () => {
  mockedUseCourse.mockReturnValue({
    data: null,
    loading: false,
    error: new Error("서버 오류"),
    reload: reloadCourse,
  });

  renderAt("/courses/3");

  expect(screen.getByRole("alert")).toHaveTextContent("과정 정보를 불러오지 못했습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});

// U1
it("비로그인이면 '로그인 후 신청'이 보이고 누르면 돌아올 경로와 함께 /login으로 간다", () => {
  givenCourse(course());
  renderWithProviders(
    <Routes>
      <Route path="/courses/:id" element={<CourseDetailPage />} />
      <Route path="/login" element={<LoginProbe />} />
    </Routes>,
    { route: "/courses/3" },
  );

  fireEvent.click(screen.getByRole("link", { name: "로그인 후 신청" }));

  expect(screen.getByTestId("probe")).toHaveTextContent("/courses/3");
  expect(screen.queryByRole("button", { name: "신청" })).not.toBeInTheDocument();
});

function LoginProbe() {
  const location = useLocation();
  return <div data-testid="probe">{(location.state as { from?: string } | null)?.from}</div>;
}

// U2
it("로그인 상태에서 신청하면 API를 부르고 확정 알림과 다시 불러오기를 한다", async () => {
  givenCourse(course());
  vi.mocked(actions.enroll).mockResolvedValue({ id: 1, sessionId: 10, status: "CONFIRMED" });

  renderAt("/courses/3", true);
  fireEvent.click(screen.getByRole("button", { name: "신청" }));

  expect(await screen.findByText("신청이 확정되었습니다")).toBeInTheDocument();
  expect(actions.enroll).toHaveBeenCalledWith(10);
  expect(reloadCourse).toHaveBeenCalled();
  expect(reloadMine).toHaveBeenCalled();
});

it("대기로 신청되면 대기 번호를 알린다", async () => {
  givenCourse(course());
  vi.mocked(actions.enroll).mockResolvedValue({
    id: 1,
    sessionId: 10,
    status: "WAITING",
    waitingNumber: 3,
  });

  renderAt("/courses/3", true);
  fireEvent.click(screen.getByRole("button", { name: "신청" }));

  expect(await screen.findByText("대기 3번으로 신청되었습니다")).toBeInTheDocument();
});

// U3
it("확정·대기 칩을 보여 주고 취소는 확인 뒤 API를 부른다", async () => {
  givenCourse(
    course({
      sessions: [
        { id: 10, startAt: FUTURE, place: "A", capacity: 5, remainingSeats: 0, status: "OPEN" },
        { id: 11, startAt: FUTURE, place: "B", capacity: 5, remainingSeats: 0, status: "OPEN" },
      ],
    }),
  );
  givenMine([
    { id: 1, sessionId: 10, status: "CONFIRMED" },
    { id: 2, sessionId: 11, status: "WAITING", waitingNumber: 2 },
    { id: 3, sessionId: 12, status: "CANCELLED" },
  ]);
  vi.mocked(actions.cancel).mockResolvedValue({ id: 1, sessionId: 10, status: "CANCELLED" });

  renderAt("/courses/3", true);

  expect(screen.getByText("확정")).toBeInTheDocument();
  expect(screen.getByText("대기 2번")).toBeInTheDocument();

  fireEvent.click(screen.getAllByRole("button", { name: "취소" })[0]);
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText(/확정자가 취소하면 대기 1순위가 확정됩니다/)).toBeInTheDocument();
  expect(actions.cancel).not.toHaveBeenCalled();

  fireEvent.click(within(dialog).getByRole("button", { name: "신청 취소" }));

  await waitFor(() => expect(actions.cancel).toHaveBeenCalledWith(10));
  expect(await screen.findByText("신청이 취소되었습니다")).toBeInTheDocument();
  expect(reloadCourse).toHaveBeenCalled();
  expect(reloadMine).toHaveBeenCalled();
});

// U4
it("신청이 거절되면(409 등) 서버 메시지를 Alert로 보여 준다", () => {
  givenCourse(course());
  actions = { ...actions, error: "이미 시작된 회차입니다." };

  renderAt("/courses/3", true);

  expect(screen.getByRole("alert")).toHaveTextContent("이미 시작된 회차입니다.");
});

it("종료·시작 후 회차와 종료된 과정은 신청 버튼이 꺼진다", () => {
  givenCourse(
    course({
      sessions: [
        { id: 10, startAt: PAST, place: "A", capacity: 5, remainingSeats: 5, status: "OPEN" },
        { id: 11, startAt: FUTURE, place: "B", capacity: 5, remainingSeats: 5, status: "CLOSED" },
        { id: 12, startAt: FUTURE, place: "C", capacity: 5, remainingSeats: 5, status: "OPEN" },
      ],
    }),
  );

  renderAt("/courses/3", true);

  const buttons = screen.getAllByRole("button", { name: /^신청/ });
  expect(buttons[0]).toBeDisabled();
  expect(buttons[1]).toBeDisabled();
  expect(buttons[2]).toBeEnabled();
});

it("종료된 과정이면 모든 신청 버튼이 꺼진다", () => {
  givenCourse(course({ status: "CLOSED" }));

  renderAt("/courses/3", true);

  expect(screen.getByRole("button", { name: /^신청/ })).toBeDisabled();
});

it("시작한 회차는 취소 버튼이 꺼진다", () => {
  givenCourse(
    course({
      sessions: [
        { id: 10, startAt: PAST, place: "A", capacity: 5, remainingSeats: 5, status: "OPEN" },
      ],
    }),
  );
  givenMine([{ id: 1, sessionId: 10, status: "CONFIRMED" }]);

  renderAt("/courses/3", true);

  expect(screen.getByRole("button", { name: /^취소/ })).toBeDisabled();
});

const sessionAt = (startAt: string, status: "OPEN" | "CLOSED" = "OPEN") => ({
  id: 10,
  startAt,
  place: "A",
  capacity: 5,
  remainingSeats: 5,
  status,
});

it("같은 회차에 취소 이력과 확정이 함께 있으면 확정으로 보여 준다", () => {
  givenCourse(course());
  givenMine([
    { id: 1, sessionId: 10, status: "CANCELLED" },
    { id: 2, sessionId: 10, status: "CONFIRMED" },
  ]);

  renderAt("/courses/3", true);

  expect(screen.getByText("확정")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "신청" })).not.toBeInTheDocument();
});

it("취소 이력만 있으면 신청 버튼을 보여 준다", () => {
  givenCourse(course());
  givenMine([{ id: 1, sessionId: 10, status: "CANCELLED" }]);

  renderAt("/courses/3", true);

  expect(screen.getByRole("button", { name: "신청" })).toBeEnabled();
});

it("종료된 과정의 사유를 접근 가능한 이름으로 담는다", () => {
  givenCourse(course({ status: "CLOSED" }));
  renderAt("/courses/3", true);
  expect(screen.getByRole("button", { name: "신청 (종료된 과정입니다)" })).toBeDisabled();
});

it("종료된 회차의 사유", () => {
  givenCourse(course({ sessions: [sessionAt(FUTURE, "CLOSED")] }));
  renderAt("/courses/3", true);
  expect(screen.getByRole("button", { name: "신청 (종료된 회차입니다)" })).toBeDisabled();
});

it("시작 시각이 현재와 같으면 시작한 것으로 본다", () => {
  givenCourse(course({ sessions: [sessionAt(NOW.toISOString())] }));
  renderAt("/courses/3", true);
  expect(screen.getByRole("button", { name: "신청 (이미 시작된 회차입니다)" })).toBeDisabled();
});

it("시작한 회차의 취소 사유", () => {
  givenCourse(course({ sessions: [sessionAt(PAST)] }));
  givenMine([{ id: 1, sessionId: 10, status: "CONFIRMED" }]);
  renderAt("/courses/3", true);
  expect(
    screen.getByRole("button", { name: "취소 (이미 시작된 회차는 취소할 수 없습니다)" }),
  ).toBeDisabled();
});

it("대기자의 취소 확인 문구는 대기 신청 취소를 안내한다", () => {
  givenCourse(course());
  givenMine([{ id: 2, sessionId: 10, status: "WAITING", waitingNumber: 1 }]);

  renderAt("/courses/3", true);
  fireEvent.click(screen.getByRole("button", { name: "취소" }));

  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("대기 신청을 취소할까요?")).toBeInTheDocument();
  expect(within(dialog).queryByText(/대기 1순위/)).not.toBeInTheDocument();
});

it("취소가 실패하면 대화 상자 안에 오류를 보이고 다시 불러온다", async () => {
  givenCourse(course());
  givenMine([{ id: 1, sessionId: 10, status: "CONFIRMED" }]);
  actions = { ...actions, error: "이미 시작된 회차입니다." };
  vi.mocked(actions.cancel).mockResolvedValue(null);

  renderAt("/courses/3", true);
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("이미 시작된 회차입니다.")).toBeInTheDocument();

  fireEvent.click(within(dialog).getByRole("button", { name: "신청 취소" }));

  await waitFor(() => expect(reloadCourse).toHaveBeenCalled());
  expect(reloadMine).toHaveBeenCalled();
  expect(screen.getByRole("dialog")).toBeInTheDocument();
});

it("submitting이면 신청 버튼을 끈다", () => {
  givenCourse(course());
  actions = { ...actions, submitting: true };

  renderAt("/courses/3", true);

  expect(screen.getByRole("button", { name: "신청" })).toBeDisabled();
});

it("내 신청 조회가 실패하면 알리고 신청을 끈다", () => {
  givenCourse(course());
  mockedUseMyEnrollments.mockReturnValue({
    data: { content: [], page: 0, size: 100, totalElements: 0 },
    loading: false,
    error: new Error("boom"),
    reload: reloadMine,
  });

  renderAt("/courses/3", true);

  expect(screen.getByRole("alert")).toHaveTextContent("내 신청 내역을 불러오지 못했습니다");
  expect(
    screen.getByRole("button", { name: "신청 (내 신청 내역을 불러오지 못했습니다)" }),
  ).toBeDisabled();
});

it("내 신청을 받아 오기 전에는 신청을 끈다", () => {
  givenCourse(course());
  mockedUseMyEnrollments.mockReturnValue({
    data: null,
    loading: true,
    error: null,
    reload: reloadMine,
  });

  renderAt("/courses/3", true);

  expect(
    screen.getByRole("button", { name: "신청 (내 신청 내역을 확인하는 중입니다)" }),
  ).toBeDisabled();
});

it("받아 오는 중에는 취소도 끈다", () => {
  givenCourse(course());
  mockedUseMyEnrollments.mockReturnValue({
    data: {
      content: [{ id: 1, sessionId: 10, status: "CONFIRMED" }],
      page: 0,
      size: 100,
      totalElements: 1,
    },
    loading: true,
    error: null,
    reload: reloadMine,
  });

  renderAt("/courses/3", true);

  expect(screen.getByRole("button", { name: /^취소/ })).toBeDisabled();
});

it("신청이 실패해도 다시 불러온다", async () => {
  givenCourse(course());
  vi.mocked(actions.enroll).mockResolvedValue(null);

  renderAt("/courses/3", true);
  fireEvent.click(screen.getByRole("button", { name: "신청" }));

  await waitFor(() => expect(reloadCourse).toHaveBeenCalled());
  expect(reloadMine).toHaveBeenCalled();
});

it("내 신청 조회 실패 알림의 '다시 시도'는 다시 불러온다", () => {
  givenCourse(course());
  mockedUseMyEnrollments.mockReturnValue({
    data: null,
    loading: false,
    error: new Error("boom"),
    reload: reloadMine,
  });

  renderAt("/courses/3", true);
  fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

  expect(reloadMine).toHaveBeenCalledTimes(1);
});
