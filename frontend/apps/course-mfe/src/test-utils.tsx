import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./auth/AuthContext";
import { clearAuth, type StoredAuth, saveAuth } from "./auth/authStorage";

export interface RenderOptions {
  /** 시작 경로(기본 "/"). 돌아올 경로 같은 state가 필요하면 객체로 준다. */
  route?: string | { pathname: string; state?: unknown };
  /** 지정하면 ui를 이 경로 패턴의 Route 요소로 감싼다(useParams용). */
  path?: string;
  /** 로그인 상태로 시작한다(sessionStorage에 저장). 생략하면 비로그인. */
  auth?: StoredAuth | null;
}

/** 유효 시간이 한참 남은 테스트용 로그인 값. */
export function fakeAuth(roles: string[] = ["EMPLOYEE"], employeeId = "E1001"): StoredAuth {
  return { token: "test-token", employeeId, roles, expiresAt: "2099-01-01T00:00:00Z" };
}

/** MemoryRouter와 AuthProvider로 감싸 그린다. */
export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}) {
  const { route = "/", path, auth = null } = options;
  clearAuth();
  if (auth) saveAuth(auth);
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        {path ? (
          <Routes>
            <Route path={path} element={ui} />
          </Routes>
        ) : (
          ui
        )}
      </AuthProvider>
    </MemoryRouter>,
  );
}
