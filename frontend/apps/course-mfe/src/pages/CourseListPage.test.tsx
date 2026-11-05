import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { useCourses } from "../hooks/useCourses";
import { CourseListPage } from "./CourseListPage";

vi.mock("../hooks/useCourses", () => ({
  useCourses: vi.fn(),
}));

const mockedUseCourses = vi.mocked(useCourses);

it("목록을 표에 보여 준다", () => {
  mockedUseCourses.mockReturnValue({
    data: {
      content: [{ id: 1, title: "JPA 입문", category: "개발", status: "OPEN" }],
      page: 0,
      size: 20,
      totalElements: 1,
    },
    loading: false,
    error: null,
  });

  render(<CourseListPage />);

  expect(screen.getByRole("cell", { name: "JPA 입문" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "개발" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "운영 중" })).toBeInTheDocument();
  expect(mockedUseCourses).toHaveBeenCalledWith({ keyword: "", category: "", page: 0, size: 20 });
});

it("실패하면 에러 메시지를 보여 준다", () => {
  mockedUseCourses.mockReturnValue({
    data: null,
    loading: false,
    error: new Error("서버 오류"),
  });

  render(<CourseListPage />);

  expect(screen.getByRole("alert")).toHaveTextContent("과정 목록을 불러오지 못했습니다.");
  expect(screen.queryByRole("table")).not.toBeInTheDocument();
});
