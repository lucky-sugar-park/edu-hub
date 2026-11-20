import { CourseAdminApi, CourseApi } from "@eduhub/api-client";
import { apiConfiguration, httpClient } from "./http";

export const courseApi = new CourseApi(apiConfiguration, undefined, httpClient);

/** 관리자 전용 API(FR-03). */
export const courseAdminApi = new CourseAdminApi(apiConfiguration, undefined, httpClient);
