import { beforeEach, describe, expect, it, vi } from "vitest";
import { getAdminCardMarketRevenue } from "@/lib/admin-card-market-api";
import { request } from "@/lib/api";

vi.mock("@/lib/api", () => ({ request: vi.fn() }));

describe("admin card market api", () => {
  beforeEach(() => vi.clearAllMocks());

  it("관리자 수익 내역을 페이지 단위로 조회한다", async () => {
    vi.mocked(request).mockResolvedValue({ content: [] });

    await getAdminCardMarketRevenue("admin-token", { page: 2, size: 10 });

    expect(request).toHaveBeenCalledWith(
      "/api/v1/admin/card/market/revenue?page=2&size=10",
      expect.objectContaining({ accessToken: "admin-token" }),
    );
  });
});
