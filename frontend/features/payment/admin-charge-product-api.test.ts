import { beforeEach, describe, expect, it, vi } from "vitest";
import { createAdminChargeProduct } from "@/features/payment/admin-charge-product-api";
import { request } from "@/lib/api";

vi.mock("@/lib/api", () => ({ request: vi.fn() }));

const mockedRequest = vi.mocked(request);

describe("admin charge product api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("상품 생성 요청에 관리자 멱등키를 전달한다", async () => {
    mockedRequest.mockResolvedValueOnce({
      id: 7,
      version: 0,
      name: "이벤트 충전",
      price: 3000,
      pointAmount: 3300,
      isActive: true,
    });

    await createAdminChargeProduct("admin-token", "create-key", {
      name: "이벤트 충전",
      price: 3000,
      pointAmount: 3300,
      isActive: true,
    });

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/admin/payments/products",
      {
        method: "POST",
        accessToken: "admin-token",
        headers: { "Idempotency-Key": "create-key" },
        body: JSON.stringify({
          name: "이벤트 충전",
          price: 3000,
          pointAmount: 3300,
          isActive: true,
        }),
      },
    );
  });
});
