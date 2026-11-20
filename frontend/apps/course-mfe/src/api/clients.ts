import { DevAuthApi, EnrollmentAdminApi, EnrollmentApi } from "@eduhub/api-client";
import { apiConfiguration, httpClient } from "./http";

/** 개발 환경 전용 로그인(D-30). 운영에서는 404다. */
export const authApi = new DevAuthApi(apiConfiguration, undefined, httpClient);

/** 신청·취소·내 신청 내역(FR-04~07). */
export const enrollmentApi = new EnrollmentApi(apiConfiguration, undefined, httpClient);

/** 관리자 명단·수료(FR-08). */
export const adminEnrollmentApi = new EnrollmentAdminApi(apiConfiguration, undefined, httpClient);
