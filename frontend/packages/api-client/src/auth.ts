import axios, { type InternalAxiosRequestConfig } from "axios";
import { Configuration } from "./generated";

/**
 * HTTP 기반: 로그인 상태(sessionStorage) 보관, 토큰 붙이기, 401 처리를 한 곳에 둔다.
 * 모든 API 클래스는 여기의 httpClient 하나로 만든다(앱의 api/courseApi.ts, api/clients.ts).
 */

// ---- 로그인 상태 보관(sessionStorage, NFR-03). 저장소를 쓸 수 없으면 조용히 건너뛴다. ----

const KEY = "eduhub.auth";

export interface StoredAuth {
  token: string;
  employeeId: string;
  roles: string[];
  /** ISO-8601 UTC 시각 */
  expiresAt: string;
}

/** 만료 시각이 지났거나 해석할 수 없으면 true. */
export function isExpired(expiresAt: string, now: number = Date.now()): boolean {
  const time = Date.parse(expiresAt);
  return Number.isNaN(time) || time <= now;
}

function isStoredAuth(value: unknown): value is StoredAuth {
  const v = value as Partial<StoredAuth> | null;
  return (
    typeof v === "object" &&
    v !== null &&
    typeof v.token === "string" &&
    v.token !== "" &&
    typeof v.employeeId === "string" &&
    typeof v.expiresAt === "string" &&
    Array.isArray(v.roles) &&
    v.roles.every((role) => typeof role === "string")
  );
}

/** 저장된 값을 읽는다. 없거나 깨졌거나 만료됐으면 null(만료·손상 값은 지운다). */
export function loadAuth(): StoredAuth | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (raw === null) return null;
    const parsed: unknown = JSON.parse(raw);
    if (isStoredAuth(parsed) && !isExpired(parsed.expiresAt)) return parsed;
    sessionStorage.removeItem(KEY);
  } catch {
    // 저장소 접근 실패 또는 JSON 손상: 저장된 값이 없는 것으로 본다.
  }
  return null;
}

export function saveAuth(auth: StoredAuth): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(auth));
  } catch {
    // 저장소를 쓸 수 없으면 메모리(AuthProvider 상태)에만 둔다.
  }
}

export function clearAuth(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // 무시
  }
}

// ---- 토큰 붙이기와 401 처리 ----

/**
 * 인증 상태(AuthProvider)가 주입하는 동작. 모듈 간 순환 참조를 피하려고 훅으로 받는다.
 * - getToken: 만료되지 않은 토큰만 돌려준다(없거나 만료면 null).
 * - clearToken: 401을 받았을 때 토큰을 지운다(로그아웃 상태로 만든다).
 * - onUnauthorized: 재시도할 수 없는 401일 때 부른다. 보호 화면에서만 로그인 화면으로 보낸다.
 */
export interface AuthHooks {
  getToken: () => string | null;
  clearToken: () => void;
  onUnauthorized: () => void;
}

let hooks: AuthHooks = { getToken: () => null, clearToken: () => {}, onUnauthorized: () => {} };

export function setAuthHooks(next: AuthHooks): void {
  hooks = next;
}

const LOGIN_PATH = "/api/v1/dev/login";

/** 로그인하지 않아도 보는 공개 GET 경로(백엔드 SecurityConfig와 같은 범위). */
function isPublicGet(config: InternalAxiosRequestConfig): boolean {
  if ((config.method ?? "get").toLowerCase() !== "get") return false;
  const path = (config.url ?? "").split("?")[0];
  return (
    path === "/api/v1/courses" || path.startsWith("/api/v1/courses/") || path === "/api/health"
  );
}

function isLoginRequest(config: InternalAxiosRequestConfig): boolean {
  return (config.url ?? "").split("?")[0] === LOGIN_PATH;
}

/** 요청을 보낼 때 붙인 토큰을 config에 남긴다. 늦게 온 401이 새 세션을 지우지 않게 비교하는 데 쓴다. */
interface TrackedConfig extends InternalAxiosRequestConfig {
  sentToken?: string;
  /** 토큰 없이(또는 바뀐 토큰으로) 다시 보낸 요청. 재시도는 한 번만 한다. */
  retried?: boolean;
}

/** 모든 API 클래스가 공유하는 Axios 인스턴스(인터셉터는 여기에만 둔다). */
export const httpClient = axios.create();

httpClient.interceptors.request.use((config: TrackedConfig) => {
  // 로그인 요청에는 토큰을 붙이지 않는다. 무효 토큰이 남아 있으면 백엔드가 401을 내 로그인이 막힌다.
  if (isLoginRequest(config)) return config;
  const token = hooks.getToken();
  config.sentToken = token ?? undefined;
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
});

httpClient.interceptors.response.use(undefined, async (error: unknown) => {
  const response = (error as { response?: { status?: number } } | null)?.response;
  const config = (error as { config?: TrackedConfig } | null)?.config;
  if (response?.status !== 401 || !config || isLoginRequest(config)) throw error;

  const sent = config.sentToken ?? null;
  // 요청을 보낸 뒤 로그인 상태가 바뀌었으면(늦게 온 401) 새 세션을 건드리지 않는다.
  const stale = sent !== hooks.getToken();
  // 백엔드는 공개 경로라도 무효 토큰이 붙으면 401을 낸다. 토큰은 항상 지운다.
  if (!stale && sent !== null) hooks.clearToken();

  if (sent !== null && !config.retried && isPublicGet(config)) {
    // 토큰 없이(또는 바뀐 지금 토큰으로) 한 번 다시 요청한다. 요청 인터셉터가 헤더를 다시 정한다.
    config.headers.delete("Authorization");
    return httpClient.request({ ...config, retried: true } as TrackedConfig);
  }
  if (!stale) hooks.onUnauthorized();
  throw error;
});

// 같은 출처로 요청한다. 개발 서버에서는 Vite 프록시가 백엔드로 넘긴다.
export const apiConfiguration = new Configuration({ basePath: "" });
