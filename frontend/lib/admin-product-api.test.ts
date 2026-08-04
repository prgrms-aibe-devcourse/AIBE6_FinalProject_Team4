import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import {
  adjustAdminProductStock,
  changeAdminProductStatus,
  createAdminProduct,
  hideAdminProduct,
  updateAdminProduct,
} from "@/lib/admin-product-api";

vi.mock("@/lib/api", () => ({ request: vi.fn() }));

const mockedRequest = vi.mocked(request);
const input = {
  name: "새싹 키트",
  category: "KIT" as const,
  pointPrice: 800,
  stock: 5,
  plantId: null,
  description: null,
  imageUrl: null,
};

describe("admin product api", () => {
  beforeEach(() => vi.clearAllMocks());

  it("등록·수정·재고·노출·숨김 API 계약을 사용한다", async () => {
    mockedRequest.mockResolvedValue({});

    await createAdminProduct(input, "token");
    await updateAdminProduct(1, input, "token");
    await adjustAdminProductStock(1, -3, "token");
    await changeAdminProductStatus(1, "HIDDEN", "token");
    await hideAdminProduct(1, "token");

    expect(mockedRequest).toHaveBeenNthCalledWith(1, "/api/v1/admin/product", {
      method: "POST",
      accessToken: "token",
      body: JSON.stringify(input),
    });
    expect(mockedRequest).toHaveBeenNthCalledWith(
      2,
      "/api/v1/admin/product/1",
      {
        method: "PUT",
        accessToken: "token",
        body: JSON.stringify(input),
      },
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      3,
      "/api/v1/admin/product/1/stock",
      {
        method: "PATCH",
        accessToken: "token",
        body: JSON.stringify({ delta: -3 }),
      },
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      4,
      "/api/v1/admin/product/1/status",
      {
        method: "PATCH",
        accessToken: "token",
        body: JSON.stringify({ status: "HIDDEN" }),
      },
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      5,
      "/api/v1/admin/product/1",
      {
        method: "DELETE",
        accessToken: "token",
      },
    );
  });
});
