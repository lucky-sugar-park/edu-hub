/** 로그인 상태를 sessionStorage에 보관한다(NFR-03). 저장소를 쓸 수 없으면 조용히 건너뛴다. */
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
