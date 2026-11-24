import { apiConfiguration, httpClient } from "./auth";
import {
  CourseAdminApi,
  CourseApi,
  DevAuthApi,
  EnrollmentAdminApi,
  EnrollmentApi,
} from "./generated";

// 생성 코드(API 클래스, 타입, enum)를 모두 내보낸다. generated/는 직접 고치지 않는다.
export * from "./generated";
// 공유 Axios 인스턴스와 로그인 상태 보관(D-30).
export * from "./auth";
export { toErrorMessage } from "./errors";

// 앱이 axios에 직접 의존하지 않게 여기서 내보낸다. 호출 실패의 HTTP 상태는 isAxiosError로 확인한다.
export { default as axios, isAxiosError } from "axios";
export type { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from "axios";

// API 인스턴스는 여기서만 만든다. 모두 같은 httpClient(토큰 인터셉터)를 쓴다.
// MFE끼리 이 패키지를 singleton으로 공유하므로 인스턴스도 하나씩만 생긴다.

/** 개발 환경 전용 로그인(D-30). 운영에서는 404다. */
export const authApi = new DevAuthApi(apiConfiguration, undefined, httpClient);

/** 과정 목록·상세(FR-01, FR-02). */
export const courseApi = new CourseApi(apiConfiguration, undefined, httpClient);

/** 관리자 과정·회차 관리(FR-03). */
export const courseAdminApi = new CourseAdminApi(apiConfiguration, undefined, httpClient);

/** 신청·취소·내 신청 내역(FR-04~07). */
export const enrollmentApi = new EnrollmentApi(apiConfiguration, undefined, httpClient);

/** 관리자 명단·수료(FR-08). */
export const adminEnrollmentApi = new EnrollmentAdminApi(apiConfiguration, undefined, httpClient);
