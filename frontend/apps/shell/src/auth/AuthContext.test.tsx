import { fireEvent, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { authApi } from "@eduhub/api-client";
import { fakeAuth, renderWithProviders } from "../test-utils";
import { useAuth } from "./AuthContext";

vi.mock("@eduhub/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@eduhub/api-client")>()),
  authApi: { login: vi.fn() },
  setAuthHooks: vi.fn(),
}));

const login = vi.mocked(authApi.login);

function Probe() {
  const auth = useAuth();
  return (
    <div>
      <span data-testid="state">
        {auth.token ? `${auth.employeeId}|${auth.roles.join(",")}|${auth.isAdmin}` : "anonymous"}
      </span>
      <button type="button" onClick={() => void auth.login("E2002")}>
        로그인
      </button>
      <button type="button" onClick={auth.logout}>
        로그아웃
      </button>
    </div>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  login.mockResolvedValue({
    data: {
      accessToken: "jwt-value",
      tokenType: "Bearer",
      expiresAt: "2099-01-01T00:00:00Z",
      employeeId: "E2002",
      roles: ["EMPLOYEE", "ADMIN"],
    },
  } as Awaited<ReturnType<typeof authApi.login>>);
});

it("로그인하면 상태와 저장소에 담긴다", async () => {
  renderWithProviders(<Probe />);
  expect(screen.getByTestId("state")).toHaveTextContent("anonymous");

  fireEvent.click(screen.getByRole("button", { name: "로그인" }));

  await waitFor(() =>
    expect(screen.getByTestId("state")).toHaveTextContent("E2002|EMPLOYEE,ADMIN|true"),
  );
  expect(login).toHaveBeenCalledWith({ employeeId: "E2002" });
  expect(JSON.parse(sessionStorage.getItem("eduhub.auth") ?? "{}")).toMatchObject({
    token: "jwt-value",
    employeeId: "E2002",
  });
});

it("저장소에 있는 로그인 상태를 복원한다", () => {
  renderWithProviders(<Probe />, { auth: fakeAuth(["EMPLOYEE"], "E1001") });

  expect(screen.getByTestId("state")).toHaveTextContent("E1001|EMPLOYEE|false");
});

it("만료된 저장 값은 무시한다", () => {
  renderWithProviders(<Probe />, {
    auth: { ...fakeAuth(), expiresAt: "2020-01-01T00:00:00Z" },
  });

  expect(screen.getByTestId("state")).toHaveTextContent("anonymous");
});

it("로그아웃하면 상태와 저장소를 지운다", () => {
  renderWithProviders(<Probe />, { auth: fakeAuth() });

  fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));

  expect(screen.getByTestId("state")).toHaveTextContent("anonymous");
  expect(sessionStorage.getItem("eduhub.auth")).toBeNull();
});
