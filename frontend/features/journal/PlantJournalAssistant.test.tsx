import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PlantJournalAssistant from "@/features/journal/PlantJournalAssistant";
import { askPlantChat } from "@/features/journal/plant-chat-api";
import { ApiError } from "@/lib/api";

vi.mock("@/features/journal/plant-chat-api", () => ({
  askPlantChat: vi.fn(),
}));

const mockedAskPlantChat = vi.mocked(askPlantChat);
const PLANT = { id: 21, nickname: "바질이", speciesName: "바질" };
const scrollIntoViewMock = vi.fn();

Object.defineProperty(Element.prototype, "scrollIntoView", {
  configurable: true,
  value: scrollIntoViewMock,
});

function renderAssistant(plant: typeof PLANT | null = PLANT) {
  return render(
    <PlantJournalAssistant
      plant={plant}
      currentJournalContent="오늘 새잎 끝이 조금 갈색이고 겉흙은 말라 있었어요."
      accessToken="access-token"
    />,
  );
}

function openAssistant() {
  fireEvent.click(
    screen.getByRole("button", { name: /식물을 키우다 궁금한 점이 있나요/ }),
  );
}

describe("PlantJournalAssistant", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("일지 페이지에서는 플로팅 버튼으로 열고 닫을 수 있다", () => {
    renderAssistant();

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    openAssistant();
    expect(
      screen.getByRole("dialog", { name: "AI 식물 도우미" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("무료 · 바로 확인")).not.toBeInTheDocument();
    expect(screen.queryByText(/Enter 전송/)).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: "AI 식물 도우미 닫기" }),
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("여러 식물 중 상담할 식물을 바꿀 수 있다", () => {
    const onPlantChange = vi.fn();
    render(
      <PlantJournalAssistant
        plant={PLANT}
        plantOptions={[
          PLANT,
          { id: 22, nickname: "상추", speciesName: "청상추" },
        ]}
        onPlantChange={onPlantChange}
        currentJournalContent=""
        accessToken="access-token"
      />,
    );
    openAssistant();

    fireEvent.change(screen.getByLabelText("상담할 식물 선택"), {
      target: { value: "22" },
    });

    expect(onPlantChange).toHaveBeenCalledWith(22);
  });

  it("질문 입력 예시는 식물 닉네임이 아닌 실제 종명을 사용한다", () => {
    render(
      <PlantJournalAssistant
        plant={{ id: 23, nickname: "딸기공주", speciesName: "설향 딸기" }}
        currentJournalContent=""
        accessToken="access-token"
      />,
    );
    openAssistant();

    expect(
      screen.getByPlaceholderText(
        "설향 딸기에 대해 궁금한 점을 입력해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText(
        "딸기공주에 대해 궁금한 점을 입력해 주세요.",
      ),
    ).not.toBeInTheDocument();
  });

  it("질문 입력란은 브라우저 자동완성과 비밀번호 저장 대상에서 제외한다", () => {
    renderAssistant();
    openAssistant();

    const questionInput = screen.getByLabelText(
      "내 식물 기록을 바탕으로 직접 질문하기",
    );
    expect(questionInput).toHaveAttribute("name", "plantJournalAiQuestion");
    expect(questionInput).toHaveAttribute("autocomplete", "off");
  });

  it("식물을 고르기 전에는 질문 기능을 안내만 하고 비활성 상태로 둔다", () => {
    renderAssistant(null);
    openAssistant();

    expect(screen.getByText(/식물을 먼저 선택하면/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "AI에게 묻기" }),
    ).not.toBeInTheDocument();
  });

  it("FAQ를 선택하면 AI를 호출하지 않고 준비된 답변과 행동을 보여준다", () => {
    renderAssistant();
    openAssistant();

    fireEvent.click(
      screen.getByRole("button", {
        name: "오늘 일지에는 무엇을 적으면 좋을까요?",
      }),
    );

    expect(mockedAskPlantChat).not.toHaveBeenCalled();
    expect(screen.getByText("준비된 답변")).toBeInTheDocument();
    expect(screen.getByText(/보이는 모습.*오늘 한 관리/)).toBeInTheDocument();
    expect(screen.getByText("지금 해볼 일")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /1:1 문의하기/ }),
    ).not.toBeInTheDocument();
  });

  it("FAQ를 고르면 목록이 접히고 카테고리 칩으로 다시 펼칠 수 있다", () => {
    renderAssistant();
    openAssistant();

    fireEvent.click(
      screen.getByRole("button", {
        name: "오늘 일지에는 무엇을 적으면 좋을까요?",
      }),
    );

    expect(
      screen.getByRole("button", { name: "자주 묻는 질문 목록 펼치기" }),
    ).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(screen.getByRole("button", { name: /물주기/ }));

    expect(
      screen.getByRole("button", { name: "자주 묻는 질문 목록 접기" }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("목록이 접힌 뒤에도 FAQ 버튼은 같은 노드로 DOM에 남는다", () => {
    // 접기를 조건부 렌더로 "정리"하면 대화 맥락 전달(위 테스트)이 조용히 깨진다. 그 회귀를 막는 가드다.
    renderAssistant();
    openAssistant();

    const faqButton = screen.getByRole("button", {
      name: "오늘 일지에는 무엇을 적으면 좋을까요?",
    });
    fireEvent.click(faqButton);

    expect(faqButton).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "오늘 일지에는 무엇을 적으면 좋을까요?",
      }),
    ).toBe(faqButton);
  });

  it("FAQ 답변과 직접 AI 답변을 같은 흐름에서 바로 보이게 한다", async () => {
    let resolveResponse = (
      _value: Awaited<ReturnType<typeof askPlantChat>>,
    ) => {};
    const pendingResponse = new Promise<
      Awaited<ReturnType<typeof askPlantChat>>
    >((resolve) => {
      resolveResponse = resolve;
    });
    mockedAskPlantChat.mockReturnValueOnce(pendingResponse);
    renderAssistant();
    openAssistant();

    fireEvent.click(
      screen.getByRole("button", {
        name: "오늘 일지에는 무엇을 적으면 좋을까요?",
      }),
    );
    await waitFor(() =>
      expect(scrollIntoViewMock).toHaveBeenLastCalledWith({ block: "nearest" }),
    );
    expect(scrollIntoViewMock.mock.instances.at(-1)).toHaveTextContent(
      "보이는 모습",
    );
    scrollIntoViewMock.mockClear();

    fireEvent.change(
      screen.getByLabelText("내 식물 기록을 바탕으로 직접 질문하기"),
      {
        target: { value: "딸기는 어떤 상태에서 수확하면 좋을까요?" },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));

    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalledTimes(1));
    expect(scrollIntoViewMock).toHaveBeenLastCalledWith({ block: "nearest" });
    expect(scrollIntoViewMock.mock.instances.at(-1)).toHaveTextContent(
      "기록을 살펴보고 있어요",
    );
    const log = screen.getByRole("log", { name: "AI 식물 도우미 대화" });
    expect(log.parentElement).toHaveClass(
      "flex",
      "min-h-full",
      "flex-col",
      "justify-end",
    );

    await act(async () => {
      resolveResponse({
        answer: "열매 전체가 고르게 붉고 향이 진할 때 수확해 주세요.",
        recommendedActions: ["아침에 꼭지째 수확해 주세요."],
        additionalChecks: [],
      });
      await pendingResponse;
    });

    expect(
      await screen.findByText(
        "열매 전체가 고르게 붉고 향이 진할 때 수확해 주세요.",
      ),
    ).toBeInTheDocument();
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalledTimes(2));
    expect(scrollIntoViewMock.mock.instances.at(-1)).toHaveTextContent(
      "열매 전체가 고르게 붉고 향이 진할 때 수확해 주세요.",
    );
    const turnTexts = Array.from(log.children).map(
      (message) => message.textContent ?? "",
    );
    expect(turnTexts).toHaveLength(4);
    expect(turnTexts[0]).toContain("오늘 일지에는 무엇을 적으면 좋을까요?");
    expect(turnTexts[1]).toContain("보이는 모습");
    expect(turnTexts[2]).toContain("딸기는 어떤 상태에서 수확하면 좋을까요?");
    expect(turnTexts[3]).toContain(
      "열매 전체가 고르게 붉고 향이 진할 때 수확해 주세요.",
    );
    expect(scrollIntoViewMock).toHaveBeenCalledTimes(2);
  });

  it("대화창을 닫았다 다시 열면 가장 최근 답변이 있는 최하단을 보여준다", async () => {
    renderAssistant();
    openAssistant();
    fireEvent.click(
      screen.getByRole("button", {
        name: "오늘 일지에는 무엇을 적으면 좋을까요?",
      }),
    );
    await waitFor(() => expect(scrollIntoViewMock).toHaveBeenCalled());

    fireEvent.click(
      screen.getByRole("button", { name: "AI 식물 도우미 닫기" }),
    );
    scrollIntoViewMock.mockClear();
    openAssistant();

    await waitFor(() =>
      expect(scrollIntoViewMock).toHaveBeenCalledWith({ block: "end" }),
    );
    expect(scrollIntoViewMock.mock.instances.at(-1)).toBe(
      screen.getByTestId("plant-journal-chat-end"),
    );
    expect(screen.getByText("준비된 답변")).toBeInTheDocument();
  });

  it("직접 질문하면 선택 식물·작성 중인 일지와 함께 AI 응답을 요청한다", async () => {
    mockedAskPlantChat.mockResolvedValueOnce({
      answer: "최근 기록만으로는 원인을 단정하기 어려워요.",
      recommendedActions: ["겉흙 아래 수분을 확인해 주세요."],
      additionalChecks: ["새잎에도 갈변이 생기는지 살펴보세요."],
    });
    renderAssistant();
    openAssistant();

    fireEvent.change(
      screen.getByLabelText("내 식물 기록을 바탕으로 직접 질문하기"),
      {
        target: { value: "잎 끝이 갈색인 이유가 뭘까요?" },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));

    await waitFor(() =>
      expect(mockedAskPlantChat).toHaveBeenCalledWith(
        21,
        {
          question: "잎 끝이 갈색인 이유가 뭘까요?",
          currentJournalContent:
            "오늘 새잎 끝이 조금 갈색이고 겉흙은 말라 있었어요.",
          recentMessages: [],
        },
        "access-token",
        expect.any(AbortSignal),
      ),
    );
    expect(
      await screen.findByText("최근 기록만으로는 원인을 단정하기 어려워요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("겉흙 아래 수분을 확인해 주세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText("새잎에도 갈변이 생기는지 살펴보세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/AI 답변은 식물 프로필과 최근 일지를 바탕으로/),
    ).toBeInTheDocument();
  });

  it("대화가 길어지면 최근 질문·답변 세 쌍만 다음 AI 요청에 전달한다", async () => {
    mockedAskPlantChat.mockResolvedValueOnce({
      answer: "맞춤 답변입니다.",
      recommendedActions: ["기록을 이어가 주세요."],
      additionalChecks: [],
    });
    renderAssistant();
    openAssistant();

    const faqButton = screen.getByRole("button", {
      name: "오늘 일지에는 무엇을 적으면 좋을까요?",
    });
    fireEvent.click(faqButton);
    fireEvent.click(faqButton);
    fireEvent.click(faqButton);
    fireEvent.click(faqButton);
    fireEvent.change(
      screen.getByLabelText("내 식물 기록을 바탕으로 직접 질문하기"),
      {
        target: { value: "이 기록을 보고 조언해 주세요." },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));

    await waitFor(() => expect(mockedAskPlantChat).toHaveBeenCalledTimes(1));
    const payload = mockedAskPlantChat.mock.calls[0][1];
    expect(payload.recentMessages).toHaveLength(6);
    expect(payload.recentMessages.map((message) => message.role)).toEqual([
      "USER",
      "ASSISTANT",
      "USER",
      "ASSISTANT",
      "USER",
      "ASSISTANT",
    ]);
  });

  it("응답을 기다리는 동안 전송을 다시 눌러도 AI를 한 번만 호출한다", async () => {
    mockedAskPlantChat.mockReturnValueOnce(new Promise(() => {}));
    renderAssistant();
    openAssistant();
    fireEvent.change(
      screen.getByLabelText("내 식물 기록을 바탕으로 직접 질문하기"),
      {
        target: { value: "물을 더 줘도 될까요?" },
      },
    );

    const sendButton = screen.getByRole("button", { name: "AI에게 묻기" });
    await act(async () => {
      fireEvent.click(sendButton);
      fireEvent.click(sendButton);
    });

    expect(mockedAskPlantChat).toHaveBeenCalledTimes(1);
    expect(screen.getByRole("status")).toHaveTextContent(
      "기록을 살펴보고 있어요",
    );
  });

  it("서버 오류 메시지를 다른 재시도 문구와 중복하지 않는다", async () => {
    mockedAskPlantChat.mockRejectedValueOnce(
      new ApiError(
        "COMMON_RATE_LIMITED",
        "AI 호출 횟수 제한에 걸렸어요. 잠시 뒤에 다시 시도해 주세요.",
        429,
      ),
    );
    renderAssistant();
    openAssistant();
    fireEvent.change(
      screen.getByLabelText("내 식물 기록을 바탕으로 직접 질문하기"),
      {
        target: { value: "물을 언제 줘야 하나요?" },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(
      "AI 호출 횟수 제한에 걸렸어요. 잠시 뒤에 다시 시도해 주세요.",
    );
    expect(alert.textContent?.match(/다시 시도/g) ?? []).toHaveLength(1);
  });

  it("질문 중 식물을 바꾸면 이전 요청과 대화를 정리한다", async () => {
    mockedAskPlantChat.mockReturnValueOnce(new Promise(() => {}));
    const { rerender } = renderAssistant();
    openAssistant();
    fireEvent.change(
      screen.getByLabelText("내 식물 기록을 바탕으로 직접 질문하기"),
      {
        target: { value: "지금 상태가 괜찮을까요?" },
      },
    );
    fireEvent.click(screen.getByRole("button", { name: "AI에게 묻기" }));
    const signal = mockedAskPlantChat.mock.calls[0][3];

    rerender(
      <PlantJournalAssistant
        plant={{ id: 22, nickname: "상추", speciesName: "청상추" }}
        currentJournalContent="새 식물 기록"
        accessToken="access-token"
      />,
    );

    await waitFor(() => expect(signal?.aborted).toBe(true));
    expect(
      screen.queryByText("지금 상태가 괜찮을까요?"),
    ).not.toBeInTheDocument();
    expect(screen.getByText(/상추\(청상추\)/)).toBeInTheDocument();
    expect(
      screen.getByLabelText("내 식물 기록을 바탕으로 직접 질문하기"),
    ).toHaveValue("");
    expect(screen.getByRole("button", { name: "AI에게 묻기" })).toBeDisabled();
  });
});
