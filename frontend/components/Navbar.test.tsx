import { cleanup, fireEvent, render, screen } from "@testing-library/react";
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

  it("모바일 하단 탭에 가챠 바로가기를 표시한다", () => {
    const { container } = render(<Navbar />);

    // 데스크톱 상단 내비게이션 + 모바일 하단 탭, 둘 다 항상 가챠 링크가 있다.
    expect(container.querySelectorAll('a[href="/gacha"]')).toHaveLength(2);
    expect(screen.getByText("casino").closest("a")).toHaveAttribute(
      "href",
      "/gacha",
    );
  });

  it("모바일 하단 탭에 커뮤니티 바로가기를 표시한다", () => {
    render(<Navbar />);

    expect(screen.getByText("forum").closest("a")).toHaveAttribute(
      "href",
      "/board",
    );
  });

  it("모바일 하단 '더보기' 시트를 열면 마이페이지 바로가기를 표시한다", () => {
    render(<Navbar />);

    // 로그인 전이라 "더보기" 안의 마이페이지는 /auth로 연결된다.
    fireEvent.click(screen.getByText("더보기"));

    expect(screen.getByText("마이페이지").closest("a")).toHaveAttribute(
      "href",
      "/auth",
    );
  });
});
