package com.example.eduhub.enrollment;

/** 수료 처리에 실패한 사유(FR-08). */
public enum CompletionFailureReason {
  /** 그 회차의 활성 신청이 대기 상태다. */
  NOT_CONFIRMED,
  /** 이미 수료 처리됐다. */
  ALREADY_COMPLETED,
  /** 취소한 신청뿐이다. */
  CANCELLED,
  /** 그 회차에 신청한 적이 없다. */
  NOT_ENROLLED
}
