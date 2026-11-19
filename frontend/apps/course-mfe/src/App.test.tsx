import { fireEvent, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { App } from "./App";
import { fakeAuth, renderWithProviders } from "./test-utils";

vi.mock("./api/courseApi", () => ({
  devAuthApi: { login: vi.fn() },
  setAuthHooks: vi.fn(),
}));
vi.mock("./pages/CourseListPage", () => ({ CourseListPage: () => <div>과정 목록 화면</div> }));
vi.mock("./pages/CourseDetailPage", () => ({ CourseDetailPage: () => <div>과정 상세 화면</div> }));
vi.mock("./pages/AdminCoursePage", () => ({ AdminCoursePage: () => <div>과정 관리 화면</div> }));

const admin = () => fakeAuth(["EMPLOYEE", "ADMIN"], "E9001");

it("비로그인으로 /me/enrollments를 열면 로그인 화면으로 보낸다", () => {
  renderWithProviders(<App />, { route: "/me/enrollments" });

  expect(screen.getByText("개발 환경 전용 로그인(비밀번호 없음)")).toBeInTheDocument();
});

it("비로그인으로 /admin을 열면 로그인 화면으로 보낸다", () => {
  renderWithProviders(<App />, { route: "/admin" });

  expect(screen.getByText("개발 환경 전용 로그인(비밀번호 없음)")).toBeInTheDocument();
  expect(screen.queryByText("과정 관리 화면")).not.toBeInTheDocument();
});

it("일반 사원이 /admin을 열면 권한 안내를 보여 준다", () => {
  renderWithProviders(<App />, { route: "/admin", auth: fakeAuth() });

  expect(screen.getByText("관리자만 볼 수 있습니다.")).toBeInTheDocument();
  expect(screen.queryByText("과정 관리 화면")).not.toBeInTheDocument();
});

it("일반 사원이 명단 경로를 열어도 권한 안내를 보여 준다", () => {
  renderWithProviders(<App />, { route: "/admin/sessions/10/roster", auth: fakeAuth() });

  expect(screen.getByText("관리자만 볼 수 있습니다.")).toBeInTheDocument();
});

it("관리자는 /admin과 명단 경로를 볼 수 있다", () => {
  renderWithProviders(<App />, { route: "/admin", auth: admin() });
  expect(screen.getByText("과정 관리 화면")).toBeInTheDocument();
});

it("로그인한 사원은 /me/enrollments를 볼 수 있다", () => {
  renderWithProviders(<App />, { route: "/me/enrollments", auth: fakeAuth() });

  expect(screen.getByRole("heading", { name: "내 신청 내역" })).toBeInTheDocument();
});

it("상단 바: 비로그인에는 로그인 버튼만 있다", () => {
  renderWithProviders(<App />);

  expect(screen.getByRole("link", { name: "과정 목록" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "로그인" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "내 신청" })).not.toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "과정 관리" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "로그아웃" })).not.toBeInTheDocument();
});

it("상단 바: 일반 사원은 내 신청과 사번, 로그아웃이 보이고 과정 관리는 숨는다", () => {
  renderWithProviders(<App />, { auth: fakeAuth() });

  expect(screen.getByRole("link", { name: "내 신청" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "과정 관리" })).not.toBeInTheDocument();
  expect(screen.getByText("E1001")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "로그아웃" })).toBeInTheDocument();
});

it("상단 바: 관리자는 과정 관리도 보인다", () => {
  renderWithProviders(<App />, { auth: admin() });

  expect(screen.getByRole("link", { name: "내 신청" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "과정 관리" })).toBeInTheDocument();
});

it("로그아웃하면 홈으로 가고 로그인 버튼이 나타난다", () => {
  renderWithProviders(<App />, { route: "/me/enrollments", auth: fakeAuth() });

  fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));

  expect(screen.getByText("과정 목록 화면")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "로그인" })).toBeInTheDocument();
  expect(sessionStorage.getItem("eduhub.auth")).toBeNull();
});
