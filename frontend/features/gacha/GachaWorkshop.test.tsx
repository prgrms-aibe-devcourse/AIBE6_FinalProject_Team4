import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import GachaWorkshop from "@/features/gacha/GachaWorkshop";
import { GachaCollectionCard } from "@/lib/gacha-api";

vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: vi.fn(), askConfirm: vi.fn() }),
}));

vi.mock("@/features/gacha/use-gacha-cosmetics", () => ({
  notifyGachaCosmeticsChanged: vi.fn(),
  useGachaCosmetics: () => ({
    data: {
      shards: { balance: 40, lifetimeEarned: 50, lifetimeSpent: 10 },
      cosmetics: [
        {
          code: "TITLE_SPROUT_COLLECTOR",
          name: "새싹 수집가",
          type: "TITLE",
          price: 30,
          styleKey: "title-sprout-collector",
          owned: false,
          equipped: false,
          unlockedAt: null,
        },
      ],
    },
    setData: vi.fn(),
    refresh: vi.fn(),
  }),
}));

const collection: GachaCollectionCard[] = [
  {
    id: 1,
    code: "COMMON_CABBAGE",
    name: "양배추",
    rarity: "COMMON",
    description: null,
    imageUrl: "/cards/1/card.png",
    displayOrder: 1,
    ownedCount: 3,
    dismantleableCount: 2,
    shardPerCard: 1,
    owned: true,
    unlocked: true,
    goldenGachaAcquired: false,
  },
  {
    id: 40,
    code: "HYPER_MANGO",
    name: "애플망고",
    rarity: "HYPER_RARE",
    description: null,
    imageUrl: "/cards/40/card.png",
    displayOrder: 40,
    ownedCount: 5,
    dismantleableCount: 0,
    shardPerCard: 0,
    owned: true,
    unlocked: true,
    goldenGachaAcquired: false,
  },
];

describe("GachaWorkshop", () => {
  it("분해 가능한 중복만 전체 선택하고 한 장을 남긴 예상 조각을 표시한다", () => {
    render(
      <GachaWorkshop
        accessToken="token"
        collection={collection}
        onCollectionRefresh={vi.fn()}
      />,
    );

    fireEvent.click(
      screen.getByRole("button", { name: "분해 가능한 중복 전체 선택" }),
    );

    expect(screen.getByText(/2장 분해/)).toBeInTheDocument();
    expect(screen.getByText("2조각")).toBeInTheDocument();
    expect(screen.queryByText("애플망고")).not.toBeInTheDocument();
  });
});
