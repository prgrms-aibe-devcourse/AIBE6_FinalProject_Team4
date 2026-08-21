import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import PlantCareGuidePanel from "@/features/plant/PlantCareGuidePanel";
import { ApiError } from "@/lib/api";
import { getPlantCareGuide, PlantCareGuideData } from "@/lib/care-guide-api";

vi.mock("@/lib/care-guide-api", () => ({
  getPlantCareGuide: vi.fn(),
}));

const mockedGetGuide = vi.mocked(getPlantCareGuide);

const GUIDE: PlantCareGuideData = {
  speciesName: "방울토마토",
  difficulty: "초급",
  difficultyReason: "베란다 화분에서도 잘 자라는 편이에요.",
  environment: {
    sunlight: "하루 6시간 이상 직사광선을 받게 해 주세요.",
    watering: "겉흙이 마르면 흠뻑 주세요.",
    temperature: "18~28도를 유지해 주세요.",
  },
  stages: [
    { name: "파종", guide: "씨앗을 1cm 깊이로 심어 주세요." },
    { name: "새싹", guide: "통풍이 잘 되는 곳에 두세요." },
    { name: "성장", guide: "곁순을 정리해 주세요." },
    { name: "수확", guide: "붉게 익으면 따 주세요." },
  ],
  pitfalls: [
    { problem: "잎이 노랗게 변해요", action: "물 주는 횟수를 줄여 주세요." },
    {
      problem: "열매가 갈라져요",
      action: "물 주는 간격을 일정하게 유지해 주세요.",
    },
  ],
  harvestTarget: "파종 후 약 70일이면 첫 열매를 볼 수 있어요.",
  grounding: {
    status: "VERIFIED",
    scope: "EXACT_SPECIES",
    resolvedSpeciesName: "방울토마토",
    sources: [
      {
        sourceId: "nise-cherry-tomato-cultivation",
        sourceName: "국립특수교육원 방울토마토 재배 과정",
        sourceUrl: "https://example.test/cherry-tomato",
        version: "2026-08-21",
        contentHash: "a".repeat(64),
      },
    ],
  },
  cached: true,
};

function renderPanel() {
  return render(
    <PlantCareGuidePanel speciesName="방울토마토" accessToken="access-token" />,
  );
}

