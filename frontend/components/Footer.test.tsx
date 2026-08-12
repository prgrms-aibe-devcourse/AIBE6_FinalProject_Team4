import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Footer from "./Footer";

vi.mock("next/navigation", () => ({
  usePathname: () => "/card-market",
}));

describe("Footer", () => {
  afterEach(cleanup);

  it("서비스 메뉴에서 독립 카드 거래소 페이지로 이동한다", () => {
    render(<Footer />);

    expect(screen.getByRole("link", { name: "카드 거래소" })).toHaveAttribute(
      "href",
      "/card-market",
    );
  });
});
