// 회차 시작 시각은 API에서 ISO-8601 Instant(UTC)로 오가고, 화면에서는 브라우저 로컬 시간으로 다룬다.

const pad = (value: number) => String(value).padStart(2, "0");

/** ISO 시각을 `<input type="datetime-local">` 값(YYYY-MM-DDTHH:mm, 로컬 시간)으로 바꾼다. */
export function toLocalInputValue(iso: string): string {
  const date = new Date(iso);
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/** `datetime-local` 값(로컬 시간)을 ISO 시각(UTC)으로 바꾼다. */
export function fromLocalInputValue(value: string): string {
  return new Date(value).toISOString();
}

/** 화면 표시용. 예: 2026. 12. 1. 오전 10:00 */
export function formatDateTime(iso: string | undefined): string {
  if (!iso) return "";
  return new Date(iso).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" });
}

/** 교육 시작 시각이 지났으면(같은 시각 포함) true. 백엔드 판단(D-08)과 같은 기준이다. */
export function hasStarted(iso: string | undefined, now: Date = new Date()): boolean {
  return iso !== undefined && new Date(iso).getTime() <= now.getTime();
}
