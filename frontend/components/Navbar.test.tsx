import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Navbar from "./Navbar";
import { grantLocalTestGachaCard } from "@/lib/gacha-api";

const mocks = vi.hoisted(() => ({
  authed: false,
  hydrated: false,
  accessToken: null as string | null,
  user: null as null | {
    nickname: string;
    email: string;
    level: number;
    role: string;
  },
  showToast: vi.fn(),
}));

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
        authed: mocks.authed,
        accessToken: mocks.accessToken,
        cartCount: 0,
        notifications: [],
        user: mocks.user,
      },
      hydrated: mocks.hydrated,
      logout: vi.fn(),
      unreadCount: 0,
      markNotifRead: vi.fn(),
      markAllNotifsRead: vi.fn(),
    }),
  };
});

vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: mocks.showToast, askConfirm: vi.fn() }),
}));

vi.mock("@/lib/gacha-api", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/gacha-api")>();
  return { ...original, grantLocalTestGachaCard: vi.fn() };
});

vi.mock("@/features/gacha/use-gacha-cosmetics", () => ({
  useGachaCosmetics: () => ({ title: null, border: null }),
}));

describe("Navbar", () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.clearAllMocks();
    mocks.authed = false;
    mocks.hydrated = false;
    mocks.accessToken = null;
    mocks.user = null;
  });

  it("모바일 하단 메뉴에 가챠 바로가기를 표시한다", () => {
    const { container } = render(<Navbar />);

    // 데스크톱 상단 내비게이션 + 모바일 하단 탭, 둘 다 항상 가챠 링크가 있다.
    expect(container.querySelectorAll('a[href="/gacha"]')).toHaveLength(2);
    expect(screen.getByText("casino").closest("a")).toHaveAttribute(
      "href",
      "/gacha",
    );
    expect(screen.getByRole("link", { name: "거래소" })).toHaveAttribute(
      "href",
      "/card-market",
    );
  });

  it("로컬 로그인 프로필의 문의 아래에서 하이퍼 테스트 카드를 지급한다", async () => {
    mocks.authed = true;
    mocks.hydrated = true;
    mocks.accessToken = "access-token";
    mocks.user = {
      nickname: "테스터",
      email: "tester@example.com",
      level: 1,
      role: "USER",
    };
    vi.mocked(grantLocalTestGachaCard).mockResolvedValue({
      cardId: 11,
      cardName: "애플망고",
      rarity: "HYPER_RARE",
      grantedQuantity: 2,
      ownedCountAfter: 2,
    });

    render(<Navbar />);
    fireEvent.click(screen.getByRole("button", { name: "테" }));
    const inquiry = screen.getByRole("link", { name: "1:1 문의" });
    const grantButton = screen.getByRole("button", { name: "하이퍼 2장" });
    expect(
      inquiry.compareDocumentPosition(grantButton) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();

    fireEvent.click(grantButton);

    await waitFor(() =>
      expect(grantLocalTestGachaCard).toHaveBeenCalledWith(
        "HYPER_RARE",
        2,
        "access-token",
      ),
    );
    expect(mocks.showToast).toHaveBeenCalledWith("애플망고 2장을 지급했어요.");
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
