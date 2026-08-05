import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import GachaPage from "./page";
import {
  getGachaCatalog,
  getGachaDraws,
  getGachaRates,
  getMyGachaCollection,
} from "@/lib/gacha-api";

const mockAuth = vi.hoisted(() => ({ accessToken: null as string | null }));

vi.mock("next/image", () => ({
  default: ({ alt }: { alt: string }) => <span role="img" aria-label={alt} />,
}));

vi.mock("@/lib/store", () => ({
  useStore: () => ({
    state: mockAuth,
    hydrated: true,
  }),
}));

vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: vi.fn(), askConfirm: vi.fn() }),
}));

vi.mock("@/features/gacha/use-gacha-cosmetics", () => ({
  useGachaCosmetics: () => ({
    data: null,
    title: null,
    border: null,
  }),
}));

vi.mock("@/lib/gacha-api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/gacha-api")>();
  return {
    ...original,
    getGachaCatalog: vi.fn(),
    getGachaRates: vi.fn(),
    getMyGachaCollection: vi.fn(),
    getGachaDraws: vi.fn(),
  };
});

const mockedCatalog = vi.mocked(getGachaCatalog);
const mockedRates = vi.mocked(getGachaRates);
const mockedCollection = vi.mocked(getMyGachaCollection);
const mockedDraws = vi.mocked(getGachaDraws);

const card = {
  id: 1,
  code: "COMMON_LETTUCE",
  name: "양상추",
  rarity: "COMMON" as const,
  description: "봄 텃밭의 첫 번째 수호자",
  imageUrl: null,
  displayOrder: 1,
};

describe("GachaPage", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    mockAuth.accessToken = null;
    mockedCatalog.mockResolvedValue([card]);
    mockedRates.mockResolvedValue({
      rateVersion: 1,
      drawCount: 5,
      totalWeight: 2_100_000,
      rarities: [{ rarity: "COMMON", weight: 1_470_000, percent: 70 }],
      notices: [],
    });
    mockedCollection.mockResolvedValue([]);
    mockedDraws.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it("공개 도감은 접힌 상태로 시작하고 미획득 원본을 노출하지 않는다", async () => {
    render(<GachaPage />);

    expect(
      await screen.findByRole("heading", {
        name: "오늘의 기록이 카드가 돼요",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText("양상추")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "커먼 도감 펼치기" }));

    expect(await screen.findByText("양상추")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "양상추 미획득 카드" }),
    ).toBeDisabled();
    expect(
      screen.queryByRole("img", { name: "양상추 카드 일러스트" }),
    ).not.toBeInTheDocument();
    expect(mockedCollection).not.toHaveBeenCalled();
    expect(mockedDraws).not.toHaveBeenCalled();
  });

  it("보유 카드 갤러리에서 원본 일러스트를 확대한다", async () => {
    mockAuth.accessToken = "access-token";
    mockedCollection.mockResolvedValue([
      {
        ...card,
        imageUrl: "/cards/1/card.png",
        ownedCount: 3,
        dismantleableCount: 2,
        shardPerCard: 1,
        owned: true,
        unlocked: true,
        goldenGachaAcquired: false,
      },
    ]);

    render(<GachaPage />);
    await screen.findByRole("heading", {
      name: "오늘의 기록이 카드가 돼요",
    });

    fireEvent.click(screen.getByRole("button", { name: /내 카드 갤러리/ }));
    expect(
      await screen.findByRole("heading", { name: "나의 카드 갤러리" }),
    ).toBeInTheDocument();
    expect(screen.getByText("×3")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /조각 공방 열기/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^조각 공방$/ }),
    ).not.toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", {
        name: "양상추 원본 일러스트 크게 보기",
      }),
    );

    expect(
      screen.getByRole("dialog", { name: "양상추 원본 일러스트" }),
    ).toBeInTheDocument();
    const originalImage = screen.getByRole("img", {
      name: "양상추 원본 카드 일러스트",
    });
    expect(originalImage).toBeInTheDocument();
    expect(originalImage.parentElement).toHaveClass("aspect-[1122/1402]");
    expect(originalImage.parentElement).not.toHaveClass("bg-black");
  });

  it("마이페이지 내 카드 링크는 내 카드 갤러리를 바로 연다", async () => {
    mockAuth.accessToken = "access-token";

    render(<GachaPage searchParams={{ tab: "mine" }} />);

    expect(
      await screen.findByRole("heading", { name: "나의 카드 갤러리" }),
    ).toBeInTheDocument();
  });

  it("마이페이지 칭호·테두리 링크는 이펙트 상점을 바로 연다", async () => {
    mockAuth.accessToken = "access-token";

    render(
      <GachaPage searchParams={{ tab: "workshop", section: "cosmetics" }} />,
    );

    expect(
      await screen.findByRole("heading", {
        name: "이펙트 칭호·프로필 테두리",
      }),
    ).toBeInTheDocument();
  });

  it("브라우저 뒤로가기로 복귀하면 보유 카드와 개봉 이력을 다시 조회한다", async () => {
    mockAuth.accessToken = "access-token";
    render(<GachaPage />);

    await waitFor(() => expect(mockedCollection).toHaveBeenCalledOnce());
    await waitFor(() => expect(mockedDraws).toHaveBeenCalledOnce());

    window.dispatchEvent(new Event("pageshow"));

    await waitFor(() => expect(mockedCollection).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(mockedDraws).toHaveBeenCalledTimes(2));
  });
});
