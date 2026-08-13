import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api";
import AdminPointAdjustmentPanelComponent from "@/features/point/AdminPointAdjustmentPanel";

const mocks = vi.hoisted(() => ({
  getAdminUsers: vi.fn(),
  getWalletByAdmin: vi.fn(),
  adjustPointByAdmin: vi.fn(),
  askConfirm: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock("@/features/admin/user-api", () => ({
  getAdminUsers: mocks.getAdminUsers,
}));

vi.mock("@/features/point/api", () => ({
  getWalletByAdmin: mocks.getWalletByAdmin,
  adjustPointByAdmin: mocks.adjustPointByAdmin,
}));

vi.mock("@/features/point/AdminPointAdjustmentHistory", () => ({
  default: ({ refreshKey }: { refreshKey: number }) => (
    <div data-testid="adjustment-history">history-{refreshKey}</div>
  ),
}));

vi.mock("@/lib/ui", () => ({
  useUI: () => ({
    askConfirm: mocks.askConfirm,
    showToast: mocks.showToast,
  }),
}));

const selectedUser = {
  id: 10,
  email: "green@example.com",
  nickname: "초록",
  name: "김초록",
  role: "USER" as const,
  status: "ACTIVE" as const,
  createdAt: "2026-08-01T10:00:00",
};

const usersPage = {
  content: [selectedUser],
  number: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
  numberOfElements: 1,
  first: true,
  last: true,
  empty: false,
};

const wallet = {
  userId: 10,
  paidPoint: 3000,
  freePoint: 1000,
  balance: 4000,
  updatedAt: "2026-08-03T10:00:00",
};

const adjustmentResult = {
  transactionId: 31,
  userId: 10,
  currencyType: "FREE" as const,
  amount: 1000,
  adjustmentReason: "SPECIAL_EVENT" as const,
  balanceAfter: 2000,
  paidPoint: 3000,
  freePoint: 2000,
  balance: 5000,
};

function AdminPointAdjustmentPanel({
  accessToken,
  adminUserId = 77,
}: {
  accessToken: string | null;
  adminUserId?: number;
}) {
  return (
    <AdminPointAdjustmentPanelComponent
      accessToken={accessToken}
      adminUserId={adminUserId}
    />
  );
}

async function selectUser(accessToken = "admin-token") {
  fireEvent.click(
    await screen.findByRole("button", { name: "초록 회원 선택" }),
  );
  await waitFor(() =>
    expect(mocks.getWalletByAdmin).toHaveBeenCalledWith(accessToken, 10),
  );
  await screen.findByText("4,000P");
}

interface FillFormOptions {
  mode?: "GRANT" | "DEDUCT";
  amount?: string;
  reason?: "SPECIAL_EVENT" | "OUTSTANDING_MEMBER" | "FRAUD_PENALTY";
}

function fillForm(options: FillFormOptions = {}) {
  const mode = options.mode ?? "GRANT";
  const amount = options.amount ?? "1000";
  const reason =
    options.reason ?? (mode === "DEDUCT" ? "FRAUD_PENALTY" : "SPECIAL_EVENT");
  fireEvent.change(screen.getByLabelText("조정 방식"), {
    target: { value: mode },
  });
  fireEvent.change(screen.getByLabelText("조정 사유"), {
    target: { value: reason },
  });
  fireEvent.change(screen.getByLabelText(/조정 포인트/), {
    target: { value: amount },
  });
}

describe("AdminPointAdjustmentPanel", () => {
  beforeEach(() => {
    sessionStorage.clear();
    mocks.getAdminUsers.mockReset().mockResolvedValue(usersPage);
    mocks.getWalletByAdmin.mockReset().mockResolvedValue(wallet);
    mocks.adjustPointByAdmin.mockReset();
    mocks.askConfirm.mockReset();
    mocks.showToast.mockReset();
    mocks.askConfirm.mockImplementation((options) => options.onOk?.());
  });

  it("목록에서 회원을 선택한 뒤 포인트를 지급하고 최신 잔액을 확인한다", async () => {
    mocks.adjustPointByAdmin.mockResolvedValue(adjustmentResult);
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fillForm();

    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    expect(mocks.askConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.stringContaining("사유: 특별 이벤트"),
      }),
    );
    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1),
    );
    expect(mocks.adjustPointByAdmin).toHaveBeenCalledWith(
      "admin-token",
      {
        userId: 10,
        currencyType: "FREE",
        amount: 1000,
        adjustmentReason: "SPECIAL_EVENT",
      },
      expect.any(String),
    );
    expect((await screen.findAllByText("5,000P")).length).toBeGreaterThan(0);
    expect(screen.getByText("원장 번호 #31")).toBeInTheDocument();
    expect(screen.getByTestId("adjustment-history")).toHaveTextContent(
      "history-1",
    );
  });

  it("차감 전 회원과 현재 잔액을 확인하고 서버에는 음수 금액을 전달한다", async () => {
    mocks.adjustPointByAdmin.mockResolvedValue({
      ...adjustmentResult,
      amount: -500,
      adjustmentReason: "FRAUD_PENALTY" as const,
      balanceAfter: 500,
      freePoint: 500,
      balance: 3500,
    });
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fillForm({ mode: "DEDUCT", amount: "500" });

    fireEvent.click(screen.getByRole("button", { name: "포인트 차감" }));

    expect(mocks.askConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        body: expect.stringContaining("초록(green@example.com)"),
      }),
    );
    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1),
    );
    expect(mocks.adjustPointByAdmin).toHaveBeenCalledWith(
      "admin-token",
      {
        userId: 10,
        currencyType: "FREE",
        amount: -500,
        adjustmentReason: "FRAUD_PENALTY",
      },
      expect.any(String),
    );
  });

  it("조정 유형을 바꾸면 이전 사유를 초기화하고 허용된 사유만 제공한다", async () => {
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();

    fireEvent.change(screen.getByLabelText("조정 사유"), {
      target: { value: "OUTSTANDING_MEMBER" },
    });
    fireEvent.change(screen.getByLabelText("조정 방식"), {
      target: { value: "DEDUCT" },
    });

    expect(screen.getByLabelText("조정 사유")).toHaveValue("");
    expect(
      screen.queryByRole("option", { name: "특별 이벤트" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "부정행위 패널티" }),
    ).toBeInTheDocument();
  });

  it("사유를 선택하지 않으면 포인트 조정 요청을 보내지 않는다", async () => {
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fireEvent.change(screen.getByLabelText(/조정 포인트/), {
      target: { value: "1000" },
    });

    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "포인트 조정 사유를 선택해 주세요.",
    );
    expect(mocks.askConfirm).not.toHaveBeenCalled();
    expect(mocks.adjustPointByAdmin).not.toHaveBeenCalled();
  });

  it("응답을 받지 못한 요청은 회원을 다시 선택해도 같은 멱등키로 재시도한다", async () => {
    mocks.adjustPointByAdmin
      .mockRejectedValueOnce(
        new ApiError(
          "UNKNOWN_ERROR",
          "네트워크 응답을 확인하지 못했어요.",
          500,
        ),
      )
      .mockResolvedValueOnce(adjustmentResult);
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fillForm();

    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "네트워크 응답을 확인하지 못했어요.",
    );
    fireEvent.click(screen.getByRole("button", { name: "초록 회원 선택" }));
    await waitFor(() =>
      expect(mocks.getWalletByAdmin).toHaveBeenCalledTimes(2),
    );
    await screen.findByText("4,000P");
    fillForm();
    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(2),
    );
    expect(mocks.adjustPointByAdmin.mock.calls[0][2]).toBe(
      mocks.adjustPointByAdmin.mock.calls[1][2],
    );
  });

  it("응답 유실 후 화면을 다시 열어도 같은 관리자의 동일 요청은 저장한 멱등키를 재사용한다", async () => {
    mocks.adjustPointByAdmin
      .mockRejectedValueOnce(new TypeError("network"))
      .mockResolvedValueOnce(adjustmentResult);
    const firstRender = render(
      <AdminPointAdjustmentPanel accessToken="admin-token" adminUserId={91} />,
    );
    await selectUser();
    fillForm();
    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1),
    );
    const firstKey = mocks.adjustPointByAdmin.mock.calls[0][2];

    firstRender.unmount();
    render(
      <AdminPointAdjustmentPanel accessToken="admin-token" adminUserId={91} />,
    );
    await selectUser();
    fillForm();
    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(2),
    );
    expect(mocks.adjustPointByAdmin.mock.calls[1][2]).toBe(firstKey);
    expect(sessionStorage.length).toBe(0);
  });

  it("관리자 계정이 바뀌면 다른 관리자의 조정 멱등키를 재사용하지 않는다", async () => {
    mocks.adjustPointByAdmin
      .mockRejectedValueOnce(new TypeError("network"))
      .mockResolvedValueOnce(adjustmentResult);
    const firstRender = render(
      <AdminPointAdjustmentPanel accessToken="admin-a" adminUserId={91} />,
    );
    await selectUser("admin-a");
    fillForm();
    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));
    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1),
    );
    const firstKey = mocks.adjustPointByAdmin.mock.calls[0][2];

    firstRender.unmount();
    render(
      <AdminPointAdjustmentPanel accessToken="admin-b" adminUserId={92} />,
    );
    await selectUser("admin-b");
    fillForm();
    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(2),
    );
    expect(mocks.adjustPointByAdmin.mock.calls[1][2]).not.toBe(firstKey);
  });

  it("기존 조정의 멱등 재생 응답에 사유가 없어도 표시한다", async () => {
    mocks.adjustPointByAdmin.mockResolvedValue({
      ...adjustmentResult,
      adjustmentReason: null,
    });
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fillForm();

    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    expect(await screen.findByText("사유 기록 없음")).toBeInTheDocument();
  });

  it("조정 요청이 진행되는 동안 회원 선택을 잠근다", async () => {
    let resolveAdjustment:
      ((result: typeof adjustmentResult) => void) | undefined;
    mocks.adjustPointByAdmin.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveAdjustment = resolve;
        }),
    );
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fillForm();

    fireEvent.click(screen.getByRole("button", { name: "포인트 지급" }));

    await waitFor(() =>
      expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1),
    );
    expect(
      screen.getByRole("button", { name: "초록 회원 선택" }),
    ).toBeDisabled();
    expect(screen.getByLabelText("회원 검색")).toBeDisabled();

    resolveAdjustment?.(adjustmentResult);
    expect(await screen.findByText("원장 번호 #31")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "초록 회원 선택" }),
    ).not.toBeDisabled();
  });
});
