import { apiConfiguration, EnrollmentAdminApi, EnrollmentApi, httpClient } from "@eduhub/api-client";

/** 신청·취소·내 신청 내역(FR-04~07). */
export const enrollmentApi = new EnrollmentApi(apiConfiguration, undefined, httpClient);

/** 관리자 명단·수료(FR-08). */
export const adminEnrollmentApi = new EnrollmentAdminApi(apiConfiguration, undefined, httpClient);
