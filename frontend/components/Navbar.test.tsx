import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Navbar from "./Navbar";

vi.mock("next/navigation", () => ({
  usePathname: () => "/gacha",
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/lib/store", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/store")>();
  return {
    ...original,
    useStore: () => ({
      balance: 0,
      state: {
        authed: false,
        accessToken: null,
        cartCount: 0,
        notifications: [],
        user: null,
      },
      hydrated: false,
      logout: vi.fn(),
      unreadCount: 0,
      markNotifRead: vi.fn(),
      markAllNotifsRead: vi.fn(),
    }),
  };
});

vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: vi.fn(), askConfirm: vi.fn() }),
}));

vi.mock("@/features/gacha/use-gacha-cosmetics", () => ({
  useGachaCosmetics: () => ({ title: null, border: null }),
}));

describe("Navbar", () => {
  afterEach(cleanup);

  it("모바일 하단 메뉴에 가챠 바로가기를 표시한다", () => {
    const { container } = render(<Navbar />);

    const gachaLinks = container.querySelectorAll('a[href="/gacha"]');
    expect(gachaLinks).toHaveLength(2);
    expect(screen.getByText("casino").closest("a")).toHaveAttribute(
      "href",
      "/gacha",
    );
  });
});
