import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminChargeProductPanelComponent from "@/features/payment/AdminChargeProductPanel";
import { ApiError } from "@/lib/api";

const mocks = vi.hoisted(() => ({
  getAdminChargeProducts: vi.fn(),
  createAdminChargeProduct: vi.fn(),
  updateAdminChargeProduct: vi.fn(),
  askConfirm: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock("@/features/payment/admin-charge-product-api", () => ({
  getAdminChargeProducts: mocks.getAdminChargeProducts,
  createAdminChargeProduct: mocks.createAdminChargeProduct,
  updateAdminChargeProduct: mocks.updateAdminChargeProduct,
}));

vi.mock("@/lib/ui", () => ({
  useUI: () => ({
    askConfirm: mocks.askConfirm,
    showToast: mocks.showToast,
  }),
}));

const products = [
  {
    id: 1,
    version: 0,
    name: "새싹 1,000P",
    price: 1000,
    pointAmount: 1000,
    isActive: true,
  },
  {
    id: 2,
    version: 3,
    name: "이벤트 5,500P",
    price: 5000,
    pointAmount: 5500,
    isActive: false,
  },
];

function AdminChargeProductPanel({
  accessToken,
  adminUserId = 1,
}: {
  accessToken: string;
  adminUserId?: number;
}) {
  return (
    <AdminChargeProductPanelComponent
      accessToken={accessToken}
      adminUserId={adminUserId}
    />
  );
}

describe("AdminChargeProductPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mocks.getAdminChargeProducts.mockResolvedValue(products);
    mocks.createAdminChargeProduct.mockResolvedValue(products[0]);
    mocks.updateAdminChargeProduct.mockResolvedValue(products[0]);
    mocks.askConfirm.mockImplementation((options) => options.onOk?.());
  });

  it("활성·비활성 충전 상품을 함께 표시한다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);

    expect(await screen.findByText("새싹 1,000P")).toBeInTheDocument();
    expect(screen.getByText("이벤트 5,500P")).toBeInTheDocument();
    expect(screen.getAllByText("판매 중").length).toBeGreaterThan(0);
    expect(screen.getAllByText("판매 중지").length).toBeGreaterThan(0);
  });

  it("검증한 값을 사용해 충전 상품을 추가하고 목록을 다시 조회한다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "  여름 특별 충전  " },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "3000" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "3300" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));

    await waitFor(() =>
      expect(mocks.createAdminChargeProduct).toHaveBeenCalledWith(
        "admin-token",
        expect.any(String),
        {
          name: "여름 특별 충전",
          price: 3000,
          pointAmount: 3300,
          isActive: true,
        },
      ),
    );
    expect(mocks.getAdminChargeProducts).toHaveBeenCalledTimes(2);
  });

  it("생성 응답이 유실되면 같은 요청에 동일한 멱등키를 재사용한다", async () => {
    mocks.createAdminChargeProduct
      .mockRejectedValueOnce(new ApiError("UNKNOWN_ERROR", "응답 유실", 500))
      .mockResolvedValueOnce(products[0]);
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "재시도 상품" },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "3000" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "3300" },
    });
    const submit = screen.getByRole("button", { name: "충전 상품 추가" });
    fireEvent.click(submit);
    await waitFor(() =>
      expect(mocks.createAdminChargeProduct).toHaveBeenCalledTimes(1),
    );
    fireEvent.click(submit);
    await waitFor(() =>
      expect(mocks.createAdminChargeProduct).toHaveBeenCalledTimes(2),
    );

    const firstKey = mocks.createAdminChargeProduct.mock.calls[0][1];
    const secondKey = mocks.createAdminChargeProduct.mock.calls[1][1];
    expect(firstKey).toBe(secondKey);
  });

  it("생성 응답 유실 후 화면을 다시 열어도 저장한 멱등키를 복구한다", async () => {
    mocks.createAdminChargeProduct
      .mockRejectedValueOnce(new TypeError("network"))
      .mockResolvedValueOnce(products[0]);
    const firstRender = render(
      <AdminChargeProductPanel accessToken="admin-token" />,
    );
    await screen.findByText("새싹 1,000P");

    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "새로고침 재시도 상품" },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "4000" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "4400" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));
    await waitFor(() =>
      expect(mocks.createAdminChargeProduct).toHaveBeenCalledTimes(1),
    );
    const firstKey = mocks.createAdminChargeProduct.mock.calls[0][1];

    firstRender.unmount();
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");
    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "새로고침 재시도 상품" },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "4000" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "4400" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));

    await waitFor(() =>
      expect(mocks.createAdminChargeProduct).toHaveBeenCalledTimes(2),
    );
    expect(mocks.createAdminChargeProduct.mock.calls[1][1]).toBe(firstKey);
  });

  it("관리자 계정이 바뀌면 다른 계정의 생성 멱등키를 재사용하지 않는다", async () => {
    mocks.createAdminChargeProduct
      .mockRejectedValueOnce(new TypeError("network"))
      .mockResolvedValueOnce(products[0]);
    const firstRender = render(
      <AdminChargeProductPanel accessToken="admin-a" adminUserId={11} />,
    );
    await screen.findByText("새싹 1,000P");

    for (const [label, value] of [
      ["상품명", "계정 분리 상품"],
      ["결제 금액(원)", "5000"],
      ["지급 포인트(P)", "5500"],
    ]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));
    await waitFor(() =>
      expect(mocks.createAdminChargeProduct).toHaveBeenCalledTimes(1),
    );
    const firstKey = mocks.createAdminChargeProduct.mock.calls[0][1];

    firstRender.unmount();
    render(<AdminChargeProductPanel accessToken="admin-b" adminUserId={22} />);
    await screen.findByText("새싹 1,000P");
    for (const [label, value] of [
      ["상품명", "계정 분리 상품"],
      ["결제 금액(원)", "5000"],
      ["지급 포인트(P)", "5500"],
    ]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));

    await waitFor(() =>
      expect(mocks.createAdminChargeProduct).toHaveBeenCalledTimes(2),
    );
    expect(mocks.createAdminChargeProduct.mock.calls[1][1]).not.toBe(firstKey);
  });

  it("조정 범위를 벗어난 값을 API로 전송하지 않는다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "잘못된 상품" },
    });
    const priceInput = screen.getByLabelText("결제 금액(원)");
    fireEvent.change(priceInput, {
      target: { value: "0" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "1000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));

    expect(mocks.createAdminChargeProduct).not.toHaveBeenCalled();
    expect(priceInput).toHaveAttribute("aria-invalid", "true");
    expect(
      screen.getByText("결제 금액은 1 이상의 정수로 입력해 주세요."),
    ).toBeInTheDocument();
    expect(mocks.showToast).toHaveBeenCalledWith(
      "결제 금액은 1 이상의 정수로 입력해 주세요.",
      "err",
    );
  });

  it("결제 금액 대비 지급 포인트 비율이 150%를 넘으면 API로 전송하지 않는다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "과다 지급 상품" },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "1000" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "1501" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));

    expect(mocks.createAdminChargeProduct).not.toHaveBeenCalled();
    expect(
      screen.getByText(
        "지급 포인트는 결제 금액의 100% 이상 150% 이하로 입력해 주세요.",
      ),
    ).toBeInTheDocument();
  });

  it("판매 중지 전 확인하고 전체 필드로 수정 요청한다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.click(
      screen.getByRole("button", { name: "새싹 1,000P 판매 중지" }),
    );

    expect(mocks.askConfirm).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "충전 상품 판매를 중지할까요?",
      }),
    );
    await waitFor(() =>
      expect(mocks.updateAdminChargeProduct).toHaveBeenCalledWith(
        "admin-token",
        1,
        {
          name: "새싹 1,000P",
          price: 1000,
          pointAmount: 1000,
          isActive: false,
          version: 0,
        },
      ),
    );
  });

  it("기존 충전 상품을 불러와 수정한다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.click(screen.getByRole("button", { name: "새싹 1,000P 수정" }));
    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "새싹 1,200P" },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "1200" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "1250" },
    });
    fireEvent.click(screen.getByRole("button", { name: "수정 저장" }));

    await waitFor(() =>
      expect(mocks.updateAdminChargeProduct).toHaveBeenCalledWith(
        "admin-token",
        1,
        {
          name: "새싹 1,200P",
          price: 1200,
          pointAmount: 1250,
          isActive: true,
          version: 0,
        },
      ),
    );
    expect(mocks.getAdminChargeProducts).toHaveBeenCalledTimes(2);
  });

  it("편집 중인 상품은 판매 상태를 변경할 수 없다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.click(screen.getByRole("button", { name: "새싹 1,000P 수정" }));

    const statusButton = screen.getByRole("button", {
      name: "새싹 1,000P 판매 중지",
    });
    expect(statusButton).toBeDisabled();
    fireEvent.click(statusButton);
    expect(mocks.askConfirm).not.toHaveBeenCalled();
  });

  it("목록 조회 실패 후 다시 시도할 수 있다", async () => {
    mocks.getAdminChargeProducts
      .mockRejectedValueOnce(
        new ApiError("UNKNOWN_ERROR", "목록 조회 실패", 500),
      )
      .mockResolvedValueOnce(products);
    render(<AdminChargeProductPanel accessToken="admin-token" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "목록 조회 실패",
    );
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("새싹 1,000P")).toBeInTheDocument();
  });

  it("비활성 상품을 현재 버전으로 다시 판매한다", async () => {
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("이벤트 5,500P");

    fireEvent.click(
      screen.getByRole("button", { name: "이벤트 5,500P 판매 시작" }),
    );

    expect(mocks.askConfirm).toHaveBeenCalledWith(
      expect.objectContaining({ title: "충전 상품을 판매할까요?" }),
    );
    await waitFor(() =>
      expect(mocks.updateAdminChargeProduct).toHaveBeenCalledWith(
        "admin-token",
        2,
        {
          name: "이벤트 5,500P",
          price: 5000,
          pointAmount: 5500,
          isActive: true,
          version: 3,
        },
      ),
    );
  });

  it("상품 저장 중 다른 mutation을 시작하지 않는다", async () => {
    const saveRequest = deferred<(typeof products)[number]>();
    mocks.createAdminChargeProduct.mockReturnValue(saveRequest.promise);
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "새 상품" },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "1000" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "1000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));

    expect(
      screen.getByRole("button", { name: "새싹 1,000P 판매 중지" }),
    ).toBeDisabled();
    fireEvent.click(
      screen.getByRole("button", { name: "새싹 1,000P 판매 중지" }),
    );
    expect(mocks.askConfirm).not.toHaveBeenCalled();

    saveRequest.resolve(products[0]);
    await waitFor(() =>
      expect(mocks.getAdminChargeProducts).toHaveBeenCalledTimes(2),
    );
  });

  it("늦게 도착한 이전 조회 응답으로 최신 목록을 덮지 않는다", async () => {
    const staleRequest = deferred<typeof products>();
    const latestProducts = [{ ...products[0], name: "최신 상품", version: 1 }];
    mocks.getAdminChargeProducts
      .mockResolvedValueOnce(products)
      .mockReturnValueOnce(staleRequest.promise)
      .mockResolvedValueOnce(latestProducts);
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.click(screen.getByRole("button", { name: "새로고침" }));
    fireEvent.change(screen.getByLabelText("상품명"), {
      target: { value: "추가 상품" },
    });
    fireEvent.change(screen.getByLabelText("결제 금액(원)"), {
      target: { value: "2000" },
    });
    fireEvent.change(screen.getByLabelText("지급 포인트(P)"), {
      target: { value: "2200" },
    });
    fireEvent.click(screen.getByRole("button", { name: "충전 상품 추가" }));
    expect(await screen.findByText("최신 상품")).toBeInTheDocument();

    staleRequest.resolve(products);
    await Promise.resolve();
    expect(screen.getByText("최신 상품")).toBeInTheDocument();
    expect(screen.queryByText("새싹 1,000P")).not.toBeInTheDocument();
  });

  it("편집 중 서버 버전이 변경되면 편집을 취소하고 최신 값을 표시한다", async () => {
    const latestProducts = [
      { ...products[0], name: "서버에서 변경된 상품", version: 1 },
    ];
    mocks.getAdminChargeProducts
      .mockResolvedValueOnce(products)
      .mockResolvedValueOnce(latestProducts);
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.click(screen.getByRole("button", { name: "새싹 1,000P 수정" }));
    fireEvent.click(screen.getByRole("button", { name: "새로고침" }));

    expect(await screen.findByText("서버에서 변경된 상품")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "충전 상품 추가" }),
    ).toBeInTheDocument();
    expect(mocks.showToast).toHaveBeenCalledWith(
      "편집 중인 상품이 변경되어 최신 목록을 불러왔어요. 다시 수정해 주세요.",
      "err",
    );
  });

  it("수정 중 낙관적 락 충돌이 발생하면 편집을 취소하고 목록을 갱신한다", async () => {
    mocks.updateAdminChargeProduct.mockRejectedValueOnce(
      new ApiError(
        "COMMON_OPTIMISTIC_LOCK_CONFLICT",
        "이미 변경된 상품입니다.",
        409,
      ),
    );
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    fireEvent.click(screen.getByRole("button", { name: "새싹 1,000P 수정" }));
    fireEvent.submit(screen.getByRole("form", { name: "충전 상품 입력" }));

    await waitFor(() =>
      expect(mocks.showToast).toHaveBeenCalledWith(
        "다른 관리자가 상품을 변경했어요. 최신 목록에서 다시 수정해 주세요.",
        "err",
      ),
    );
    expect(mocks.getAdminChargeProducts).toHaveBeenCalledTimes(2);
    expect(
      screen.getByRole("button", { name: "충전 상품 추가" }),
    ).toBeInTheDocument();
  });

  it("상태 변경 실패 후 잠금을 풀어 다시 시도할 수 있다", async () => {
    mocks.updateAdminChargeProduct
      .mockRejectedValueOnce(new ApiError("UNKNOWN_ERROR", "변경 실패", 500))
      .mockResolvedValueOnce(products[0]);
    render(<AdminChargeProductPanel accessToken="admin-token" />);
    await screen.findByText("새싹 1,000P");

    const statusButton = screen.getByRole("button", {
      name: "새싹 1,000P 판매 중지",
    });
    fireEvent.click(statusButton);
    await waitFor(() =>
      expect(mocks.showToast).toHaveBeenCalledWith("변경 실패", "err"),
    );
    expect(statusButton).toBeEnabled();

    fireEvent.click(statusButton);
    await waitFor(() =>
      expect(mocks.updateAdminChargeProduct).toHaveBeenCalledTimes(2),
    );
  });
});

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, resolve, reject };
}
