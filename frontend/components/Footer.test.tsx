import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Footer from "./Footer";

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
}));

describe("Footer", () => {
  afterEach(cleanup);

  it("서비스 링크에서 가챠 도감으로 이동할 수 있다", () => {
    render(<Footer />);

    expect(screen.getByRole("link", { name: "가챠" })).toHaveAttribute(
      "href",
      "/gacha",
    );
  });
});
