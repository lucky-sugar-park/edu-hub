// remote 모듈 타입(federation 플러그인 dts: false). remote가 내보내는 이름·props와 맞춰야 한다.

declare module "courseMfe/CourseListPage" {
  /** 과정 목록(/). 이름 있는 export다. */
  export function CourseListPage(): JSX.Element;
}

declare module "courseMfe/CourseDetailPage" {
  import type { CourseDetailResponse, SessionResponse } from "@eduhub/api-client";
  import type { ReactNode } from "react";

  /** 회차 행의 작업 칸을 그릴 때 받는 값. reload로 남은 자리 등을 다시 불러온다. */
  export interface SessionActionContext {
    course: CourseDetailResponse;
    session: SessionResponse;
    reload: () => void;
  }

  export interface CourseDetailPageProps {
    /** 회차 행마다 부른다. shell이 enrollmentMfe의 EnrollButton을 끼워 넣는다. */
    renderSessionAction?: (context: SessionActionContext) => ReactNode;
  }

  /** 과정 상세(/courses/:courseId). */
  export default function CourseDetailPage(props: CourseDetailPageProps): JSX.Element;
}

declare module "courseMfe/CourseFormPage" {
  /** 과정·회차 관리(/admin). */
  export default function CourseFormPage(): JSX.Element;
}

declare module "enrollmentMfe/EnrollButton" {
  import type { CourseDetailResponse, SessionResponse } from "@eduhub/api-client";

  export interface EnrollButtonProps {
    course: CourseDetailResponse;
    session: SessionResponse;
    /** 신청·취소 뒤에 부른다(과정 상세 다시 불러오기). */
    onChanged?: () => void;
  }

  /** 회차 하나의 신청·취소 버튼. */
  export default function EnrollButton(props: EnrollButtonProps): JSX.Element;
}

declare module "enrollmentMfe/MyEnrollmentsPage" {
  /** 내 신청 내역(/me/enrollments). */
  export default function MyEnrollmentsPage(): JSX.Element;
}

declare module "enrollmentMfe/RosterPage" {
  /** 회차 명단·수료(/admin/sessions/:sessionId/roster). sessionId는 useParams로 읽는다. */
  export default function RosterPage(): JSX.Element;
}
