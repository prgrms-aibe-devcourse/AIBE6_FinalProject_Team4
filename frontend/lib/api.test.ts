import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, AUTH_EXPIRED_EVENT, request } from "@/lib/api";

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

  // AUTH_ACCOUNT_NOT_ACTIVE는 401이 아니라 403으로 온다(계정이 존재는 하니까) — 그래도
  // 세션이 더 이상 유효하지 않다는 뜻이므로, status가 아니라 code로 알아보고 401과 똑같이
  // 강제 로그아웃 이벤트를 쏴야 한다.
  it("AUTH_ACCOUNT_NOT_ACTIVE(403)도 세션 만료와 동일하게 로그아웃 이벤트를 발생시킨다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: "AUTH_ACCOUNT_NOT_ACTIVE", message: "계정이 활성 상태가 아닙니다." }),
          { status: 403 },
        ),
      ),
    );
    const handler = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, handler);

    try {
      await expect(request("/api/v1/board/posts")).rejects.toBeInstanceOf(ApiError);
      expect(handler).toHaveBeenCalledOnce();
    } finally {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handler);
    }
  });

  // 일반적인 403(예: 관리자가 아닌 유저가 관리자 API를 호출)은 세션 자체는 여전히 유효한
  // 상태이므로 로그아웃시키면 안 된다 — code로 구분하지 않으면 이 케이스까지 강제
  // 로그아웃돼 버린다.
  it("일반 AUTH_ACCESS_DENIED(403)는 로그아웃 이벤트를 발생시키지 않는다", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: "AUTH_ACCESS_DENIED", message: "이 작업을 수행할 권한이 없습니다." }),
          { status: 403 },
        ),
      ),
    );
    const handler = vi.fn();
    window.addEventListener(AUTH_EXPIRED_EVENT, handler);

    try {
      await expect(request("/api/v1/admin/user")).rejects.toBeInstanceOf(ApiError);
      expect(handler).not.toHaveBeenCalled();
    } finally {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handler);
    }
  });
});
