export {
  Configuration,
  CourseAdminApi,
  CourseApi,
  DevAuthApi,
  EnrollmentAdminApi,
  EnrollmentApi,
  HealthApi,
} from "./generated";
export type {
  CompletionRequest,
  CompletionResponse,
  ConfirmedEntry,
  CourseDetailResponse,
  CourseRequest,
  CourseSummaryResponse,
  EnrollmentResponse,
  ErrorResponse,
  LoginRequest,
  LoginResponse,
  MyEnrollmentResponse,
  PageResponseCourseSummaryResponse,
  PageResponseMyEnrollmentResponse,
  SessionRequest,
  SessionResponse,
  SessionRosterResponse,
  WaitingEntry,
} from "./generated";
// 신청 상태·수료 실패 사유 값(T5~T7에서 쓴다).
export {
  ConfirmedEntryStatusEnum,
  EnrollmentResponseStatusEnum,
  FailureReasonEnum,
  MyEnrollmentResponseStatusEnum,
} from "./generated";
// 앱이 axios에 직접 의존하지 않게 여기서 내보낸다. 인스턴스 생성은 앱의 api/courseApi.ts 한 곳에서만 한다.
// 호출 실패의 HTTP 상태를 확인할 때 isAxiosError를 쓴다.
export { default as axios, isAxiosError } from "axios";
export type { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from "axios";
