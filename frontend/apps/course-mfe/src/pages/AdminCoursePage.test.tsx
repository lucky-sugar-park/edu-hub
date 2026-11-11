import type { CourseDetailResponse } from "@eduhub/api-client";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { type UseCourseAdminResult, useCourseAdmin } from "../hooks/useCourseAdmin";
import { useCourseDetail } from "../hooks/useCourseDetail";
import { useCourses } from "../hooks/useCourses";
import { AdminCoursePage } from "./AdminCoursePage";

vi.mock("../hooks/useCourses", () => ({ useCourses: vi.fn() }));
vi.mock("../hooks/useCourseDetail", () => ({ useCourseDetail: vi.fn() }));
vi.mock("../hooks/useCourseAdmin", () => ({ useCourseAdmin: vi.fn() }));

const mockedUseCourses = vi.mocked(useCourses);
const mockedUseCourseDetail = vi.mocked(useCourseDetail);
const mockedUseCourseAdmin = vi.mocked(useCourseAdmin);

const PAST = "2020-01-01T01:00:00Z";
const FUTURE = "2099-01-01T01:00:00Z";

const openCourse: CourseDetailResponse = {
  id: 1,
  title: "JPA 입문",
  category: "개발",
  description: "기초 과정",
  status: "OPEN",
  sessions: [
    { id: 10, startAt: PAST, place: "본사 3층", capacity: 20, remainingSeats: 20, status: "OPEN" },
    { id: 11, startAt: FUTURE, place: "별관", capacity: 30, remainingSeats: 30, status: "OPEN" },
  ],
};

let reloadCourses: ReturnType<typeof vi.fn>;
let reloadDetail: ReturnType<typeof vi.fn>;
let admin: UseCourseAdminResult;

function givenDetail(detail: CourseDetailResponse | null) {
  mockedUseCourseDetail.mockImplementation((courseId) => ({
    data: courseId === null ? null : detail,
    loading: false,
    error: null,
    reload: reloadDetail,
  }));
}

function givenAdmin(overrides: Partial<UseCourseAdminResult> = {}) {
  admin = {
    submitting: false,
    error: null,
    clearError: vi.fn(),
    createCourse: vi.fn().mockResolvedValue({ id: 2 }),
    updateCourse: vi.fn().mockResolvedValue({ id: 1 }),
    closeCourse: vi.fn().mockResolvedValue({ id: 1 }),
    createSession: vi.fn().mockResolvedValue({ id: 12 }),
    updateSession: vi.fn().mockResolvedValue({ id: 10 }),
    closeSession: vi.fn().mockResolvedValue({ id: 10 }),
    ...overrides,
  };
  mockedUseCourseAdmin.mockReturnValue(admin);
}

