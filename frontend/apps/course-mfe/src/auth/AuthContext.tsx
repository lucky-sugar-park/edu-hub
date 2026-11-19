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
import { isAxiosError } from "@eduhub/api-client";
import { devAuthApi, setAuthHooks } from "../api/courseApi";
import { clearAuth, isExpired, loadAuth, type StoredAuth, saveAuth } from "./authStorage";

export interface AuthContextValue {
  token: string | null;
  employeeId: string | null;
  roles: string[];
  expiresAt: string | null;
  isAdmin: boolean;
  /** 사번으로 dev 로그인한다. 실패하면 예외를 그대로 던진다(화면이 메시지로 바꾼다). */
  login: (employeeId: string) => Promise<void>;
  logout: () => void;
}

/** dev 로그인 API가 없는 환경(404, 예: 운영)이다. 화면은 이 에러만 보고 SSO 안내를 띄운다. */
export class DevLoginUnavailableError extends Error {
  constructor() {
    super("dev login is unavailable");
    this.name = "DevLoginUnavailableError";
  }
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

  const login = useCallback(async (employeeId: string) => {
    let data: Awaited<ReturnType<typeof devAuthApi.login>>["data"];
    try {
      data = (await devAuthApi.login({ employeeId })).data;
    } catch (cause) {
      if (isAxiosError(cause) && cause.response?.status === 404) throw new DevLoginUnavailableError();
      throw cause;
    }
    const next: StoredAuth = {
      token: data.accessToken,
      employeeId: data.employeeId,
      roles: data.roles,
      expiresAt: data.expiresAt,
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
