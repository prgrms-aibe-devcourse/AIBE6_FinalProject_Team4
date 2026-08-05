import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import { getAdminGachaDraws, retryAdminGachaDraw } from "@/lib/admin-gacha-api";

vi.mock("@/lib/api", () => ({ request: vi.fn() }));
const mockedRequest = vi.mocked(request);

describe("admin gacha api", () => {
  beforeEach(() => vi.clearAllMocks());

  it("상태·사용자 필터와 수동 재시도 계약을 사용한다", async () => {
    mockedRequest.mockResolvedValue({});

    await getAdminGachaDraws("token", {
      status: "MANUAL_REVIEW",
      userId: 7,
      page: 1,
    });
    await retryAdminGachaDraw(10, "token");

    expect(mockedRequest).toHaveBeenNthCalledWith(
      1,
      "/api/v1/admin/card/gacha/draws?page=1&size=20&status=MANUAL_REVIEW&userId=7",
      { accessToken: "token", signal: undefined },
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      2,
      "/api/v1/admin/card/gacha/draws/10/retry",
      { method: "PATCH", accessToken: "token" },
    );
  });
});
