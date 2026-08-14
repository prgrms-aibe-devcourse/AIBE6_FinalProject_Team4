import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  analyzeJournalImage,
  getJournalImageAnalyses,
} from "@/features/journal/journal-image-analysis-api";
import { request } from "@/lib/api";

vi.mock("@/lib/api", () => ({
  request: vi.fn(),
}));

const mockedRequest = vi.mocked(request);

describe("journal image analysis api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedRequest.mockResolvedValue(undefined);
  });

  it("현재 일지의 저장된 사진 분석 결과를 조회한다", async () => {
    const controller = new AbortController();

    await getJournalImageAnalyses(31, "access-token", controller.signal);

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/ai/journals/31/image-analysis",
      {
        accessToken: "access-token",
        signal: controller.signal,
      },
    );
  });

  it("선택한 저장 사진 해시만 분석 요청으로 전달한다", async () => {
    const imageHash = "a".repeat(64);
    const controller = new AbortController();

    await analyzeJournalImage(31, imageHash, "access-token", controller.signal);

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/ai/journals/31/image-analysis",
      {
        method: "POST",
        accessToken: "access-token",
        signal: controller.signal,
        body: JSON.stringify({ imageHash }),
      },
    );
  });
});
