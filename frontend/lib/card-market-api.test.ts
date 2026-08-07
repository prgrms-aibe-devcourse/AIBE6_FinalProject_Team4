import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import {
  acceptMarketNegotiation,
  buyMarketListing,
  cancelMarketListing,
  cancelMarketNegotiation,
  createMarketListing,
  createMarketNegotiation,
  getMarketListings,
  getMarketWallet,
  proposeMarketPrice,
  rejectMarketNegotiation,
} from "@/lib/card-market-api";

vi.mock("@/lib/api", () => ({
  request: vi.fn(),
}));

const mockedRequest = vi.mocked(request);

describe("card market api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(crypto, "randomUUID").mockReturnValue(
      "11111111-1111-4111-8111-111111111111",
    );
  });

  it("공개 판매 목록에 등급과 페이지 조건을 전달한다", async () => {
    mockedRequest.mockResolvedValueOnce({
      content: [],
      page: 2,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });

    await getMarketListings("GOLDEN_RARE", 2);

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/card/market/listings?page=2&size=20&sort=createdAt%2Cdesc&assetType=GOLDEN_RARE",
      { signal: undefined },
    );
  });

  it("유상·무상·보관 포인트를 인증 조회한다", async () => {
    mockedRequest.mockResolvedValueOnce({
      paidPoint: 1000,
      freePoint: 500,
      escrowedPaidPoint: 300,
    });

    await getMarketWallet("access-token");

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/card/market/me/wallet",
      { accessToken: "access-token", signal: undefined },
    );
  });

  it("가격 제안에 매 요청별 멱등키와 유상 포인트 가격을 전달한다", async () => {
    mockedRequest.mockResolvedValueOnce({});

    await createMarketNegotiation(17, 900, "READY_TO_BUY", "access-token");

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/card/market/listings/17/negotiations",
      {
        method: "POST",
        accessToken: "access-token",
        headers: {
          "Idempotency-Key": "11111111-1111-4111-8111-111111111111",
        },
        body: JSON.stringify({ price: 900, messageCode: "READY_TO_BUY" }),
      },
    );
  });

  it("판매 등록·취소·즉시 구매 요청에 인증과 멱등키를 전달한다", async () => {
    mockedRequest.mockResolvedValue({});

    await createMarketListing(11, null, 1000, "access-token");
    await cancelMarketListing(17, "access-token");
    await buyMarketListing(17, "access-token");

    expect(mockedRequest).toHaveBeenNthCalledWith(
      1,
      "/api/v1/card/market/listings",
      expect.objectContaining({
        method: "POST",
        accessToken: "access-token",
        headers: {
          "Idempotency-Key": "11111111-1111-4111-8111-111111111111",
        },
        body: JSON.stringify({
          cardId: 11,
          goldenInstanceId: null,
          askingPrice: 1000,
        }),
      }),
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      2,
      "/api/v1/card/market/listings/17",
      expect.objectContaining({ method: "DELETE" }),
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      3,
      "/api/v1/card/market/listings/17/purchases",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("역제안·수락·거절·취소 API 계약을 사용한다", async () => {
    mockedRequest.mockResolvedValue({});

    await proposeMarketPrice(31, 850, "MAXIMUM_OFFER", "access-token");
    await acceptMarketNegotiation(31, "access-token");
    await rejectMarketNegotiation(31, "access-token");
    await cancelMarketNegotiation(31, "access-token");

    expect(mockedRequest).toHaveBeenNthCalledWith(
      1,
      "/api/v1/card/market/negotiations/31/proposals",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ price: 850, messageCode: "MAXIMUM_OFFER" }),
      }),
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      2,
      "/api/v1/card/market/negotiations/31/acceptances",
      expect.objectContaining({ method: "POST" }),
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      3,
      "/api/v1/card/market/negotiations/31/rejections",
      expect.objectContaining({ method: "POST" }),
    );
    expect(mockedRequest).toHaveBeenNthCalledWith(
      4,
      "/api/v1/card/market/negotiations/31",
      expect.objectContaining({ method: "DELETE" }),
    );
  });
});
