// API 인스턴스는 @eduhub/api-client가 하나만 만든다(shared singleton). 기존 import 경로를 지키려고 다시 내보내기만 한다.
export { courseAdminApi, courseApi } from "@eduhub/api-client";
