import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import JournalImageAnalysisPanel from "@/features/journal/JournalImageAnalysisPanel";
import {
  analyzeJournalImage,
  getJournalImageAnalyses,
  JournalImageAnalysisData,
} from "@/features/journal/journal-image-analysis-api";
import { ApiError } from "@/lib/api";

vi.mock("@/features/journal/journal-image-analysis-api", () => ({
  analyzeJournalImage: vi.fn(),
  getJournalImageAnalyses: vi.fn(),
}));

const mockedAnalyze = vi.mocked(analyzeJournalImage);
const mockedGetAnalyses = vi.mocked(getJournalImageAnalyses);
const images = [
  {
    imageUrl: "/api/v1/journals/images/7/basil.webp",
    imageHash: "a".repeat(64),
    representative: true,
  },
  {
    imageUrl: "/api/v1/journals/images/7/leaf.webp",
    imageHash: "b".repeat(64),
    representative: false,
  },
];

function analysis(imageHash = images[0].imageHash): JournalImageAnalysisData {
  return {
    id: 91,
    journalId: 31,
    imageHash,
    imageQuality: "CLEAR",
    condition: "NEEDS_ATTENTION",
    summary: "아래쪽 잎 한 장의 끝이 옅게 변한 모습이 보여요.",
    observations: ["잎 끝부분이 주변보다 옅어요."],
    possibleCauses: ["물주기 간격의 영향일 수 있어요."],
    recommendedActions: ["흙이 마른 정도를 먼저 확인해 주세요."],
    additionalChecks: ["잎 뒷면도 살펴봐 주세요."],
    grounding: {
      status: "VERIFIED",
      scope: "EXACT_SPECIES",
      resolvedSpeciesName: "바질",
      sources: [
        {
          sourceId: "official-basil",
          sourceName: "공식 바질 재배 문서",
          sourceUrl: "https://example.test/basil",
          version: "2026-08-21",
          contentHash: "a".repeat(64),
        },
      ],
    },
    analyzedAt: "2026-08-13T10:30:00",
  };
}

describe("JournalImageAnalysisPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetAnalyses.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("새 사진 업로드 없이 현재 선택한 저장 사진을 분석한다", async () => {
    mockedAnalyze.mockResolvedValue(analysis());
    render(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={0}
        accessToken="access-token"
      />,
    );

    const button = await screen.findByRole("button", {
      name: /선택한 사진 AI로 살펴보기/,
    });
    expect(
      screen.getByText("새 사진을 올릴 필요가 없어요"),
    ).toBeInTheDocument();
    fireEvent.click(button);

    expect(mockedAnalyze).toHaveBeenCalledWith(
      31,
      images[0].imageHash,
      "access-token",
      expect.any(AbortSignal),
    );
    expect(
      await screen.findByText("조금 더 관찰해 주세요"),
    ).toBeInTheDocument();
    expect(screen.getByText(analysis().summary)).toBeInTheDocument();
    expect(screen.getByText("공식 문서 근거를 확인했어요")).toBeInTheDocument();
  });

  it("저장된 결과를 재사용하고 선택 사진이 바뀌면 해당 결과를 보여준다", async () => {
    mockedGetAnalyses.mockResolvedValue([
      analysis(images[0].imageHash),
      {
        ...analysis(images[1].imageHash),
        condition: "HEALTHY",
        summary: "새 잎이 고르게 펼쳐져 보여요.",
      },
    ]);
    const { rerender } = render(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={0}
        accessToken="access-token"
      />,
    );

    expect(await screen.findByText(analysis().summary)).toBeInTheDocument();
    expect(mockedAnalyze).not.toHaveBeenCalled();

    rerender(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={1}
        accessToken="access-token"
      />,
    );

    expect(
      screen.getByText("새 잎이 고르게 펼쳐져 보여요."),
    ).toBeInTheDocument();
    expect(screen.getByText("안정적으로 보여요")).toBeInTheDocument();
  });

  it("동일 사진 분석이 진행 중이면 재시도 가능한 안내를 보여준다", async () => {
    mockedAnalyze.mockRejectedValue(
      new ApiError(
        "AI_IMAGE_ANALYSIS_IN_PROGRESS",
        "사진을 분석하고 있습니다.",
        409,
      ),
    );
    render(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={0}
        accessToken="access-token"
      />,
    );

    fireEvent.click(
      await screen.findByRole("button", {
        name: /선택한 사진 AI로 살펴보기/,
      }),
    );

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        "이 사진을 이미 분석하고 있어요",
      ),
    );
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("사진을 바꾸면 이전 사진의 분석 오류를 표시하지 않는다", async () => {
    mockedAnalyze.mockRejectedValue(
      new ApiError(
        "AI_IMAGE_ANALYSIS_IN_PROGRESS",
        "사진을 분석하고 있습니다.",
        409,
      ),
    );
    const { rerender } = render(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={0}
        accessToken="access-token"
      />,
    );

    fireEvent.click(
      await screen.findByRole("button", {
        name: /선택한 사진 AI로 살펴보기/,
      }),
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "이 사진을 이미 분석하고 있어요",
    );

    rerender(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={1}
        accessToken="access-token"
      />,
    );

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: /선택한 사진 AI로 살펴보기/,
      }),
    ).toBeInTheDocument();
  });

  it("진행 중인 분석 요청을 사용자가 취소할 수 있다", async () => {
    let requestSignal: AbortSignal | undefined;
    mockedAnalyze.mockImplementation(
      (_journalId, _imageHash, _accessToken, signal) => {
        requestSignal = signal;
        return new Promise((_resolve, reject) => {
          signal.addEventListener("abort", () =>
            reject(new DOMException("aborted", "AbortError")),
          );
        });
      },
    );
    render(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={0}
        accessToken="access-token"
      />,
    );

    fireEvent.click(
      await screen.findByRole("button", {
        name: /선택한 사진 AI로 살펴보기/,
      }),
    );
    fireEvent.click(screen.getByRole("button", { name: "분석 취소" }));

    expect(requestSignal?.aborted).toBe(true);
    expect(
      await screen.findByRole("button", {
        name: /선택한 사진 AI로 살펴보기/,
      }),
    ).toBeEnabled();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("40초 동안 응답이 없으면 요청을 취소하고 재시도를 허용한다", async () => {
    mockedAnalyze.mockImplementation(
      (_journalId, _imageHash, _accessToken, signal) =>
        new Promise((_resolve, reject) => {
          signal.addEventListener("abort", () =>
            reject(new DOMException("aborted", "AbortError")),
          );
        }),
    );
    render(
      <JournalImageAnalysisPanel
        journalId={31}
        images={images}
        activeIndex={0}
        accessToken="access-token"
      />,
    );
    const analyzeButton = await screen.findByRole("button", {
      name: /선택한 사진 AI로 살펴보기/,
    });
    vi.useFakeTimers();

    fireEvent.click(analyzeButton);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(40_000);
    });
    vi.useRealTimers();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "AI 분석 응답 시간이 초과됐어요",
    );
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeEnabled();
  });
});
