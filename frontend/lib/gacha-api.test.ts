import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import { purchaseGachaPacks } from "@/lib/gacha-api";

vi.mock("@/lib/api", () => ({
  request: vi.fn(),
}));

const mockedRequest = vi.mocked(request);

describe("gacha api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("상점에서 선택한 수량과 멱등키로 가챠 팩을 구매한다", async () => {
    mockedRequest.mockResolvedValueOnce({
      purchaseId: 501,
      productId: 9,
      productName: "시즌 1 가챠 카드팩",
      quantity: 2,
      unitPoint: 100,
      totalPoint: 200,
      usedFreePoint: 200,
      usedPaidPoint: 0,
      remainingBalance: 800,
      drawIds: [701, 702],
    });

    const response = await purchaseGachaPacks(
      9,
      2,
      "access-token",
      "purchase-key",
    );

    expect(mockedRequest).toHaveBeenCalledWith("/api/v1/card/gacha/purchases", {
      method: "POST",
      accessToken: "access-token",
      headers: {
        "Idempotency-Key": "purchase-key",
      },
      body: JSON.stringify({ productId: 9, quantity: 2 }),
    });
    expect(response.drawIds).toEqual([701, 702]);
  });
});
