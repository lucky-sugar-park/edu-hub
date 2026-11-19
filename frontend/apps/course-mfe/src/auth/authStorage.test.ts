import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { clearAuth, isExpired, loadAuth, saveAuth, type StoredAuth } from "./authStorage";

const valid: StoredAuth = {
  token: "t",
  employeeId: "E1001",
  roles: ["EMPLOYEE"],
  expiresAt: "2099-01-01T00:00:00Z",
};

beforeEach(() => sessionStorage.clear());
afterEach(() => vi.unstubAllGlobals());

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
