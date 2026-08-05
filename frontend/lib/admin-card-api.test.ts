import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import {
  changeAdminCardStatus,
  createAdminCard,
  getAdminExchangeProductOptions,
  hideAdminCard,
  updateAdminCard,
} from "@/lib/admin-card-api";

vi.mock("@/lib/api", () => ({ request: vi.fn() }));
const mockedRequest = vi.mocked(request);

const input = {
  name: "수박 쿠폰",
  pointPrice: 300,
  exchangeProductId: 1,
  requiredCountForExchange: 5,
  description: null,
  imageUrl: null,
  status: "ON_SALE" as const,
};

describe("admin card api", () => {
  beforeEach(() => vi.clearAllMocks());

  it("쿠폰 등록·수정·노출·숨김 API 계약을 사용한다", async () => {
    mockedRequest.mockResolvedValue({});

    await createAdminCard(input, "token");
    await updateAdminCard(1, input, "token");
    await changeAdminCardStatus(1, "HIDDEN", "token");
    await hideAdminCard(1, "token");
    await getAdminExchangeProductOptions("token");

    expect(mockedRequest).toHaveBeenNthCalledWith(1, "/api/v1/admin/card", {
      method: "POST",
      accessToken: "token",
      body: JSON.stringify(input),
    });
    expect(mockedRequest).toHaveBeenNthCalledWith(
      3,
      "/api/v1/admin/card/1/status",
      {
        method: "PATCH",
        accessToken: "token",
        body: JSON.stringify({ status: "HIDDEN" }),
      },
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(4, "/api/v1/admin/card/1", {
      method: "DELETE",
      accessToken: "token",
    });
    expect(mockedRequest).toHaveBeenNthCalledWith(
      5,
      "/api/v1/admin/card/exchange-products",
      { accessToken: "token", signal: undefined },
    );
  });
});
