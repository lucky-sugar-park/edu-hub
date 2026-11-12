import { Configuration, CourseAdminApi, CourseApi } from "@eduhub/api-client";

// 같은 출처로 요청한다. 개발 서버에서는 Vite 프록시가 백엔드로 넘긴다.
const configuration = new Configuration({ basePath: "" });

export const courseApi = new CourseApi(configuration);

/** 관리자 전용 API(FR-03). 인증(D-11)이 붙으면 같은 configuration에 토큰을 넣는다. */
export const courseAdminApi = new CourseAdminApi(configuration);
