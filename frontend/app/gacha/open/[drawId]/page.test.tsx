import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import GachaOpenPage from "./page";
import { getGachaDraw, markGachaDrawViewed } from "@/lib/gacha-api";

const navigation = vi.hoisted(() => ({ back: vi.fn(), replace: vi.fn() }));
const store = vi.hoisted(() => ({ refreshNotifications: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => navigation,
}));

vi.mock("next/image", () => ({
  default: ({ alt, className }: { alt: string; className?: string }) => (
    <span role="img" aria-label={alt} data-class={className} />
  ),
}));

vi.mock("@/lib/store", () => ({
  useStore: () => ({
    state: { accessToken: "access-token" },
    hydrated: true,
    refreshNotifications: store.refreshNotifications,
  }),
}));

vi.mock("@/lib/gacha-api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/gacha-api")>();
  return {
    ...original,
    getGachaDraw: vi.fn(),
    markGachaDrawViewed: vi.fn(),
  };
});

const mockedGetDraw = vi.mocked(getGachaDraw);
const mockedMarkViewed = vi.mocked(markGachaDrawViewed);

describe("GachaOpenPage", () => {
  afterEach(cleanup);
  beforeEach(() => {
    vi.clearAllMocks();
    store.refreshNotifications.mockResolvedValue(undefined);
  });

  it("실제 카드 비율을 유지하고 원본 전체를 잘리지 않게 표시한다", async () => {
    mockedGetDraw.mockResolvedValue({
      drawId: 21,
      status: "COMPLETED",
      sourceType: "LOG_REWARD",
      rateVersion: 1,
      createdAt: "2026-07-30T03:00:00Z",
      completedAt: "2026-07-30T03:00:01Z",
      resultViewedAt: null,
      items: Array.from({ length: 5 }, (_, index) => ({
        sequence: index + 1,
        cardId: index + 1,
        code: `CARD_${index + 1}`,
        name: `카드 ${index + 1}`,
        imageUrl: `/cards/${index + 1}/card.png`,
        rolledRarity: "COMMON" as const,
        finalRarity: "COMMON" as const,
        downgraded: false,
        new: index === 0,
        ownedCountAfter: 1,
        nextMilestone: 3,
        goldenOriginRank: null,
      })),
    });

    render(<GachaOpenPage params={{ drawId: "21" }} />);

    await waitFor(() =>
      expect(mockedMarkViewed).toHaveBeenCalledWith(21, "access-token"),
    );
    expect(
      screen.queryByRole("link", { name: "← 나가기" }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      await screen.findByRole("button", { name: /팩을 눌러 개봉하기/ }),
    );
    expect(
      await screen.findByText(
        "카드의 순서를 섞고 있어요",
        {},
        { timeout: 1800 },
      ),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "첫 카드 확인하기" }));

    const revealedCard = await screen.findByRole("img", { name: "카드 1" });
    expect(revealedCard).toHaveAttribute("data-class", "object-contain");
    expect(revealedCard.parentElement).toHaveClass("aspect-[1122/1402]");
    expect(
      screen.getByRole("button", { name: "다음 카드 보기" }),
    ).toBeInTheDocument();

    for (let index = 0; index < 4; index += 1) {
      fireEvent.click(screen.getByRole("button", { name: "다음 카드 보기" }));
    }
    fireEvent.click(screen.getByRole("button", { name: "전체 결과 보기" }));
    expect(
      await screen.findByRole("button", { name: "뒤로가기" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "내 카드 보기" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("일지 보러 가기")).not.toBeInTheDocument();
    expect(screen.queryByText("카드팩 구매하기")).not.toBeInTheDocument();
    expect(screen.queryByText("다른 개봉 내역 보기")).not.toBeInTheDocument();
    expect(screen.queryByText("개봉 연출 다시 보기")).not.toBeInTheDocument();
    expect(screen.queryByText(/사운드|음소거/)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "뒤로가기" }));
    await waitFor(() =>
      expect(store.refreshNotifications).toHaveBeenCalledTimes(1),
    );
    expect(navigation.replace).toHaveBeenCalledWith("/gacha");
    expect(navigation.back).not.toHaveBeenCalled();
  });

  it("환불된 팩은 포인트 반환 안내 후 대기를 종료한다", async () => {
    mockedGetDraw.mockResolvedValue({
      drawId: 22,
      status: "REFUNDED",
      sourceType: "PURCHASE",
      rateVersion: 1,
      createdAt: "2026-07-30T03:00:00Z",
      completedAt: null,
      resultViewedAt: null,
      items: [],
    });

    render(<GachaOpenPage params={{ drawId: "22" }} />);

    expect(
      await screen.findByText(
        "팩을 준비하지 못해 사용한 포인트를 돌려드렸어요.",
      ),
    ).toBeInTheDocument();
    expect(mockedMarkViewed).not.toHaveBeenCalled();
    expect(
      screen.queryByText("카드 5장을 준비하고 있어요"),
    ).not.toBeInTheDocument();
  });

  it("일지 보상으로 진입하면 뒤로가기 시 성장일지 목록으로 이동한다", async () => {
    mockedGetDraw.mockResolvedValue({
      drawId: 23,
      status: "COMPLETED",
      sourceType: "LOG_REWARD",
      rateVersion: 1,
      createdAt: "2026-07-30T03:00:00Z",
      completedAt: "2026-07-30T03:00:01Z",
      resultViewedAt: "2026-07-30T03:00:02Z",
      items: Array.from({ length: 5 }, (_, index) => ({
        sequence: index + 1,
        cardId: index + 1,
        code: `JOURNAL_CARD_${index + 1}`,
        name: `일지 카드 ${index + 1}`,
        imageUrl: `/cards/${index + 1}/journal-card.png`,
        rolledRarity: "COMMON" as const,
        finalRarity: "COMMON" as const,
        downgraded: false,
        new: false,
        ownedCountAfter: 2,
        nextMilestone: 3,
        goldenOriginRank: null,
      })),
    });

    render(
      <GachaOpenPage
        params={{ drawId: "23" }}
        searchParams={{ returnTo: "journals" }}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "뒤로가기" }));

    await waitFor(() =>
      expect(store.refreshNotifications).toHaveBeenCalledTimes(1),
    );
    expect(navigation.replace).toHaveBeenCalledWith("/journals");
    expect(navigation.back).not.toHaveBeenCalled();
  });
});
