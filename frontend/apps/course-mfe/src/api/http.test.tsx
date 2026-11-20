import { screen, waitFor } from "@testing-library/react";
import { useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fakeAuth, renderWithProviders } from "../test-utils";
import {
  clearAuth,
  httpClient,
  isExpired,
  loadAuth,
  type StoredAuth,
  saveAuth,
  setAuthHooks,
} from "./http";

type Adapter = NonNullable<typeof httpClient.defaults.adapter>;
type AdapterConfig = Parameters<Extract<Adapter, (...args: never[]) => unknown>>[0];

interface Call {
  url: string;
  authorization: string | null;
}

let calls: Call[];
/** 호출 순서대로 돌려줄 상태 코드. 다 쓰면 200. */
let statuses: number[];
const originalAdapter = httpClient.defaults.adapter;

beforeEach(() => {
  calls = [];
  statuses = [];
  httpClient.defaults.adapter = async (config: AdapterConfig) => {
    calls.push({
      url: config.url ?? "",
      authorization: (config.headers.get("Authorization") as string | undefined) ?? null,
    });
    const status = statuses.shift() ?? 200;
    const response = { data: {}, status, statusText: "", headers: {}, config };
    if (status >= 400) throw Object.assign(new Error(`HTTP ${status}`), { response, config });
    return response;
  };
});

afterEach(() => {
  httpClient.defaults.adapter = originalAdapter;
  setAuthHooks({ getToken: () => null, clearToken: () => {}, onUnauthorized: () => {} });
});

function Where() {
  const location = useLocation();
  return <div data-testid="where">{`${location.pathname}|${JSON.stringify(location.state)}`}</div>;
}

const stored = () => sessionStorage.getItem("eduhub.auth");

it("토큰이 있으면 Authorization 헤더를 붙인다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth() });

  await httpClient.get("/api/v1/me/enrollments");

  expect(calls[0].authorization).toBe("Bearer test-token");
});

it("토큰이 없으면 헤더를 붙이지 않는다", async () => {
  renderWithProviders(<Where />);

  await httpClient.get("/api/v1/courses");

  expect(calls[0].authorization).toBeNull();
});

it("만료된 토큰은 붙이지 않고 버린다", async () => {
  vi.useFakeTimers({ toFake: ["Date"] });
  try {
    vi.setSystemTime(new Date("2098-12-31T23:59:59Z"));
    renderWithProviders(<Where />, { auth: fakeAuth() });
    vi.setSystemTime(new Date("2099-01-02T00:00:00Z"));

    await httpClient.get("/api/v1/me/enrollments");

    expect(calls[0].authorization).toBeNull();
    expect(stored()).toBeNull();
  } finally {
    vi.useRealTimers();
  }
});

it("401이면 토큰을 지운다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth() });
  statuses = [401];

  await expect(httpClient.get("/api/v1/me/enrollments")).rejects.toBeTruthy();

  expect(stored()).toBeNull();
});

it("토큰을 붙인 공개 GET이 401이면 토큰 없이 한 번 다시 요청한다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth(), route: "/courses/3" });
  statuses = [401, 200];

  await httpClient.get("/api/v1/courses/3");

  expect(calls).toHaveLength(2);
  expect(calls[0].authorization).toBe("Bearer test-token");
  expect(calls[1].authorization).toBeNull();
  expect(stored()).toBeNull();
});

it("공개 GET 경로(목록, 상세, 헬스)는 모두 재시도한다", async () => {
  for (const url of ["/api/v1/courses?keyword=a", "/api/v1/courses/7", "/api/health"]) {
    calls = [];
    statuses = [401, 200];
    renderWithProviders(<Where />, { auth: fakeAuth() });

    await httpClient.get(url);

    expect(calls).toHaveLength(2);
  }
});

it("재시도도 401이면 더 반복하지 않는다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth() });
  statuses = [401, 401];

  await expect(httpClient.get("/api/v1/courses")).rejects.toBeTruthy();

  expect(calls).toHaveLength(2);
});

it("보호 화면에서 401이면 돌아올 경로와 함께 로그인으로 보낸다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth(), route: "/me/enrollments?page=1" });
  statuses = [401];

  await expect(httpClient.get("/api/v1/me/enrollments")).rejects.toBeTruthy();

  await waitFor(() =>
    expect(screen.getByTestId("where")).toHaveTextContent(
      '/login|{"from":"/me/enrollments?page=1"}',
    ),
  );
  expect(calls).toHaveLength(1);
});

it("공개 화면에서 401이면 이동하지 않는다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth(), route: "/courses/3" });
  statuses = [401];

  await expect(httpClient.post("/api/v1/sessions/10/enrollments")).rejects.toBeTruthy();

  expect(screen.getByTestId("where")).toHaveTextContent("/courses/3|null");
  expect(stored()).toBeNull();
});

it("로그인 요청의 401은 토큰을 지우거나 이동하지 않는다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth(), route: "/admin" });
  statuses = [401];

  await expect(httpClient.post("/api/v1/dev/login", {})).rejects.toBeTruthy();

  expect(screen.getByTestId("where")).toHaveTextContent("/admin|null");
  expect(stored()).not.toBeNull();
  expect(calls).toHaveLength(1);
});

it("로그인 요청에는 기존 토큰을 붙이지 않는다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth() });

  await httpClient.post("/api/v1/dev/login", {});

  expect(calls[0].authorization).toBeNull();
});

