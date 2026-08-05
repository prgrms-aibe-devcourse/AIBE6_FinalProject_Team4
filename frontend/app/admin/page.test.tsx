import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import Admin from "./page";

const mocks = vi.hoisted(() => ({
  getProducts: vi.fn(),
  changeStatus: vi.fn(),
  getSpecies: vi.fn(),
  getExchanges: vi.fn(),
  showToast: vi.fn(),
  askConfirm: vi.fn(),
}));

vi.mock("@/lib/store", () => ({
  fmt: (value: number) => value.toLocaleString("ko-KR"),
  useStore: () => ({
    state: { accessToken: "admin-token" },
    hydrated: true,
  }),
}));

vi.mock("@/lib/ui", () => ({
  useUI: () => ({
    showToast: mocks.showToast,
    askConfirm: mocks.askConfirm,
  }),
}));

vi.mock("@/lib/admin-product-api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/admin-product-api")>()),
  getAdminProducts: mocks.getProducts,
  changeAdminProductStatus: mocks.changeStatus,
}));

vi.mock("@/lib/species-api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/species-api")>()),
  getSpecies: mocks.getSpecies,
}));

vi.mock("@/lib/exchange-api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/exchange-api")>()),
  getExchangesForAdmin: mocks.getExchanges,
}));

describe("Admin product management", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getSpecies.mockResolvedValue([]);
    mocks.getExchanges.mockResolvedValue({ content: [] });
    mocks.getProducts.mockResolvedValue([
      {
        id: 1,
        name: "새싹 키트",
        category: "KIT",
        pointPrice: 800,
        stock: 3,
        unlimitedStock: false,
        soldOut: false,
        plantId: null,
        description: null,
        imageUrl: null,
        status: "ACTIVE",
        createdAt: "2026-08-04T00:00:00",
        updatedAt: "2026-08-04T00:00:00",
      },
      {
        id: 2,
        name: "시즌 1 가챠 팩",
        category: "GACHA_PACK",
        pointPrice: 100,
        stock: 0,
        unlimitedStock: true,
        soldOut: false,
        plantId: null,
        description: null,
        imageUrl: null,
        status: "ACTIVE",
        createdAt: "2026-08-04T00:00:00",
        updatedAt: "2026-08-04T00:00:00",
      },
    ]);
  });

  it("실제 상품과 무제한 재고 가챠 팩을 표시하고 노출 상태를 변경한다", async () => {
    mocks.changeStatus.mockResolvedValue({
      ...(await mocks.getProducts())[0],
      status: "HIDDEN",
    });
    render(<Admin />);

    fireEvent.click(screen.getByRole("button", { name: "상품 관리" }));

    expect(await screen.findByText("새싹 키트")).toBeInTheDocument();
    expect(screen.getByText("시즌 1 가챠 팩")).toBeInTheDocument();
    expect(screen.getByText("무제한 재고 · 1회 1팩 구매")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "새싹 키트 숨기기" }));
    await waitFor(() =>
      expect(mocks.changeStatus).toHaveBeenCalledWith(
        1,
        "HIDDEN",
        "admin-token",
      ),
    );
  });
});
