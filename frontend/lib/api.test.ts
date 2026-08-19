import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, request } from "@/lib/api";

describe("request", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("제한 응답의 재시도 시간을 ApiError에 보존한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: "COMMON_RATE_LIMITED",
            message: "AI 호출 횟수 제한에 걸렸어요.",
            details: { retryAfterSeconds: 86399 },
          }),
          { status: 429, headers: { "Retry-After": "86399" } },
        ),
      ),
    );

    try {
      await request("/api/v1/ai/plant-profiles/21/chat");
      throw new Error("요청이 제한 오류로 끝나야 합니다.");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).retryAfterSeconds).toBe(86399);
    }
  });

  it("본문에 재시도 시간이 없으면 Retry-After 헤더를 사용한다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: "COMMON_RATE_LIMITED",
            message: "요청이 너무 많습니다.",
          }),
          { status: 429, headers: { "Retry-After": "60" } },
        ),
      ),
    );

    try {
      await request("/api/v1/ai/plant-profiles/21/chat");
      throw new Error("요청이 제한 오류로 끝나야 합니다.");
    } catch (error) {
      expect((error as ApiError).retryAfterSeconds).toBe(60);
    }
  });
});
