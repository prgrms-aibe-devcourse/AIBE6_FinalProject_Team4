import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  GachaCosmeticsProvider,
  useGachaCosmetics,
} from "@/features/gacha/use-gacha-cosmetics";
import { getMyGachaCosmetics } from "@/lib/gacha-api";

vi.mock("@/lib/store", () => ({
  useStore: () => ({ state: { accessToken: "access-token" } }),
}));

vi.mock("@/lib/gacha-api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/gacha-api")>();
  return {
    ...original,
    getMyGachaCosmetics: vi.fn(),
  };
});

const mockedGetMyGachaCosmetics = vi.mocked(getMyGachaCosmetics);

function Balance({ label }: { label: string }) {
  const { data } = useGachaCosmetics();
  return <span>{`${label}:${data?.shards.balance ?? "loading"}`}</span>;
}

describe("GachaCosmeticsProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetMyGachaCosmetics.mockResolvedValue({
      shards: { balance: 40, lifetimeEarned: 50, lifetimeSpent: 10 },
      cosmetics: [],
    });
  });

  it("여러 소비자가 같은 전역 조회 결과를 공유한다", async () => {
    render(
      <GachaCosmeticsProvider>
        <Balance label="navbar" />
        <Balance label="page" />
      </GachaCosmeticsProvider>,
    );

    await waitFor(() => {
      expect(screen.getByText("navbar:40")).toBeInTheDocument();
      expect(screen.getByText("page:40")).toBeInTheDocument();
    });
    expect(mockedGetMyGachaCosmetics).toHaveBeenCalledOnce();
  });
});