beforeEach(() => {
  vi.clearAllMocks();
  reloadCourses = vi.fn();
  reloadDetail = vi.fn();
  mockedUseCourses.mockReturnValue({
    data: {
      content: [
        { id: 1, title: "JPA 입문", category: "개발", status: "OPEN" },
        { id: 3, title: "리더십", category: "경영", status: "CLOSED" },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
    },
    loading: false,
    error: null,
    reload: reloadCourses,
  });
  givenDetail(openCourse);
  givenAdmin();
});

function selectCourse(title = "JPA 입문") {
  fireEvent.click(screen.getByRole("button", { name: title }));
  return screen.getByRole("region", { name: "선택한 과정" });
}

function sessionRow(place: string) {
  return screen.getByRole("cell", { name: place }).closest("tr") as HTMLElement;
}

it("과정을 고르면 상세와 회차를 보여 준다", () => {
  render(<AdminCoursePage />);
  expect(screen.getByText("과정을 선택하면 회차를 관리할 수 있습니다.")).toBeInTheDocument();

  const section = selectCourse();

  expect(within(section).getByText("기초 과정")).toBeInTheDocument();
  expect(mockedUseCourseDetail).toHaveBeenLastCalledWith(1);
  expect(within(section).getAllByRole("row")).toHaveLength(3);
});

it("시작한 회차만 종료할 수 있다", () => {
  render(<AdminCoursePage />);
  selectCourse();

  expect(within(sessionRow("본사 3층")).getByRole("button", { name: "종료" })).toBeEnabled();
  expect(within(sessionRow("별관")).getByRole("button", { name: "종료" })).toBeDisabled();
});

it("회차 종료를 확인하면 API를 부르고 다시 불러온다", async () => {
  render(<AdminCoursePage />);
  selectCourse();

  fireEvent.click(within(sessionRow("본사 3층")).getByRole("button", { name: "종료" }));
  const dialog = screen.getByRole("dialog");
  expect(dialog).toHaveTextContent("회차 종료는 교육 완료를 뜻하며 되돌릴 수 없습니다.");
  fireEvent.click(within(dialog).getByRole("button", { name: "종료" }));

  await waitFor(() => expect(admin.closeSession).toHaveBeenCalledWith(10));
  expect(reloadCourses).toHaveBeenCalled();
  expect(reloadDetail).toHaveBeenCalled();
});

it("과정을 등록한다", async () => {
  render(<AdminCoursePage />);

  fireEvent.click(screen.getByRole("button", { name: "과정 등록" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText(/제목/), { target: { value: " Kotlin 입문 " } });
  fireEvent.mouseDown(within(dialog).getByRole("combobox"));
  fireEvent.click(screen.getByRole("option", { name: "개발" }));
  fireEvent.click(within(dialog).getByRole("button", { name: "저장" }));

  await waitFor(() =>
    expect(admin.createCourse).toHaveBeenCalledWith({
      title: "Kotlin 입문",
      category: "개발",
      description: undefined,
    }),
  );
  expect(reloadCourses).toHaveBeenCalled();
});

it("과정 수정 폼에 기존 값을 채운다", async () => {
  render(<AdminCoursePage />);
  selectCourse();

  fireEvent.click(screen.getByRole("button", { name: "과정 수정" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByLabelText(/제목/)).toHaveValue("JPA 입문");
  fireEvent.change(within(dialog).getByLabelText(/설명/), { target: { value: "심화" } });
  fireEvent.click(within(dialog).getByRole("button", { name: "저장" }));

  await waitFor(() =>
    expect(admin.updateCourse).toHaveBeenCalledWith(1, {
      title: "JPA 입문",
      category: "개발",
      description: "심화",
    }),
  );
});

it("시작한 회차를 수정할 때는 정원과 시작 시각을 막고 원래 시작 시각을 보낸다", async () => {
  render(<AdminCoursePage />);
  selectCourse();

  fireEvent.click(within(sessionRow("본사 3층")).getByRole("button", { name: "수정" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByLabelText(/시작 시각/)).toBeDisabled();
  expect(within(dialog).getByLabelText(/정원/)).toBeDisabled();
  fireEvent.change(within(dialog).getByLabelText(/장소/), { target: { value: "본사 5층" } });
  fireEvent.click(within(dialog).getByRole("button", { name: "저장" }));

  await waitFor(() =>
    expect(admin.updateSession).toHaveBeenCalledWith(10, {
      startAt: PAST,
      place: "본사 5층",
      capacity: 20,
    }),
  );
});

it("회차를 등록한다", async () => {
  render(<AdminCoursePage />);
  selectCourse();

  fireEvent.click(screen.getByRole("button", { name: "회차 등록" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.change(within(dialog).getByLabelText(/시작 시각/), {
    target: { value: "2099-03-02T09:30" },
  });
  fireEvent.change(within(dialog).getByLabelText(/장소/), { target: { value: "강당" } });
  fireEvent.change(within(dialog).getByLabelText(/정원/), { target: { value: "15" } });
  fireEvent.click(within(dialog).getByRole("button", { name: "저장" }));

  await waitFor(() =>
    expect(admin.createSession).toHaveBeenCalledWith(1, {
      startAt: new Date(2099, 2, 2, 9, 30).toISOString(),
      place: "강당",
      capacity: 15,
    }),
  );
});

it("요청이 실패하면 대화 상자에 서버 메시지를 보여 주고 닫지 않는다", async () => {
  givenAdmin({
    error: "종료된 과정입니다: 1",
    updateCourse: vi.fn().mockResolvedValue(null),
  });
  render(<AdminCoursePage />);
  selectCourse();

  fireEvent.click(screen.getByRole("button", { name: "과정 수정" }));
  const dialog = screen.getByRole("dialog");
  fireEvent.click(within(dialog).getByRole("button", { name: "저장" }));

  await waitFor(() => expect(admin.updateCourse).toHaveBeenCalled());
  expect(within(screen.getByRole("dialog")).getByRole("alert")).toHaveTextContent(
    "종료된 과정입니다: 1",
  );
  expect(reloadCourses).not.toHaveBeenCalled();
});

it("종료된 과정은 수정, 종료, 회차 등록을 막는다", () => {
  givenDetail({ ...openCourse, id: 3, title: "리더십", status: "CLOSED" });
  render(<AdminCoursePage />);

  const section = selectCourse("리더십");

  expect(within(section).getByRole("button", { name: "과정 수정" })).toBeDisabled();
  expect(within(section).getByRole("button", { name: "과정 종료" })).toBeDisabled();
  expect(within(section).getByRole("button", { name: "회차 등록" })).toBeDisabled();
  // 회차는 과정 종료와 별개로 교육이 끝나면 종료한다.
  expect(within(sessionRow("본사 3층")).getByRole("button", { name: "종료" })).toBeEnabled();
});

it("목록을 불러오지 못하면 에러를 보여 준다", () => {
  mockedUseCourses.mockReturnValue({ data: null, loading: false, error: new Error("x"), reload: vi.fn() });

  render(<AdminCoursePage />);

  expect(screen.getByRole("alert")).toHaveTextContent("과정 목록을 불러오지 못했습니다.");
});
