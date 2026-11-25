import { fireEvent, screen, waitFor } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { authApi } from "@eduhub/api-client";
import { renderWithProviders } from "../test-utils";
import { LoginPage } from "./LoginPage";

vi.mock("@eduhub/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@eduhub/api-client")>()),
  authApi: { login: vi.fn() },
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

function submit(employeeNo: string, password = "pw-value") {
  fireEvent.change(screen.getByLabelText("사번"), { target: { value: employeeNo } });
  fireEvent.change(screen.getByLabelText("비밀번호"), { target: { value: password } });
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
      expiresIn: 3600,
      user: { id: 1, employeeNo: "E1001", role: "EMPLOYEE" },
    },
  } as Awaited<ReturnType<typeof authApi.login>>);
});

it("비밀번호 입력란은 가려진 입력이다", () => {
  renderLogin();

  expect(screen.getByLabelText("비밀번호")).toHaveAttribute("type", "password");
  expect(screen.queryByText(/비밀번호 없음/)).not.toBeInTheDocument();
});

it("성공하면 state.from 경로로 이동한다", async () => {
  renderLogin({ from: "/me/enrollments" });

  submit("E1001");

  expect(await screen.findByText("내 신청 화면")).toBeInTheDocument();
  expect(login).toHaveBeenCalledWith({ employeeNo: "E1001", password: "pw-value" });
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

it("401이면 서버의 고정 메시지를 보여 주고 비밀번호를 비운다", async () => {
  login.mockRejectedValue(httpError(401, "사번 또는 비밀번호가 올바르지 않습니다"));
  renderLogin();

  submit("E1001", "wrong-pw");

  expect(await screen.findByText("사번 또는 비밀번호가 올바르지 않습니다")).toBeInTheDocument();
  expect(screen.getByLabelText("비밀번호")).toHaveValue("");
  expect(screen.getByLabelText("사번")).toHaveValue("E1001");
  await waitFor(() => expect(screen.getByRole("button", { name: "로그인" })).toBeEnabled());
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

it("비밀번호가 비어 있으면 요청하지 않는다", () => {
  renderLogin();

  submit("E1001", "");

  expect(screen.getByText("비밀번호를 입력하세요.")).toBeInTheDocument();
  expect(login).not.toHaveBeenCalled();
});

it("비밀번호는 앞뒤 공백을 자르지 않고 그대로 보낸다", async () => {
  renderLogin();

  submit(" E1001 ", " pw ");

  await screen.findByText("홈 화면");
  expect(login).toHaveBeenCalledWith({ employeeNo: "E1001", password: " pw " });
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
