import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useLocation, useNavigate } from "react-router-dom";
import {
  authApi,
  clearAuth,
  isExpired,
  loadAuth,
  type StoredAuth,
  saveAuth,
  setAuthHooks,
} from "@eduhub/api-client";

export interface AuthContextValue {
  token: string | null;
  employeeId: string | null;
  roles: string[];
  expiresAt: string | null;
  isAdmin: boolean;
  /**
   * 사번·비밀번호로 로그인한다(D-33). 실패하면 예외를 그대로 던진다(화면이 메시지로 바꾼다).
   * 비밀번호는 요청에만 쓰고 어디에도 저장하지 않는다.
   */
  login: (employeeNo: string, password: string) => Promise<void>;
  logout: () => void;
}

const NO_HOOKS = { getToken: () => null, clearToken: () => {}, onUnauthorized: () => {} };

const AuthContext = createContext<AuthContextValue | null>(null);

/** 로그인이 필요한 화면 경로(/me/**, /admin/**). 401 때 이 경로에서만 로그인 화면으로 보낸다. */
export function isProtectedPath(pathname: string): boolean {
  return /^\/(me|admin)(\/|$)/.test(pathname);
}

/** 라우터 안에 둔다(401 때 이동에 navigate를 쓴다). */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<StoredAuth | null>(() => loadAuth());
  const navigate = useNavigate();
  const location = useLocation();

  // 인터셉터가 최신 값을 읽도록 ref에 둔다.
  const authRef = useRef(auth);
  authRef.current = auth;
  const locationRef = useRef(location);
  locationRef.current = location;

  const logout = useCallback(() => {
    clearAuth();
    authRef.current = null;
    setAuth(null);
  }, []);

  const login = useCallback(async (employeeNo: string, password: string) => {
    const requestedAt = Date.now();
    const { data } = await authApi.login({ employeeNo, password });
    // 응답의 만료는 초 단위 길이다. 요청을 보낸 시각을 기준으로 잡아, 응답이 늦어도 실제 만료보다 늦게 잡지 않는다.
    const next: StoredAuth = {
      token: data.accessToken,
      employeeId: data.user.employeeNo,
      roles: [data.user.role],
      expiresAt: new Date(requestedAt + data.expiresIn * 1000).toISOString(),
    };
    saveAuth(next);
    authRef.current = next;
    setAuth(next);
  }, []);

  // 자식의 첫 요청(useEffect)보다 먼저 등록되도록 레이아웃 효과로 둔다.
  useLayoutEffect(() => {
    setAuthHooks({
      getToken: () => {
        const current = authRef.current;
        if (!current) return null;
        if (isExpired(current.expiresAt)) {
          logout();
          return null;
        }
        return current.token;
      },
      clearToken: logout,
      onUnauthorized: () => {
        const { pathname, search, hash } = locationRef.current;
        if (isProtectedPath(pathname)) {
          navigate("/login", { replace: true, state: { from: `${pathname}${search}${hash}` } });
        }
      },
    });
    return () => setAuthHooks(NO_HOOKS);
  }, [logout, navigate]);

  const value = useMemo<AuthContextValue>(
    () => ({
      token: auth?.token ?? null,
      employeeId: auth?.employeeId ?? null,
      roles: auth?.roles ?? [],
      expiresAt: auth?.expiresAt ?? null,
      isAdmin: auth?.roles.includes("ADMIN") ?? false,
      login,
      logout,
    }),
    [auth, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth는 AuthProvider 안에서만 쓸 수 있습니다.");
  return value;
}