describe("PlantCareGuidePanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // 진입 즉시 부르면 상세 화면을 열기만 해도 AI 비용이 나간다 — 반드시 사용자가 요청해야 한다.
  it("버튼을 누르기 전에는 가이드를 요청하지 않는다", () => {
    renderPanel();

    expect(mockedGetGuide).not.toHaveBeenCalled();
    expect(
      screen.getByRole("button", { name: "재배 가이드 보기" }),
    ).toBeInTheDocument();
  });

  it("요청한 가이드를 난이도·환경·단계·실패 대처·수확 목표로 나눠 보여준다", async () => {
    mockedGetGuide.mockResolvedValueOnce(GUIDE);
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));

    expect(mockedGetGuide).toHaveBeenCalledWith(
      "방울토마토",
      "access-token",
      expect.any(AbortSignal),
    );
    expect(await screen.findByText("난이도 초급")).toBeInTheDocument();
    expect(screen.getByText(GUIDE.difficultyReason)).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { name: "환경 조건" }),
    ).toBeInTheDocument();
    expect(screen.getByText(GUIDE.environment.sunlight)).toBeInTheDocument();
    expect(screen.getByText(GUIDE.environment.watering)).toBeInTheDocument();
    expect(screen.getByText(GUIDE.environment.temperature)).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { name: "생육 단계" }),
    ).toBeInTheDocument();
    GUIDE.stages.forEach((stage) => {
      expect(screen.getByText(stage.guide)).toBeInTheDocument();
    });

    expect(
      screen.getByRole("heading", { name: "흔한 실패와 대처" }),
    ).toBeInTheDocument();
    expect(screen.getByText("잎이 노랗게 변해요")).toBeInTheDocument();
    expect(screen.getByText("물 주는 횟수를 줄여 주세요.")).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { name: "수확 목표" }),
    ).toBeInTheDocument();
    expect(screen.getByText(GUIDE.harvestTarget)).toBeInTheDocument();
    expect(screen.getByText("공식 문서 근거를 확인했어요")).toBeInTheDocument();
    expect(
      screen.getByRole("link", {
        name: "국립특수교육원 방울토마토 재배 과정",
      }),
    ).toHaveAttribute("href", "https://example.test/cherry-tomato");
  });

  it("공식 문서 근거가 없으면 일반 AI 안내임을 구분해 보여준다", async () => {
    mockedGetGuide.mockResolvedValueOnce({
      ...GUIDE,
      grounding: {
        status: "GENERAL_FALLBACK",
        scope: "NONE",
        resolvedSpeciesName: "원숭이꼬리선인장",
        sources: [],
      },
    });
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));

    expect(
      await screen.findByText("공식 근거가 없는 일반 AI 안내예요"),
    ).toBeInTheDocument();
    expect(screen.getByText(/농약·비료 처방 대신/)).toBeInTheDocument();
  });

  it("품종 입력에 기준 작물 공통 근거를 사용하면 적용 범위를 구분해 보여준다", async () => {
    mockedGetGuide.mockResolvedValueOnce({
      ...GUIDE,
      speciesName: "설향딸기",
      grounding: {
        ...GUIDE.grounding,
        scope: "BASE_SPECIES",
        resolvedSpeciesName: "딸기",
      },
    });
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));

    expect(
      await screen.findByText("딸기 공통 재배 근거를 확인했어요"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/품종 전용 자료가 아닌 딸기 공통/),
    ).toBeInTheDocument();
  });

  // 검수를 하지 않기로 한 대신 반드시 노출하기로 한 문구다 (ai 이슈 2 제품 결정).
  it("가이드와 함께 AI 책임 문구를 노출한다", async () => {
    mockedGetGuide.mockResolvedValueOnce(GUIDE);
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));

    expect(
      await screen.findByText(
        "AI가 생성한 참고 정보이며 정확성을 보장하지 않습니다.",
      ),
    ).toBeInTheDocument();
  });

  // cached는 비용 통제용 내부 지표라 화면에 뜻이 없다.
  it("cached 값은 화면에 노출하지 않는다", async () => {
    mockedGetGuide.mockResolvedValueOnce(GUIDE);
    const { container } = renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));
    await screen.findByText("난이도 초급");

    expect(container.textContent).not.toMatch(/cached|저장된 가이드|캐시/);
  });

  it("생성이 끝날 때까지 로딩 상태를 보여준다", async () => {
    let resolveGuide: (guide: PlantCareGuideData) => void = () => {};
    mockedGetGuide.mockReturnValueOnce(
      new Promise<PlantCareGuideData>((resolve) => {
        resolveGuide = resolve;
      }),
    );
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));

    expect(screen.getByRole("status")).toHaveTextContent(
      "가이드를 만들고 있어요",
    );
    expect(
      screen.getByRole("button", { name: "가이드를 만드는 중..." }),
    ).toBeDisabled();

    await act(async () => {
      resolveGuide(GUIDE);
    });

    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByText("난이도 초급")).toBeInTheDocument();
  });

  // 가이드가 길어 등록 모달에서는 뒤따르는 입력값이 한참 밀린다.
  it("가이드를 접었다 펼쳐도 다시 요청하지 않는다", async () => {
    mockedGetGuide.mockResolvedValueOnce(GUIDE);
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));
    await screen.findByText("난이도 초급");

    fireEvent.click(screen.getByRole("button", { name: "접기" }));

    expect(screen.queryByText("난이도 초급")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "환경 조건" }),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "펼치기" }));

    expect(screen.getByText("난이도 초급")).toBeInTheDocument();
    expect(mockedGetGuide).toHaveBeenCalledTimes(1);
  });

  it("가이드를 받기 전에는 접기 버튼을 보여주지 않는다", () => {
    renderPanel();

    expect(
      screen.queryByRole("button", { name: "접기" }),
    ).not.toBeInTheDocument();
  });

  // 캐시 미스 생성은 수십 초짜리 외부 호출이다. 연타로 두 번 나가면 그대로 비용이 두 배다.
  it("응답을 기다리는 동안 다시 눌러도 요청을 한 번만 보낸다", async () => {
    mockedGetGuide.mockReturnValueOnce(
      new Promise<PlantCareGuideData>(() => {}),
    );
    renderPanel();

    const button = screen.getByRole("button", { name: "재배 가이드 보기" });
    await act(async () => {
      fireEvent.click(button);
      fireEvent.click(button);
    });

    expect(mockedGetGuide).toHaveBeenCalledTimes(1);
  });

  it.each([
    {
      name: "호출 제한",
      error: new ApiError(
        "COMMON_RATE_LIMITED",
        "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
        429,
      ),
      hint: "AI 호출 횟수 제한에 걸렸어요.",
    },
    {
      name: "응답 시간 초과",
      error: new ApiError(
        "AI_REQUEST_TIMEOUT",
        "AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.",
        504,
      ),
      hint: "생성이 예상보다 오래 걸리고 있어요.",
    },
  ])(
    "$name 오류는 서버 메시지와 힌트의 재시도 문구를 중복하지 않는다",
    async ({ error, hint }) => {
      mockedGetGuide.mockRejectedValueOnce(error);
      renderPanel();

      fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(error.message);
      expect(alert).toHaveTextContent(hint);
      expect(alert.textContent?.match(/다시 시도/g) ?? []).toHaveLength(1);
    },
  );

  // 다른 요청이 같은 종을 생성 중이면 서버가 기다리지 않고 409로 끊는다 — 재시도가 유일한 진행 방법이다.
  it("생성 중 충돌(409) 후 다시 시도하면 가이드를 다시 요청한다", async () => {
    mockedGetGuide
      .mockRejectedValueOnce(
        new ApiError(
          "COMMON_DATA_CONFLICT",
          "재배 가이드를 생성하고 있습니다. 잠시 후 다시 시도해 주세요.",
          409,
        ),
      )
      .mockResolvedValueOnce(GUIDE);
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("재배 가이드를 생성하고 있습니다.");
    // 서버 메시지가 이미 "잠시 후 다시 시도"라고 하므로 힌트는 그 말을 되풀이하지 않는다.
    expect(alert).toHaveTextContent(
      "먼저 시작된 생성이 끝나면 저장된 가이드가 바로 나와요.",
    );
    expect(alert.textContent?.match(/다시 시도/g) ?? []).toHaveLength(1);

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("난이도 초급")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(mockedGetGuide).toHaveBeenCalledTimes(2);
  });

  it("네트워크 오류처럼 서버 메시지가 없으면 기본 안내를 보여준다", async () => {
    mockedGetGuide.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("재배 가이드를 불러오지 못했어요.");
    expect(alert).toHaveTextContent(
      "네트워크 상태를 확인하고 다시 시도해 주세요.",
    );
  });

  // 늦게 도착한 응답이 다른 종의 가이드를 덮어쓰면 안 된다.
  it("요청 중에 종이 바뀌면 진행 중이던 요청을 끊는다", () => {
    mockedGetGuide.mockReturnValueOnce(
      new Promise<PlantCareGuideData>(() => {}),
    );
    const { rerender } = render(
      <PlantCareGuidePanel
        speciesName="방울토마토"
        accessToken="access-token"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));
    const signal = mockedGetGuide.mock.calls[0][2];

    rerender(
      <PlantCareGuidePanel speciesName="상추" accessToken="access-token" />,
    );

    expect(signal?.aborted).toBe(true);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "재배 가이드 보기" }),
    ).toBeInTheDocument();
  });

  it("종이 바뀌면 이전 종의 가이드를 비운다", async () => {
    mockedGetGuide.mockResolvedValueOnce(GUIDE);
    const { rerender } = render(
      <PlantCareGuidePanel
        speciesName="방울토마토"
        accessToken="access-token"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));
    await screen.findByText("난이도 초급");

    rerender(
      <PlantCareGuidePanel speciesName="상추" accessToken="access-token" />,
    );

    await waitFor(() =>
      expect(screen.queryByText("난이도 초급")).not.toBeInTheDocument(),
    );
    expect(
      screen.getByRole("button", { name: "재배 가이드 보기" }),
    ).toBeInTheDocument();
  });

  it("언마운트되면 진행 중이던 요청을 끊는다", () => {
    mockedGetGuide.mockReturnValueOnce(
      new Promise<PlantCareGuideData>(() => {}),
    );
    const { unmount } = renderPanel();

    fireEvent.click(screen.getByRole("button", { name: "재배 가이드 보기" }));
    const signal = mockedGetGuide.mock.calls[0][2];

    unmount();

    expect(signal?.aborted).toBe(true);
  });

  it("로그인 토큰이 없으면 요청 버튼을 막는다", () => {
    render(<PlantCareGuidePanel speciesName="방울토마토" accessToken={null} />);

    expect(
      screen.getByRole("button", { name: "재배 가이드 보기" }),
    ).toBeDisabled();
  });
});
