import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import { markGachaDrawViewed, purchaseGachaPacks } from "@/lib/gacha-api";

vi.mock("@/lib/api", () => ({
  request: vi.fn(),
}));

const mockedRequest = vi.mocked(request);

describe("gacha api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("상점에서 1팩과 멱등키로 가챠 팩을 구매한다", async () => {
    mockedRequest.mockResolvedValueOnce({
      purchaseId: 501,
      productId: 9,
      productName: "시즌 1 가챠 카드팩",
      quantity: 1,
      unitPoint: 100,
      totalPoint: 100,
      usedFreePoint: 100,
      usedPaidPoint: 0,
      remainingBalance: 900,
      drawIds: [701],
    });

    const response = await purchaseGachaPacks(
      9,
      1,
      "access-token",
      "purchase-key",
    );

    expect(mockedRequest).toHaveBeenCalledWith("/api/v1/card/gacha/purchases", {
      method: "POST",
      accessToken: "access-token",
      headers: {
        "Idempotency-Key": "purchase-key",
      },
      body: JSON.stringify({ productId: 9, quantity: 1 }),
    });
    expect(response.drawIds).toEqual([701]);
  });

  it("개봉 페이지 이탈 중에도 확인 처리를 완료한다", async () => {
    mockedRequest.mockResolvedValueOnce({ drawId: 701 });

    await markGachaDrawViewed(701, "access-token");

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/card/gacha/draws/701/viewed",
      {
        method: "PATCH",
        accessToken: "access-token",
        keepalive: true,
      },
    );
  });
});
