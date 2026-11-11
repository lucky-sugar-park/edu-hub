import { render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { CourseNotFoundError, useCourse } from "../hooks/useCourse";
import { CourseDetailPage } from "./CourseDetailPage";

vi.mock("../hooks/useCourse", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../hooks/useCourse")>()),
  useCourse: vi.fn(),
}));

const mockedUseCourse = vi.mocked(useCourse);

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/courses/:id" element={<CourseDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

it("과정 정보와 회차를 보여 준다", () => {
  mockedUseCourse.mockReturnValue({
    data: {
      id: 3,
      title: "JPA 입문",
      category: "개발",
      description: "엔티티와 연관관계",
      status: "OPEN",
      sessions: [
        {
          id: 10,
          startAt: "2026-12-01T05:00:00Z",
          place: "본사 3층 교육장",
          capacity: 30,
          remainingSeats: 12,
          status: "OPEN",
        },
      ],
    },
    loading: false,
    error: null,
  });

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
  mockedUseCourse.mockReturnValue({
    data: { id: 3, title: "JPA 입문", category: "개발", status: "OPEN", sessions: [] },
    loading: false,
    error: null,
  });

  renderAt("/courses/3");

  expect(screen.getByRole("cell", { name: "등록된 회차가 없습니다." })).toBeInTheDocument();
});

it("로딩 중에는 진행 표시를 보여 준다", () => {
  mockedUseCourse.mockReturnValue({ data: null, loading: true, error: null });

  renderAt("/courses/3");

  expect(screen.getByRole("progressbar", { name: "불러오는 중" })).toBeInTheDocument();
});

it("과정이 없으면 찾을 수 없다고 알린다", () => {
  mockedUseCourse.mockReturnValue({ data: null, loading: false, error: new CourseNotFoundError() });

  renderAt("/courses/999");

  expect(screen.getByRole("alert")).toHaveTextContent("과정을 찾을 수 없습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "← 목록으로" })).toHaveAttribute("href", "/");
});

it("그 밖의 실패는 불러오지 못했다고 알린다", () => {
  mockedUseCourse.mockReturnValue({ data: null, loading: false, error: new Error("서버 오류") });

  renderAt("/courses/3");

  expect(screen.getByRole("alert")).toHaveTextContent("과정 정보를 불러오지 못했습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
