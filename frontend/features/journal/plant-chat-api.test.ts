import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  askPlantChat,
  PlantChatRequestPayload,
} from "@/features/journal/plant-chat-api";
import { request } from "@/lib/api";

vi.mock("@/lib/api", () => ({
  request: vi.fn(),
}));

const mockedRequest = vi.mocked(request);

describe("plant chat api", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("선택한 식물 프로필과 서버가 발급한 대화 ID를 전달한다", async () => {
    const controller = new AbortController();
    const payload: PlantChatRequestPayload = {
      question: "잎 끝이 갈색인 이유가 뭘까요?",
      conversationId: "30a508b8-bffc-43c3-8dd0-539a2068500a",
    };
    mockedRequest.mockResolvedValueOnce({
      conversationId: "30a508b8-bffc-43c3-8dd0-539a2068500a",
      answer: "최근 기록을 보면 물주기 간격을 먼저 확인해 보세요.",
      recommendedActions: ["겉흙을 확인해 주세요."],
      additionalChecks: [],
    });

    await askPlantChat(21, payload, "access-token", controller.signal);

    expect(mockedRequest).toHaveBeenCalledWith(
      "/api/v1/ai/plant-profiles/21/chat",
      {
        method: "POST",
        accessToken: "access-token",
        signal: controller.signal,
        body: JSON.stringify(payload),
      },
    );
  });
});
