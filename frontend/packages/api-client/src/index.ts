import { Configuration, CourseApi } from "./generated";

export type { CourseSummaryResponse, ErrorResponse } from "./generated";

// 같은 출처로 요청한다. 개발 서버에서는 Vite 프록시가 백엔드로 넘긴다.
export const courseApi = new CourseApi(new Configuration({ basePath: "" }));