it("요청을 보낸 뒤 토큰이 바뀌면 늦게 온 401이 새 토큰을 지우지 않는다", async () => {
  let current: string | null = "old";
  const clearToken = vi.fn();
  const onUnauthorized = vi.fn();
  setAuthHooks({ getToken: () => current, clearToken, onUnauthorized });
  httpClient.defaults.adapter = async (config: AdapterConfig) => {
    current = "new"; // 요청 중에 다시 로그인했다.
    const response = { data: {}, status: 401, statusText: "", headers: {}, config };
    throw Object.assign(new Error("HTTP 401"), { response, config });
  };

  await expect(httpClient.get("/api/v1/me/enrollments")).rejects.toBeTruthy();

  expect(clearToken).not.toHaveBeenCalled();
  expect(onUnauthorized).not.toHaveBeenCalled();
});

it("토큰이 같으면 clearToken과 onUnauthorized를 부른다", async () => {
  const clearToken = vi.fn();
  const onUnauthorized = vi.fn();
  setAuthHooks({ getToken: () => "same", clearToken, onUnauthorized });
  statuses = [401];

  await expect(httpClient.get("/api/v1/me/enrollments")).rejects.toBeTruthy();

  expect(clearToken).toHaveBeenCalledTimes(1);
  expect(onUnauthorized).toHaveBeenCalledTimes(1);
});

it("공개 경로와 이름이 비슷한 /api/v1/courses-admin GET 401은 재시도하지 않는다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth() });
  statuses = [401];

  await expect(httpClient.get("/api/v1/courses-admin")).rejects.toBeTruthy();

  expect(calls).toHaveLength(1);
});

it("공개 경로라도 POST 401은 재시도하지 않는다", async () => {
  renderWithProviders(<Where />, { auth: fakeAuth() });
  statuses = [401];

  await expect(httpClient.post("/api/v1/courses")).rejects.toBeTruthy();

  expect(calls).toHaveLength(1);
});

/** 요청 도중 토큰이 "new"로 바뀌고, 지정한 상태 코드를 차례로 돌려주는 어댑터. 마지막 값은 계속 쓴다. */
function swapTokenDuringRequest(codes: number[]) {
  let current: string | null = "old";
  const clearToken = vi.fn();
  const onUnauthorized = vi.fn();
  setAuthHooks({ getToken: () => current, clearToken, onUnauthorized });
  httpClient.defaults.adapter = async (config: AdapterConfig) => {
    calls.push({
      url: config.url ?? "",
      authorization: (config.headers.get("Authorization") as string | undefined) ?? null,
    });
    current = "new";
    const status = codes.length > 1 ? (codes.shift() as number) : codes[0];
    const response = { data: {}, status, statusText: "", headers: {}, config };
    if (status >= 400) throw Object.assign(new Error(`HTTP ${status}`), { response, config });
    return response;
  };
  return { clearToken, onUnauthorized };
}

it("공개 GET 도중 토큰이 바뀌면 새 토큰으로 재시도하고 지우지 않는다", async () => {
  const { clearToken } = swapTokenDuringRequest([401, 200]);

  await httpClient.get("/api/v1/courses/3");

  expect(calls).toHaveLength(2);
  expect(calls[0].authorization).toBe("Bearer old");
  expect(calls[1].authorization).toBe("Bearer new");
  expect(clearToken).not.toHaveBeenCalled();
});

it("재시도가 계속 401이어도 한 번만 다시 요청한다", async () => {
  swapTokenDuringRequest([401]);

  await expect(httpClient.get("/api/v1/courses/3")).rejects.toBeTruthy();

  expect(calls).toHaveLength(2);
});

describe("authStorage(로그인 상태 보관)", () => {
  const valid: StoredAuth = {
    token: "t",
    employeeId: "E1001",
    roles: ["EMPLOYEE"],
    expiresAt: "2099-01-01T00:00:00Z",
  };

  beforeEach(() => {
    sessionStorage.clear();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("저장한 값을 읽는다", () => {
    saveAuth(valid);

    expect(loadAuth()).toEqual(valid);
    expect(sessionStorage.getItem("eduhub.auth")).not.toBeNull();
  });

  it("만료된 값은 버린다", () => {
    saveAuth({ ...valid, expiresAt: "2020-01-01T00:00:00Z" });

    expect(loadAuth()).toBeNull();
    expect(sessionStorage.getItem("eduhub.auth")).toBeNull();
  });

  it("깨진 값은 null이다", () => {
    sessionStorage.setItem("eduhub.auth", "{not json");
    expect(loadAuth()).toBeNull();

    sessionStorage.setItem("eduhub.auth", JSON.stringify({ token: 1 }));
    expect(loadAuth()).toBeNull();
  });

  it("삭제하면 읽히지 않는다", () => {
    saveAuth(valid);
    clearAuth();

    expect(loadAuth()).toBeNull();
  });

  it("저장소를 쓸 수 없어도 예외를 던지지 않는다", () => {
    const denied = () => {
      throw new Error("denied");
    };
    vi.stubGlobal("sessionStorage", { getItem: denied, setItem: denied, removeItem: denied });

    expect(() => saveAuth(valid)).not.toThrow();
    expect(loadAuth()).toBeNull();
    expect(() => clearAuth()).not.toThrow();
  });

  it("해석할 수 없는 만료 시각은 만료로 본다", () => {
    expect(isExpired("nope")).toBe(true);
    expect(isExpired("2099-01-01T00:00:00Z")).toBe(false);
  });
});
