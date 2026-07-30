import { beforeEach, describe, expect, it, vi } from "vitest";
import { request } from "@/lib/api";
import { createPlantJournal, getMyPlantProfiles } from "./api";

vi.mock("@/lib/api", () => ({
  request: vi.fn(),
}));

const mockedRequest = vi.mocked(request);

describe("journal api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("인증 토큰으로 내 식물 목록을 요청한다", async () => {
    mockedRequest.mockResolvedValueOnce([]);

    await getMyPlantProfiles("access-token");

    expect(mockedRequest).toHaveBeenCalledWith("/api/v1/plants", {
      accessToken: "access-token",
      signal: undefined,
    });
  });

  it("일지를 저장하고 가챠 보상 응답을 받는다", async () => {
    const input = {
      plantProfileId: 7,
      content: "오늘의 기록",
      images: [
        {
          imageUrl: "/journal-demo/photo-1.svg",
          imageHash: "a".repeat(64),
          representative: true,
        },
      ],
    };
    mockedRequest.mockResolvedValueOnce({
      id: 11,
      gachaReward: { granted: true, drawId: 31, status: "PENDING" },
    });

    const response = await createPlantJournal("access-token", input);

    expect(mockedRequest).toHaveBeenCalledWith("/api/v1/journals", {
      method: "POST",
      accessToken: "access-token",
      body: JSON.stringify(input),
    });
    expect(response.gachaReward.drawId).toBe(31);
  });
});
