import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import { answerInquiry, getInquiriesForAdmin } from "@/lib/inquiry-api";

vi.mock("@/lib/api", () => ({ request: vi.fn() }));
const mockedRequest = vi.mocked(request);

describe("inquiry admin api", () => {
  beforeEach(() => vi.clearAllMocks());

  it("상태 필터가 있으면 쿼리에 status를 포함한다", async () => {
    mockedRequest.mockResolvedValue({});

    await getInquiriesForAdmin("token", "OPEN", 1, 20);

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/admin/inquiries?page=1&size=20&status=OPEN",
      { accessToken: "token", signal: undefined },
    );
  });

  it("상태 필터가 없으면 쿼리에 status를 넣지 않는다", async () => {
    mockedRequest.mockResolvedValue({});

    await getInquiriesForAdmin("token", undefined, 0, 20);

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/admin/inquiries?page=0&size=20",
      { accessToken: "token", signal: undefined },
    );
  });

  it("답변 등록 API 계약을 사용한다", async () => {
    mockedRequest.mockResolvedValue({});

    await answerInquiry(5, "답변 내용", "token");

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/admin/inquiries/5/answer",
      {
        method: "PATCH",
        accessToken: "token",
        body: JSON.stringify({ answerContent: "답변 내용" }),
      },
    );
  });
});
