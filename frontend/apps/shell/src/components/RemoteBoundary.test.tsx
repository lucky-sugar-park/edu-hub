import { render, screen } from "@testing-library/react";
import { lazy } from "react";
import { afterEach, expect, it, vi } from "vitest";
import { RemoteBoundary } from "./RemoteBoundary";

afterEach(() => vi.restoreAllMocks());

it("remote를 불러오는 동안 진행 표시를 보여 주고, 다 불러오면 화면을 그린다", async () => {
  const Remote = lazy(async () => ({ default: () => <div>remote 화면</div> }));
  render(
    <RemoteBoundary name="과정 화면">
      <Remote />
    </RemoteBoundary>,
  );

  expect(screen.getByLabelText("과정 화면 불러오는 중")).toBeInTheDocument();
  expect(await screen.findByText("remote 화면")).toBeInTheDocument();
});

it("remote가 꺼져 있어 불러오지 못하면 안내 Alert를 보여 준다", async () => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  const Remote = lazy(() =>
    Promise.reject(new Error("Failed to fetch http://localhost:3001/remoteEntry.js")),
  );
  render(
    <RemoteBoundary name="과정 화면">
      <Remote />
    </RemoteBoundary>,
  );

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "과정 화면을(를) 불러오지 못했습니다",
  );
});

it("inline이면 회차 행 안에 작은 안내만 보여 준다", async () => {
  vi.spyOn(console, "error").mockImplementation(() => {});
  const Remote = lazy(() => Promise.reject(new Error("down")));
  render(
    <RemoteBoundary name="신청 버튼" inline>
      <Remote />
    </RemoteBoundary>,
  );

  expect(await screen.findByRole("alert")).toHaveTextContent("신청 버튼을(를) 불러오지 못했습니다");
});
