import type { CourseDetailResponse } from "@eduhub/api-client";
import { fireEvent, screen, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { CourseNotFoundError, useCourse } from "../hooks/useCourse";
import { renderWithProviders } from "../test-utils";
import { CourseDetailPage, type CourseDetailPageProps } from "./CourseDetailPage";

// 신청·취소 버튼은 enrollment-mfe의 EnrollButton이 맡는다(shell이 renderSessionAction으로 끼워 넣는다).
// 여기서는 과정 표시와 renderSessionAction에 넘기는 값만 확인한다.

vi.mock("../hooks/useCourse", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../hooks/useCourse")>()),
  useCourse: vi.fn(),
}));

const mockedUseCourse = vi.mocked(useCourse);

const FUTURE = "2026-12-01T05:00:00Z";

let reloadCourse: ReturnType<typeof vi.fn>;

function course(overrides: Partial<CourseDetailResponse> = {}): CourseDetailResponse {
  return {
    id: 3,
    title: "JPA 입문",
    category: "개발",
    description: "엔티티와 연관관계",
    status: "OPEN",
    sessions: [
      {
        id: 10,
        startAt: FUTURE,
        place: "본사 3층 교육장",
        capacity: 30,
        remainingSeats: 12,
        status: "OPEN",
      },
    ],
    ...overrides,
  };
}

function givenCourse(data: CourseDetailResponse) {
  mockedUseCourse.mockReturnValue({ data, loading: false, error: null, reload: reloadCourse });
}

function renderAt(path: string, props: CourseDetailPageProps = {}) {
  renderWithProviders(<CourseDetailPage {...props} />, {
    route: path,
    // shell의 라우트(/courses/:courseId)와 같은 파라미터 이름을 쓴다.
    path: "/courses/:courseId",
  });
}

beforeEach(() => {
  reloadCourse = vi.fn();
});

it("과정 정보와 회차를 보여 준다", () => {
  givenCourse(course());

  renderAt("/courses/3");

  expect(mockedUseCourse).toHaveBeenCalledWith(3);
  expect(screen.getByRole("heading", { level: 1, name: "JPA 입문" })).toBeInTheDocument();
  expect(screen.getByText("엔티티와 연관관계")).toBeInTheDocument();

  const table = screen.getByRole("table", { name: "회차 목록" });
  // UTC 05:00은 한국 시간 14:00이다.
  expect(within(table).getByRole("cell", { name: "2026. 12. 1. (화) 14:00" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "본사 3층 교육장" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "30" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "12" })).toBeInTheDocument();
  expect(within(table).getByRole("cell", { name: "운영 중" })).toBeInTheDocument();
});

it("회차가 없으면 안내 문구를 보여 준다", () => {
  givenCourse(course({ sessions: [] }));

  renderAt("/courses/3");

  expect(screen.getByRole("cell", { name: "등록된 회차가 없습니다." })).toBeInTheDocument();
});

it("로딩 중에는 진행 표시를 보여 준다", () => {
  mockedUseCourse.mockReturnValue({ data: null, loading: true, error: null, reload: reloadCourse });

  renderAt("/courses/3");

  expect(screen.getByRole("progressbar", { name: "불러오는 중" })).toBeInTheDocument();
});

it("과정이 없으면 찾을 수 없다고 알린다", () => {
  mockedUseCourse.mockReturnValue({
    data: null,
    loading: false,
    error: new CourseNotFoundError(),
    reload: reloadCourse,
  });

  renderAt("/courses/999");

  expect(screen.getByRole("alert")).toHaveTextContent("과정을 찾을 수 없습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "← 목록으로" })).toHaveAttribute("href", "/");
});

it("그 밖의 실패는 불러오지 못했다고 알린다", () => {
  mockedUseCourse.mockReturnValue({
    data: null,
    loading: false,
    error: new Error("서버 오류"),
    reload: reloadCourse,
  });

  renderAt("/courses/3");

  expect(screen.getByRole("alert")).toHaveTextContent("과정 정보를 불러오지 못했습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});


it("renderSessionAction이 없으면 신청 열을 그리지 않는다", () => {
  givenCourse(course());

  renderAt("/courses/3");

  const table = screen.getByRole("table", { name: "회차 목록" });
  expect(within(table).queryByRole("columnheader", { name: "신청" })).not.toBeInTheDocument();
  expect(within(table).getAllByRole("columnheader")).toHaveLength(5);
});

it("renderSessionAction을 회차 행마다 과정·회차·reload와 함께 부르고 결과를 행 끝에 그린다", () => {
  const first = course().sessions![0];
  const second = {
    id: 11,
    startAt: FUTURE,
    place: "본사 5층",
    capacity: 10,
    remainingSeats: 0,
    status: "OPEN" as const,
  };
  const data = course({ sessions: [first, second] });
  givenCourse(data);
  const renderSessionAction = vi.fn(({ session, reload }) => (
    <button type="button" onClick={reload}>
      작업 {session.id}
    </button>
  ));

  renderAt("/courses/3", { renderSessionAction });

  const table = screen.getByRole("table", { name: "회차 목록" });
  expect(within(table).getByRole("columnheader", { name: "신청" })).toBeInTheDocument();
  expect(renderSessionAction).toHaveBeenCalledWith({
    course: data,
    session: first,
    reload: reloadCourse,
  });
  expect(renderSessionAction).toHaveBeenCalledWith({
    course: data,
    session: second,
    reload: reloadCourse,
  });

  const rows = within(table).getAllByRole("row");
  expect(within(rows[1]).getByRole("button", { name: "작업 10" })).toBeInTheDocument();
  expect(within(rows[2]).getByRole("button", { name: "작업 11" })).toBeInTheDocument();

  fireEvent.click(within(rows[2]).getByRole("button", { name: "작업 11" }));
  expect(reloadCourse).toHaveBeenCalledTimes(1);
});

it("renderSessionAction이 있으면 회차 없음 안내가 신청 열까지 걸친다", () => {
  givenCourse(course({ sessions: [] }));

  renderAt("/courses/3", { renderSessionAction: () => null });

  expect(screen.getByRole("cell", { name: "등록된 회차가 없습니다." })).toHaveAttribute(
    "colspan",
    "6",
  );
});
