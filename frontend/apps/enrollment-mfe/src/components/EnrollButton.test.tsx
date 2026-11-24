import {
  type CourseDetailResponse,
  enrollmentApi,
  type MyEnrollmentResponse,
  type SessionResponse,
} from "@eduhub/api-client";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { fakeAuth, renderWithProviders } from "../test-utils";
import EnrollButton from "./EnrollButton";

vi.mock("@eduhub/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@eduhub/api-client")>()),
  enrollmentApi: { getMyEnrollments: vi.fn(), enroll: vi.fn(), cancel: vi.fn() },
}));

const api = vi.mocked(enrollmentApi);
type Response<T extends (...args: never[]) => unknown> = Awaited<ReturnType<T>>;

const course: CourseDetailResponse = { id: 3, title: "JPA 입문", status: "OPEN", sessions: [] };
const session: SessionResponse = {
  id: 10,
  startAt: "2099-12-01T05:00:00Z",
  place: "본사 3층",
  capacity: 30,
  status: "OPEN",
};

function myEnrollments(content: MyEnrollmentResponse[]) {
  api.getMyEnrollments.mockResolvedValue({
    data: { content, page: 0, size: 100, totalElements: content.length },
  } as Response<typeof api.getMyEnrollments>);
}

function renderButton(onChanged = vi.fn(), loggedIn = true) {
  renderWithProviders(<EnrollButton course={course} session={session} onChanged={onChanged} />, {
    auth: loggedIn ? fakeAuth() : null,
  });
  return onChanged;
}

beforeEach(() => {
  vi.resetAllMocks();
  myEnrollments([]);
});

it("비로그인이면 로그인 화면으로 가는 버튼을 보이고 내 신청을 조회하지 않는다", () => {
  renderButton(vi.fn(), false);

  const link = screen.getByRole("link", { name: "로그인 후 신청" });
  expect(link).toHaveAttribute("href", "/login");
  expect(api.getMyEnrollments).not.toHaveBeenCalled();
});

it("신청하면 결과를 알리고 onChanged와 내 신청 다시 불러오기를 부른다", async () => {
  api.enroll.mockResolvedValue({
    data: { id: 1, sessionId: 10, status: "WAITING", waitingNumber: 2 },
  } as Response<typeof api.enroll>);
  const onChanged = renderButton();

  const button = await screen.findByRole("button", { name: "신청" });
  await waitFor(() => expect(button).toBeEnabled());
  fireEvent.click(button);

  await waitFor(() => expect(api.enroll).toHaveBeenCalledWith(10));
  expect(await screen.findByText("대기 2번으로 신청되었습니다")).toBeInTheDocument();
  expect(onChanged).toHaveBeenCalledTimes(1);
  await waitFor(() => expect(api.getMyEnrollments).toHaveBeenCalledTimes(2));
});

it("확정된 회차는 상태를 보이고, 확인 대화 상자를 거쳐 취소한다", async () => {
  myEnrollments([{ id: 1, sessionId: 10, status: "CONFIRMED" }]);
  api.cancel.mockResolvedValue({
    data: { id: 1, sessionId: 10, status: "CANCELLED" },
  } as Response<typeof api.cancel>);
  const onChanged = renderButton();

  expect(await screen.findByText("확정")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "취소" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText(/대기 1순위가 확정됩니다/)).toBeInTheDocument();
  expect(api.cancel).not.toHaveBeenCalled();

  fireEvent.click(within(dialog).getByRole("button", { name: "신청 취소" }));

  await waitFor(() => expect(api.cancel).toHaveBeenCalledWith(10));
  expect(await screen.findByText("신청이 취소되었습니다")).toBeInTheDocument();
  expect(onChanged).toHaveBeenCalledTimes(1);
});

it("종료된 과정이면 신청 버튼이 꺼지고 사유를 알린다", async () => {
  renderWithProviders(<EnrollButton course={{ ...course, status: "CLOSED" }} session={session} />, {
    auth: fakeAuth(),
  });

  expect(await screen.findByRole("button", { name: "신청 (종료된 과정입니다)" })).toBeDisabled();
});

it("내 신청 내역을 불러오지 못하면 신청을 막는다", async () => {
  api.getMyEnrollments.mockRejectedValue(new Error("boom"));
  renderButton();

  expect(
    await screen.findByRole("button", { name: "신청 (내 신청 내역을 불러오지 못했습니다)" }),
  ).toBeDisabled();
});
