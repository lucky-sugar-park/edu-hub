import type { ErrorResponse } from "@eduhub/api-client";

const FALLBACK_MESSAGE = "요청을 처리하지 못했습니다.";

/** 백엔드 ErrorResponse의 message를 꺼낸다. 응답이 없으면(네트워크 오류 등) 기본 문구를 쓴다. */
export function toErrorMessage(cause: unknown): string {
  const body = (cause as { response?: { data?: ErrorResponse } } | null)?.response?.data;
  return body?.message || FALLBACK_MESSAGE;
}
