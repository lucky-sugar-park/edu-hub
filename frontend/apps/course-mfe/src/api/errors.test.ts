import { expect, it } from "vitest";
import { toErrorMessage } from "./errors";

it("서버의 message를 꺼낸다", () => {
  expect(toErrorMessage({ response: { data: { message: "이미 시작된 회차입니다." } } })).toBe(
    "이미 시작된 회차입니다.",
  );
});

it("응답이 없는 오류는 기본 문구를 쓴다", () => {
  expect(toErrorMessage(new Error("Network Error"))).toBe("요청을 처리하지 못했습니다.");
  expect(toErrorMessage(null)).toBe("요청을 처리하지 못했습니다.");
});
