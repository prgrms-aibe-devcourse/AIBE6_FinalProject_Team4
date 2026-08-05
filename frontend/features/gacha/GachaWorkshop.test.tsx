import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import GachaWorkshop from "@/features/gacha/GachaWorkshop";
import { GachaCollectionCard } from "@/lib/gacha-api";

vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: vi.fn(), askConfirm: vi.fn() }),
}));

vi.mock("@/features/gacha/use-gacha-cosmetics", () => ({
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
        {
          code: "BORDER_SPROUT_VINE",
          name: "풀잎의 숨결",
          type: "BORDER",
          price: 150,
          styleKey: "border-sprout-vine",
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
    const { container } = render(
      <GachaWorkshop
        accessToken="token"
        collection={collection}
        onCollectionRefresh={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("보유 카드 조각 40개")).toBeInTheDocument();
    expect(screen.getByText("현재 보유 조각")).toBeInTheDocument();
    expect(screen.getByText("누적 획득")).toBeInTheDocument();
    expect(screen.getByText("50개")).toBeInTheDocument();
    expect(screen.queryByText("arrow_back")).not.toBeInTheDocument();
    expect(screen.queryByText("recycling")).not.toBeInTheDocument();
    expect(screen.queryByText("auto_awesome")).not.toBeInTheDocument();

    expect(
      screen.queryByRole("button", { name: "낮은 등급부터 20개 선택" }),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /카드 분해/ }));

    fireEvent.click(
      screen.getByRole("button", { name: "낮은 등급부터 20개 선택" }),
    );

    expect(screen.getByText("2/20")).toBeInTheDocument();
    expect(screen.getByText(/2장 분해/)).toBeInTheDocument();
    expect(screen.getByText("2조각")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "2개 변환하기" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("애플망고")).not.toBeInTheDocument();
    expect(screen.queryByText("새싹 수집가")).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: "작업 선택으로 돌아가기" }),
    );
    fireEvent.click(screen.getByRole("button", { name: /이펙트 상점/ }));

    expect(
      screen.queryByRole("button", { name: "낮은 등급부터 20개 선택" }),
    ).not.toBeInTheDocument();
    expect(
      container.querySelector('[data-cosmetic-title="TITLE_SPROUT_COLLECTOR"]'),
    ).toHaveAttribute("data-title-effect", "sprout-glow");
    expect(
      container.querySelector('[data-profile-border="BORDER_SPROUT_VINE"]'),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText("풀잎의 숨결 프로필 테두리 미리보기"),
    ).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: "작업 선택으로 돌아가기" }),
    );
    fireEvent.click(screen.getByRole("button", { name: /카드 분해/ }));

    expect(
      screen.getByRole("button", { name: "낮은 등급부터 20개 선택" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("새싹 수집가")).not.toBeInTheDocument();
  });

  it("자동 선택은 낮은 등급부터 최대 20장만 담는다", () => {
    const prioritizedCollection: GachaCollectionCard[] = [
      {
        ...collection[0],
        ownedCount: 31,
        dismantleableCount: 30,
      },
      {
        ...collection[0],
        id: 2,
        code: "RARE_CARROT",
        name: "레어 당근",
        rarity: "RARE",
        displayOrder: 2,
        ownedCount: 31,
        dismantleableCount: 30,
        shardPerCard: 3,
      },
    ];

    render(
      <GachaWorkshop
        accessToken="token"
        collection={prioritizedCollection}
        onCollectionRefresh={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: /카드 분해/ }));
    expect(screen.getByText("0/20")).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: "낮은 등급부터 20개 선택" }),
    );

    expect(screen.getByText("20/20")).toBeInTheDocument();
    const commonCard = screen.getByText("양배추").closest("article");
    const rareCard = screen.getByText("레어 당근").closest("article");
    expect(commonCard).not.toBeNull();
    expect(rareCard).not.toBeNull();
    expect(within(commonCard!).getByText("20")).toBeInTheDocument();
    expect(within(rareCard!).getByText("0")).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", { name: "레어 당근 분해 수량 증가" }),
    );
    expect(within(rareCard!).getByText("0")).toBeInTheDocument();
  });
});
