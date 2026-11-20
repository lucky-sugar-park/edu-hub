import { fireEvent, screen, waitFor } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { authApi } from "../api/clients";
import { renderWithProviders } from "../test-utils";
import { LoginPage } from "./LoginPage";

vi.mock("../api/clients", () => ({ authApi: { login: vi.fn() } }));
vi.mock("../api/http", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../api/http")>()),
  setAuthHooks: vi.fn(),
}));

const login = vi.mocked(authApi.login);

function renderLogin(state?: unknown) {
  renderWithProviders(
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/me/enrollments" element={<div>내 신청 화면</div>} />
      <Route path="/" element={<div>홈 화면</div>} />
    </Routes>,
    { route: { pathname: "/login", state } },
  );
}

function submit(employeeId: string) {
  fireEvent.change(screen.getByLabelText("사번"), { target: { value: employeeId } });
  fireEvent.click(screen.getByRole("button", { name: "로그인" }));
}

function httpError(status: number, message?: string) {
  return Object.assign(new Error("failed"), {
    isAxiosError: true,
    response: { status, data: message ? { message } : {} },
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  login.mockResolvedValue({
    data: {
      accessToken: "jwt-value",
      tokenType: "Bearer",
      expiresAt: "2099-01-01T00:00:00Z",
      employeeId: "E1001",
      roles: ["EMPLOYEE"],
    },
  } as Awaited<ReturnType<typeof authApi.login>>);
});

it("개발 환경 전용 로그인임을 표시한다", () => {
  renderLogin();

  expect(screen.getByText("개발 환경 전용 로그인(비밀번호 없음)")).toBeInTheDocument();
});

it("성공하면 state.from 경로로 이동한다", async () => {
  renderLogin({ from: "/me/enrollments" });

  submit("E1001");

  expect(await screen.findByText("내 신청 화면")).toBeInTheDocument();
  expect(login).toHaveBeenCalledWith({ employeeId: "E1001" });
});

it("돌아올 경로가 없으면 /로 이동한다", async () => {
  renderLogin();

  submit("E1001");

  expect(await screen.findByText("홈 화면")).toBeInTheDocument();
});

it("앱 밖으로 가는 from은 무시한다", async () => {
  renderLogin({ from: "//evil.example" });

  submit("E1001");

  expect(await screen.findByText("홈 화면")).toBeInTheDocument();
});

it("404면 SSO 안내를 보여 준다", async () => {
  login.mockRejectedValue(httpError(404));
  renderLogin();

  submit("E1001");

  expect(
    await screen.findByText("이 환경에서는 사내 SSO 연동 전까지 로그인할 수 없습니다"),
  ).toBeInTheDocument();
});

it("그 밖의 오류는 서버 메시지를 보여 준다", async () => {
  login.mockRejectedValue(httpError(400, "사번은 20자 이하여야 합니다."));
  renderLogin();

  submit("E1001");

  expect(await screen.findByText("사번은 20자 이하여야 합니다.")).toBeInTheDocument();
  await waitFor(() => expect(screen.getByRole("button", { name: "로그인" })).toBeEnabled());
});

it("사번이 비어 있으면 요청하지 않는다", () => {
  renderLogin();

  submit("  ");

  expect(screen.getByText("사번을 입력하세요.")).toBeInTheDocument();
  expect(login).not.toHaveBeenCalled();
});

it("백슬래시가 든 from은 무시한다", async () => {
  renderLogin({ from: "/" + String.fromCharCode(92) + "evil.com" });

  submit("E1001");

  expect(await screen.findByText("홈 화면")).toBeInTheDocument();
});

it("from이 /login이면 /로 보낸다", async () => {
  renderLogin({ from: "/login" });

  submit("E1001");

  expect(await screen.findByText("홈 화면")).toBeInTheDocument();
});

it("from이 /login/(후행 슬래시)여도 /로 보낸다", async () => {
  renderLogin({ from: "/login/" });

  submit("E1001");

  expect(await screen.findByText("홈 화면")).toBeInTheDocument();
});
