import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AdminInquiryPanel from "@/components/admin/AdminInquiryPanel";
import { ApiError } from "@/lib/api";

const mocks = vi.hoisted(() => ({
  getInquiriesForAdmin: vi.fn(),
  answerInquiry: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock("@/lib/inquiry-api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/inquiry-api")>()),
  getInquiriesForAdmin: mocks.getInquiriesForAdmin,
  answerInquiry: mocks.answerInquiry,
}));
vi.mock("@/lib/ui", () => ({
  useUI: () => ({ showToast: mocks.showToast, askConfirm: vi.fn() }),
}));

const openInquiry = {
  id: 1,
  userId: 10,
  userName: "김초록",
  category: "PAYMENT" as const,
  title: "결제가 안 돼요",
  content: "카드 결제 시 오류가 나요.",
  status: "OPEN" as const,
  createdAt: "2026-08-10T09:00:00",
  answerContent: null,
  answerAdminId: null,
  answerAdminName: null,
  answeredAt: null,
};

const answeredInquiry = {
  ...openInquiry,
  id: 2,
  userName: "박노랑",
  title: "배송 문의",
  status: "ANSWERED" as const,
  answerContent: "확인 후 재배송해 드렸습니다.",
  answerAdminId: 99,
  answerAdminName: "운영자",
  answeredAt: "2026-08-11T10:00:00",
};

describe("AdminInquiryPanel", () => {
  beforeEach(() => vi.clearAllMocks());

  it("문의 목록에 작성자와 상태를 표시한다", async () => {
    mocks.getInquiriesForAdmin.mockResolvedValue({
      content: [openInquiry, answeredInquiry],
      totalElements: 2,
      totalPages: 1,
    });

    render(<AdminInquiryPanel accessToken="token" />);

    expect(await screen.findByText("결제가 안 돼요")).toBeInTheDocument();
    expect(screen.getByText("김초록")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "답변하기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "답변 보기" })).toBeInTheDocument();
  });

  it("답변 등록 후 목록을 새로고침한다", async () => {
    mocks.getInquiriesForAdmin.mockResolvedValue({
      content: [openInquiry],
      totalElements: 1,
      totalPages: 1,
    });
    mocks.answerInquiry.mockResolvedValue({ ...openInquiry, status: "ANSWERED" });

    render(<AdminInquiryPanel accessToken="token" />);
    fireEvent.click(await screen.findByRole("button", { name: "답변하기" }));

    fireEvent.change(screen.getByPlaceholderText("답변 내용을 입력해 주세요."), {
      target: { value: "확인해 보겠습니다." },
    });
    fireEvent.click(screen.getByRole("button", { name: "답변 등록" }));

    await screen.findByText("결제가 안 돼요");
    expect(mocks.answerInquiry).toHaveBeenCalledWith(1, "확인해 보겠습니다.", "token");
    expect(mocks.getInquiriesForAdmin).toHaveBeenCalledTimes(2);
    expect(mocks.showToast).toHaveBeenCalledWith("답변을 등록했어요.");
  });

  it("다른 관리자가 먼저 답변해 409가 나면 목록을 새로고침하고 모달을 닫는다", async () => {
    mocks.getInquiriesForAdmin
      .mockResolvedValueOnce({ content: [openInquiry], totalElements: 1, totalPages: 1 })
      .mockResolvedValueOnce({
        content: [{ ...openInquiry, status: "ANSWERED", answerContent: "다른 관리자가 먼저 답변함" }],
        totalElements: 1,
        totalPages: 1,
      });
    mocks.answerInquiry.mockRejectedValue(
      new ApiError("INQUIRY_INVALID_STATE", "이미 답변이 완료된 문의입니다.", 409),
    );

    render(<AdminInquiryPanel accessToken="token" />);
    fireEvent.click(await screen.findByRole("button", { name: "답변하기" }));
    fireEvent.change(screen.getByPlaceholderText("답변 내용을 입력해 주세요."), {
      target: { value: "확인하겠습니다." },
    });
    fireEvent.click(screen.getByRole("button", { name: "답변 등록" }));

    expect(await screen.findByRole("button", { name: "답변 보기" })).toBeInTheDocument();
    expect(mocks.showToast).toHaveBeenCalledWith("이미 답변이 완료된 문의입니다.", "err");
    expect(mocks.getInquiriesForAdmin).toHaveBeenCalledTimes(2);
    expect(
      screen.queryByPlaceholderText("답변 내용을 입력해 주세요."),
    ).not.toBeInTheDocument();
  });

  it("마지막 페이지의 마지막 항목에 답변하면 이전 페이지로 물러난다", async () => {
    const firstPageItem = { ...openInquiry, id: 1, title: "결제가 안 돼요" };
    const secondPageItem = { ...openInquiry, id: 3, title: "환불 문의" };

    mocks.getInquiriesForAdmin
      .mockResolvedValueOnce({ content: [firstPageItem], totalElements: 2, totalPages: 2 })
      .mockResolvedValueOnce({ content: [secondPageItem], totalElements: 2, totalPages: 2 })
      .mockResolvedValueOnce({ content: [], totalElements: 1, totalPages: 1 })
      .mockResolvedValueOnce({ content: [firstPageItem], totalElements: 1, totalPages: 1 });
    mocks.answerInquiry.mockResolvedValue({ ...secondPageItem, status: "ANSWERED" });

    render(<AdminInquiryPanel accessToken="token" />);
    await screen.findByText("결제가 안 돼요");

    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    await screen.findByText("환불 문의");

    fireEvent.click(screen.getByRole("button", { name: "답변하기" }));
    fireEvent.change(screen.getByPlaceholderText("답변 내용을 입력해 주세요."), {
      target: { value: "환불 처리했습니다." },
    });
    fireEvent.click(screen.getByRole("button", { name: "답변 등록" }));

    expect(await screen.findByText("결제가 안 돼요")).toBeInTheDocument();
    expect(screen.queryByText("조건에 맞는 문의가 없어요")).not.toBeInTheDocument();
    expect(mocks.getInquiriesForAdmin).toHaveBeenCalledTimes(4);
    expect(mocks.getInquiriesForAdmin).toHaveBeenLastCalledWith("token", undefined, 0, 20, expect.anything());
  });

  it("답변완료 항목은 답변 내용을 읽기 전용으로 보여준다", async () => {
    mocks.getInquiriesForAdmin.mockResolvedValue({
      content: [answeredInquiry],
      totalElements: 1,
      totalPages: 1,
    });

    render(<AdminInquiryPanel accessToken="token" />);
    fireEvent.click(await screen.findByRole("button", { name: "답변 보기" }));

    expect(screen.getByText("확인 후 재배송해 드렸습니다.")).toBeInTheDocument();
    expect(
      screen.queryByPlaceholderText("답변 내용을 입력해 주세요."),
    ).not.toBeInTheDocument();
  });
});
