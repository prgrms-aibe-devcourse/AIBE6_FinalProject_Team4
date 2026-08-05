import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import GachaBatchOpenPage from "./page";
import { saveGachaBatch } from "@/features/gacha/batch-session";
import {
  GachaDrawDetail,
  GachaRarity,
  getGachaDraw,
  markGachaDrawViewed,
} from "@/lib/gacha-api";

const navigation = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));

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

function completedDraw(
  drawId: number,
  name: string,
  rarity: GachaRarity,
): GachaDrawDetail {
  return {
    drawId,
    status: "COMPLETED",
    sourceType: "ADMIN",
    rateVersion: 1,
    createdAt: "2026-07-30T03:00:00Z",
    completedAt: "2026-07-30T03:00:01Z",
    resultViewedAt: null,
    items: Array.from({ length: 5 }, (_, index) => ({
      sequence: index + 1,
      cardId: drawId,
      code: `CARD_${drawId}`,
      name,
      imageUrl: `/cards/${drawId}/card.png`,
      rolledRarity: rarity,
      finalRarity: rarity,
      downgraded: false,
      new: index === 0,
      ownedCountAfter: index + 1,
      nextMilestone: null,
      goldenOriginRank: rarity === "GOLDEN_RARE" ? index + 1 : null,
    })),
  };
}

describe("GachaBatchOpenPage", () => {
  afterEach(() => {
    cleanup();
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it("여러 팩을 한 번 셔플한 뒤 결과를 등급순으로 묶어 표시한다", async () => {
    mockedGetDraw.mockImplementation(async (drawId) =>
      drawId === 11
        ? completedDraw(11, "커먼 카드", "COMMON")
        : completedDraw(22, "골든 카드", "GOLDEN_RARE"),
    );
    const batchKey = saveGachaBatch([11, 22]);

    render(<GachaBatchOpenPage params={{ batchKey }} />);

    await waitFor(() => {
      expect(mockedMarkViewed).toHaveBeenCalledTimes(2);
      expect(mockedMarkViewed).toHaveBeenCalledWith(11, "access-token");
      expect(mockedMarkViewed).toHaveBeenCalledWith(22, "access-token");
    });
    expect(
      screen.queryByRole("link", { name: "← 나가기" }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      await screen.findByRole("button", { name: /2팩 한번에 개봉하기/ }),
    );
    expect(
      await screen.findByText(
        "카드의 순서를 섞고 있어요",
        {},
        { timeout: 1800 },
      ),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "전체 결과 확인하기" }));

    expect(await screen.findByText("2팩 개봉 완료")).toBeInTheDocument();
    expect(screen.getByText("총 10장 · 2종 · 높은 등급순")).toBeInTheDocument();
    const resultImages = screen
      .getAllByRole("img")
      .filter((image) => image.getAttribute("aria-label"));
    expect(
      resultImages.map((image) => image.getAttribute("aria-label")),
    ).toEqual(["골든 카드", "커먼 카드"]);
    expect(screen.getAllByText("+5")).toHaveLength(2);
    expect(
      screen.getByRole("link", { name: "상점으로 가기" }),
    ).toHaveAttribute("href", "/shop");
  });
});
