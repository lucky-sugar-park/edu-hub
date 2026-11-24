import { clearAuth, type StoredAuth, saveAuth } from "@eduhub/api-client";
import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";

export interface RenderOptions {
  /** 시작 경로(기본 "/"). */
  route?: string;
  /** 지정하면 ui를 이 경로 패턴의 Route 요소로 감싼다(useParams용). */
  path?: string;
  /** 로그인 상태로 시작한다(sessionStorage에 저장). 생략하면 비로그인. */
  auth?: StoredAuth | null;
}

/** 유효 시간이 한참 남은 테스트용 로그인 값. */
export function fakeAuth(roles: string[] = ["EMPLOYEE"], employeeId = "E1001"): StoredAuth {
  return { token: "test-token", employeeId, roles, expiresAt: "2099-01-01T00:00:00Z" };
}

/** 로그인 상태를 정한다. shell과 같은 저장소(@eduhub/api-client의 saveAuth)에 둔다. */
export function setLoggedIn(auth: StoredAuth | null) {
  clearAuth();
  if (auth) saveAuth(auth);
}

/**
 * MemoryRouter로 감싸 그린다. remote는 shell의 AuthProvider를 쓰지 않으므로
 * 로그인 상태는 저장소로만 만든다.
 */
export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}) {
  const { route = "/", path, auth = null } = options;
  setLoggedIn(auth);
  return render(
    <MemoryRouter initialEntries={[route]}>
      {path ? (
        <Routes>
          <Route path={path} element={ui} />
        </Routes>
      ) : (
        ui
      )}
    </MemoryRouter>,
  );
}
