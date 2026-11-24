// 사용자가 국내 임직원이므로 PC 시간대와 상관없이 한국 시간으로 보여 준다.
const DATE_TIME_FORMAT = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "numeric",
  day: "numeric",
  weekday: "short",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

/** API의 UTC Instant(ISO 8601 문자열)를 "2026. 12. 1. (화) 14:00" 형식의 한국 시간으로 바꾼다. */
export function formatDateTime(instant: string | undefined): string {
  if (!instant) return "-";
  const date = new Date(instant);
  if (Number.isNaN(date.getTime())) return instant;
  const parts = Object.fromEntries(
    DATE_TIME_FORMAT.formatToParts(date).map((part) => [part.type, part.value]),
  );
  return `${parts.year}. ${parts.month}. ${parts.day}. (${parts.weekday}) ${parts.hour}:${parts.minute}`;
}
