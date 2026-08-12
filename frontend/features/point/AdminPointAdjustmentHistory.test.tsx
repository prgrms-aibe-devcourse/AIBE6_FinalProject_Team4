import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminPointAdjustmentHistory from "@/features/point/AdminPointAdjustmentHistory";

const mocks = vi.hoisted(() => ({
  getAdminPointAdjustments: vi.fn(),
}));

vi.mock("@/features/point/api", () => ({
  getAdminPointAdjustments: mocks.getAdminPointAdjustments,
}));

const historyPage = {
  content: [
    {
      transactionId: 32,
      targetUserId: 10,
      targetEmail: "green@example.com",
      targetNickname: "초록",
      currencyType: "FREE" as const,
      amount: 1000,
      balanceAfter: 2000,
      adjustmentReason: "SPECIAL_EVENT" as const,
      adminUserId: 1,
      createdAt: "2026-08-03T10:00:00",
    },
    {
      transactionId: 31,
      targetUserId: 11,
      targetEmail: "seed@example.com",
      targetNickname: "새싹",
      currencyType: "PAID" as const,
      amount: -500,
      balanceAfter: 2500,
      adjustmentReason: null,
      adminUserId: null,
      createdAt: "2026-08-02T10:00:00",
    },
  ],
  number: 0,
  size: 20,
  totalElements: 22,
  totalPages: 2,
  numberOfElements: 2,
  first: true,
  last: false,
  empty: false,
};

describe("AdminPointAdjustmentHistory", () => {
  beforeEach(() => {
    mocks.getAdminPointAdjustments.mockReset().mockResolvedValue(historyPage);
  });

  it("전체 관리자 조정 내역과 관리자 기록 유무를 표시한다", async () => {
    render(
      <AdminPointAdjustmentHistory
        accessToken="admin-token"
        selectedUserId={10}
        refreshKey={0}
      />,
    );

    await screen.findByText("green@example.com");
    expect(mocks.getAdminPointAdjustments).toHaveBeenCalledWith(
      expect.objectContaining({
        accessToken: "admin-token",
        page: 0,
        size: 20,
        userId: undefined,
      }),
    );
    expect(screen.getByText("#1")).toBeInTheDocument();
    expect(screen.getByText("기록 없음")).toBeInTheDocument();
    expect(screen.getByText("특별 이벤트")).toBeInTheDocument();
    expect(screen.getByText("사유 기록 없음")).toBeInTheDocument();
    expect(screen.getByText("총 22건")).toBeInTheDocument();
  });

  it("포인트 종류·조정 방식·선택 회원 필터를 조회 조건에 반영한다", async () => {
    render(
      <AdminPointAdjustmentHistory
        accessToken="admin-token"
        selectedUserId={10}
        refreshKey={0}
      />,
    );
    await screen.findByText("green@example.com");

    fireEvent.change(screen.getByLabelText("내역 포인트 종류"), {
      target: { value: "FREE" },
    });
    fireEvent.change(screen.getByLabelText("내역 조정 방식"), {
      target: { value: "GRANT" },
    });
    fireEvent.click(screen.getByRole("checkbox", { name: "선택 회원만" }));

    await waitFor(() => {
      expect(mocks.getAdminPointAdjustments).toHaveBeenLastCalledWith(
        expect.objectContaining({
          currencyType: "FREE",
          direction: "GRANT",
          userId: 10,
          page: 0,
        }),
      );
    });
  });

  it("다음 페이지를 조회한다", async () => {
    render(
      <AdminPointAdjustmentHistory
        accessToken="admin-token"
        selectedUserId={10}
        refreshKey={0}
      />,
    );
    await screen.findByText("green@example.com");

    fireEvent.click(screen.getByRole("button", { name: "다음" }));

    await waitFor(() => {
      expect(mocks.getAdminPointAdjustments).toHaveBeenLastCalledWith(
        expect.objectContaining({ page: 1 }),
      );
    });
  });
});
