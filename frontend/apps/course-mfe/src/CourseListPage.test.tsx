import { courseApi } from "@eduhub/api-client";
import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { CourseListPage } from "./CourseListPage";

vi.mock("@eduhub/api-client", () => ({
  courseApi: { searchCourses: vi.fn() },
}));

it("과정 목록을 표에 보여 준다", async () => {
  vi.mocked(courseApi.searchCourses).mockResolvedValue({
    data: {
      content: [{ id: 1, title: "JPA 입문", category: "개발", status: "OPEN" }],
      page: 0,
      size: 20,
      totalElements: 1,
    },
  } as Awaited<ReturnType<typeof courseApi.searchCourses>>);

  render(<CourseListPage />);

  expect(await screen.findByRole("cell", { name: "JPA 입문" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "개발" })).toBeInTheDocument();
  expect(screen.getByRole("cell", { name: "운영 중" })).toBeInTheDocument();
  expect(courseApi.searchCourses).toHaveBeenCalledWith(undefined, undefined, 0, 20);
});
