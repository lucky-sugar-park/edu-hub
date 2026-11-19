import {
  axios,
  Configuration,
  CourseAdminApi,
  CourseApi,
  DevAuthApi,
  EnrollmentAdminApi,
  EnrollmentApi,
  type InternalAxiosRequestConfig,
} from "@eduhub/api-client";

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

  if (sent !== null && isPublicGet(config)) {
    // 토큰 없이(또는 바뀐 지금 토큰으로) 한 번 다시 요청한다. 요청 인터셉터가 헤더를 다시 정한다.
    config.headers.delete("Authorization");
    return httpClient.request(config);
  }
  if (!stale) hooks.onUnauthorized();
  throw error;
});

// 같은 출처로 요청한다. 개발 서버에서는 Vite 프록시가 백엔드로 넘긴다.
const configuration = new Configuration({ basePath: "" });

export const courseApi = new CourseApi(configuration, undefined, httpClient);

/** 관리자 전용 API(FR-03). */
export const courseAdminApi = new CourseAdminApi(configuration, undefined, httpClient);

/** 신청·취소·내 신청 내역(FR-04~07). */
export const enrollmentApi = new EnrollmentApi(configuration, undefined, httpClient);

/** 관리자 명단·수료(FR-08). */
export const enrollmentAdminApi = new EnrollmentAdminApi(configuration, undefined, httpClient);

/** 개발 환경 전용 로그인(D-30). 운영에서는 404다. */
export const devAuthApi = new DevAuthApi(configuration, undefined, httpClient);
