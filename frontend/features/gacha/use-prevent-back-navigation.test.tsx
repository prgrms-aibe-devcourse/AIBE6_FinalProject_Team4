import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { usePreventBackNavigation } from "./use-prevent-back-navigation";

describe("usePreventBackNavigation", () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it("개봉 중 브라우저 뒤로가기를 취소한다", () => {
    const forward = vi
      .spyOn(window.history, "forward")
      .mockImplementation(() => {});

    renderHook(() => usePreventBackNavigation(true));
    act(() => window.dispatchEvent(new PopStateEvent("popstate")));

    expect(forward).toHaveBeenCalledOnce();
  });
});
