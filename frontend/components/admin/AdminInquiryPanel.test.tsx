import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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
    // "전체" 조회는 상태 우선순위로 재정렬하기 위해 한 번에 다 받아와 클라이언트에서
    // 페이지네이션하므로, 서버 페이지네이션/롤백 경로를 그대로 검증하려면 특정 상태
    // (대기)로 필터링한 상태에서 테스트한다.
    const firstPageItem = { ...openInquiry, id: 1, title: "결제가 안 돼요" };
    const secondPageItem = { ...openInquiry, id: 3, title: "환불 문의" };

    mocks.getInquiriesForAdmin
      .mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0 }) // 초기 진입: 전체
      .mockResolvedValueOnce({ content: [firstPageItem], totalElements: 2, totalPages: 2 }) // 대기, page 0
      .mockResolvedValueOnce({ content: [secondPageItem], totalElements: 2, totalPages: 2 }) // 대기, page 1
      .mockResolvedValueOnce({ content: [], totalElements: 1, totalPages: 1 }) // 답변 후 재조회: 대기, page 1
      .mockResolvedValueOnce({ content: [firstPageItem], totalElements: 1, totalPages: 1 }); // 롤백: 대기, page 0
    mocks.answerInquiry.mockResolvedValue({ ...secondPageItem, status: "ANSWERED" });

    render(<AdminInquiryPanel accessToken="token" />);
    fireEvent.click(await screen.findByRole("button", { name: "대기" }));
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
    expect(mocks.getInquiriesForAdmin).toHaveBeenCalledTimes(5);
    expect(mocks.getInquiriesForAdmin).toHaveBeenLastCalledWith("token", "OPEN", 0, 10, expect.anything(), "createdAt,ASC");
  });

  it("뒤처진 응답이 늦게 도착해도 사용자가 이미 이동한 유효한 페이지를 건드리지 않는다", async () => {
    // 페이지네이션 버튼은 loading 중엔 disabled라 답변 후 재조회가 끝나기 전엔 누를 수 없다 —
    // 하지만 상태 필터 칩은 disabled 처리가 없어서, 재조회가 아직 안 끝난 사이에도 클릭할 수 있다.
    // 그렇게 실제로 두 요청이 동시에 떠 있는 상태를 만들어 재현한다.
    // "전체"는 한 번에 다 받아와 클라이언트에서 페이지네이션하므로, 서버 페이지네이션
    // 경쟁 상태를 검증하려면 특정 상태(대기 → 답변완료)로 필터링해서 테스트한다.
    const itemA = { ...openInquiry, id: 1, title: "A 문의" };
    const itemB = { ...openInquiry, id: 2, title: "B 문의" };
    const itemC = { ...openInquiry, id: 3, title: "C 문의" };
    const itemD = { ...answeredInquiry, id: 4, title: "D 문의" };
    const itemE = { ...answeredInquiry, id: 5, title: "E 문의" };

    let resolveStale!: (value: { content: unknown[]; totalElements: number; totalPages: number }) => void;
    const staleResponse = new Promise((resolve) => {
      resolveStale = resolve;
    });

    mocks.getInquiriesForAdmin
      .mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0 }) // 초기 진입: 전체
      .mockResolvedValueOnce({ content: [itemA], totalElements: 3, totalPages: 3 }) // 필터 전환: 대기, page 0
      .mockResolvedValueOnce({ content: [itemB], totalElements: 3, totalPages: 3 }) // 다음: 대기, page 1
      .mockResolvedValueOnce({ content: [itemC], totalElements: 3, totalPages: 3 }) // 다음: 대기, page 2
      .mockImplementationOnce(() => staleResponse) // 답변 후 재조회(대기, page 2) — 일부러 응답을 늦춤
      .mockResolvedValueOnce({ content: [itemD], totalElements: 2, totalPages: 2 }) // 필터 전환: 답변완료, page 0
      .mockResolvedValueOnce({ content: [itemE], totalElements: 2, totalPages: 2 }); // 다음: 답변완료, page 1
    mocks.answerInquiry.mockResolvedValue({ ...itemC, status: "ANSWERED" });

    render(<AdminInquiryPanel accessToken="token" />);
    fireEvent.click(await screen.findByRole("button", { name: "대기" }));
    await screen.findByText("A 문의");

    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    await screen.findByText("B 문의");

    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    await screen.findByText("C 문의");

    fireEvent.click(screen.getByRole("button", { name: "답변하기" }));
    fireEvent.change(screen.getByPlaceholderText("답변 내용을 입력해 주세요."), {
      target: { value: "확인했습니다." },
    });
    fireEvent.click(screen.getByRole("button", { name: "답변 등록" }));

    // 답변 후 재조회(대기, page 2)가 나갈 때까지 기다린 다음, 그 응답이 오기 전에 다른 필터로 옮겨서
    // 뒤이어 유효한 다른 페이지(답변완료, page 1)까지 이동해 둔다.
    await waitFor(() => expect(mocks.getInquiriesForAdmin).toHaveBeenCalledTimes(5));
    fireEvent.click(screen.getByRole("button", { name: "답변완료" }));
    await screen.findByText("D 문의");

    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    await screen.findByText("E 문의");

    // 뒤늦게 도착한 "대기, page 2" 응답(빈 결과) — 가드가 없으면 지금 보고 있는 유효한
    // "답변완료, page 1"에서 최신 page state(1)를 기준으로 한 칸 더 물러나 page 0으로 밀어낸다.
    resolveStale({ content: [], totalElements: 2, totalPages: 1 });
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(screen.getByText("E 문의")).toBeInTheDocument();
    expect(screen.queryByText("조건에 맞는 문의가 없어요")).not.toBeInTheDocument();
    expect(mocks.getInquiriesForAdmin).toHaveBeenCalledTimes(7);
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
