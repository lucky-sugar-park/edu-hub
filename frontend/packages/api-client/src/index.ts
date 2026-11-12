export { Configuration, CourseAdminApi, CourseApi, EnrollmentApi } from "./generated";
export type {
  CourseDetailResponse,
  CourseRequest,
  CourseSummaryResponse,
  EnrollmentResponse,
  ErrorResponse,
  PageResponseCourseSummaryResponse,
  SessionRequest,
  SessionResponse,
} from "./generated";
// 호출 실패의 HTTP 상태를 확인할 때 쓴다. 앱이 axios에 직접 의존하지 않게 여기서 내보낸다.
export { isAxiosError } from "axios";
