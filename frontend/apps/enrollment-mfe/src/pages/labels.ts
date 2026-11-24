const ENROLLMENT_STATUS_LABELS: Record<string, string> = {
  CONFIRMED: "확정",
  WAITING: "대기",
  CANCELLED: "취소",
  COMPLETED: "수료",
};

/** 신청 상태 이름(확정, 대기, 취소, 수료). */
export function enrollmentStatusLabel(status: string | undefined): string {
  if (!status) return "-";
  return ENROLLMENT_STATUS_LABELS[status] ?? status;
}

const FAILURE_REASON_LABELS: Record<string, string> = {
  NOT_CONFIRMED: "대기 중",
  ALREADY_COMPLETED: "이미 수료",
  CANCELLED: "취소됨",
  NOT_ENROLLED: "신청 없음",
};

/** 수료 처리 실패 사유 이름. */
export function failureReasonLabel(reason: string | undefined): string {
  if (!reason) return "-";
  return FAILURE_REASON_LABELS[reason] ?? reason;
}
