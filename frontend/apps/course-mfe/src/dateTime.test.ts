import { expect, it } from "vitest";
import { fromLocalInputValue, hasStarted, toLocalInputValue } from "./dateTime";

it("datetime-local 값과 ISO 시각을 서로 바꾼다", () => {
  const local = "2026-12-01T10:30";

  const iso = fromLocalInputValue(local);

  expect(iso).toBe(new Date(2026, 11, 1, 10, 30).toISOString());
  expect(toLocalInputValue(iso)).toBe(local);
});

it("시작 시각과 같거나 지나면 시작한 것으로 본다", () => {
  const now = new Date("2026-11-12T00:00:00Z");

  expect(hasStarted("2026-11-11T23:59:00Z", now)).toBe(true);
  expect(hasStarted("2026-11-12T00:00:00Z", now)).toBe(true);
  expect(hasStarted("2026-11-12T00:01:00Z", now)).toBe(false);
  expect(hasStarted(undefined, now)).toBe(false);
});
