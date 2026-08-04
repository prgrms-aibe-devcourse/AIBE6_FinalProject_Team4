import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import GachaQaShardGrantButton from "./GachaQaShardGrantButton";

const { grantMock, notifyMock, showToastMock } = vi.hoisted(() => ({
  grantMock: vi.fn(),
  notifyMock: vi.fn(),
  showToastMock: vi.fn(),
}));

vi.mock("@/lib/gacha-api", () => ({ grantGachaQaShards: grantMock }));
vi.mock("@/features/gacha/use-gacha-cosmetics", () => ({
  notifyGachaCosmeticsChanged: notifyMock,
}));
vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: showToastMock }),
}));

describe("GachaQaShardGrantButton", () => {
  it("로그인 사용자의 지갑에 QA 조각 100개를 지급한다", async () => {
    grantMock.mockResolvedValue({
      balance: 130,
      lifetimeEarned: 130,
      lifetimeSpent: 0,
    });

    render(<GachaQaShardGrantButton accessToken="access-token" />);
    fireEvent.click(
      screen.getByRole("button", { name: "조각 100개 지급 (QA)" }),
    );

    await waitFor(() => expect(grantMock).toHaveBeenCalledWith("access-token"));
    expect(notifyMock).toHaveBeenCalledOnce();
    expect(showToastMock).toHaveBeenCalledWith(
      "조각 100개 지급 완료 · 현재 130개",
    );
  });
});
