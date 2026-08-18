import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import {
  commerceErrorMessage,
  formatCommerceDateTime,
  formatPoint,
  isAbortError,
} from "./presentation";
import { firstSearchParam, parseOneBasedPage } from "./list-query";

describe("commerce presentation", () => {
  it("formats point and date values consistently", () => {
    expect(formatPoint(12345)).toBe("12,345P");
    expect(formatCommerceDateTime("2026-08-12T17:30:00")).toContain("8월 12일");
  });

  it("uses API messages and ignores abort errors", () => {
    expect(
      commerceErrorMessage(
        new ApiError("INVALID", "잘못된 요청", 400),
        "fallback",
      ),
    ).toBe("잘못된 요청");
    expect(commerceErrorMessage(new Error("unknown"), "fallback")).toBe(
      "fallback",
    );
    expect(isAbortError(new DOMException("aborted", "AbortError"))).toBe(true);
  });
});

describe("commerce list query", () => {
  it("normalizes repeated and invalid page parameters", () => {
    expect(firstSearchParam(["mine", "catalog"])).toBe("mine");
    expect(parseOneBasedPage("3")).toBe(2);
    expect(parseOneBasedPage("0")).toBe(0);
    expect(parseOneBasedPage("not-a-page")).toBe(0);
  });
});
