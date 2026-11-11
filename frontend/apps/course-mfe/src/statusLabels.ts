// 과정과 회차가 같은 상태 값(OPEN, CLOSED)을 쓴다.
const STATUS_LABELS: Record<string, string> = { OPEN: "운영 중", CLOSED: "종료" };

export function statusLabel(status: string | undefined): string {
  if (!status) return "-";
  return STATUS_LABELS[status] ?? status;
}
