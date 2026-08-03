import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/lib/api';
import AdminPointAdjustmentPanel from '@/features/point/AdminPointAdjustmentPanel';

const mocks = vi.hoisted(() => ({
  adjustPointByAdmin: vi.fn(),
  askConfirm: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock('@/features/point/api', () => ({
  adjustPointByAdmin: mocks.adjustPointByAdmin,
}));

vi.mock('@/lib/ui', () => ({
  useUI: () => ({
    askConfirm: mocks.askConfirm,
    showToast: mocks.showToast,
  }),
}));

const adjustmentResult = {
  transactionId: 31,
  userId: 10,
  currencyType: 'FREE' as const,
  amount: 1000,
  balanceAfter: 1200,
  paidPoint: 3000,
  freePoint: 1200,
  balance: 4200,
};

function fillForm({ mode = 'GRANT', amount = '1000' } = {}) {
  fireEvent.change(screen.getByLabelText('대상 회원 ID'), { target: { value: '10' } });
  fireEvent.change(screen.getByLabelText('조정 방식'), { target: { value: mode } });
  fireEvent.change(screen.getByLabelText(/조정 포인트/), { target: { value: amount } });
}

describe('AdminPointAdjustmentPanel', () => {
  beforeEach(() => {
    mocks.adjustPointByAdmin.mockReset();
    mocks.askConfirm.mockReset();
    mocks.showToast.mockReset();
    mocks.askConfirm.mockImplementation((options) => options.onOk?.());
  });

  it('관리자가 포인트를 지급하고 최신 잔액을 확인한다', async () => {
    mocks.adjustPointByAdmin.mockResolvedValue(adjustmentResult);
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    fillForm();

    fireEvent.click(screen.getByRole('button', { name: '포인트 지급' }));

    await waitFor(() => expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1));
    expect(mocks.adjustPointByAdmin).toHaveBeenCalledWith(
      'admin-token',
      { userId: 10, currencyType: 'FREE', amount: 1000 },
      expect.any(String),
    );
    expect(await screen.findByText('4,200P')).toBeInTheDocument();
    expect(screen.getByText('원장 번호 #31')).toBeInTheDocument();
  });

  it('차감 전 확인을 받고 서버에는 음수 금액을 전달한다', async () => {
    mocks.adjustPointByAdmin.mockResolvedValue({
      ...adjustmentResult,
      amount: -500,
      balanceAfter: 700,
      freePoint: 700,
      balance: 3700,
    });
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    fillForm({ mode: 'DEDUCT', amount: '500' });

    fireEvent.click(screen.getByRole('button', { name: '포인트 차감' }));

    expect(mocks.askConfirm).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1));
    expect(mocks.adjustPointByAdmin).toHaveBeenCalledWith(
      'admin-token',
      { userId: 10, currencyType: 'FREE', amount: -500 },
      expect.any(String),
    );
  });

  it('응답을 받지 못한 동일 요청은 같은 멱등키로 재시도한다', async () => {
    mocks.adjustPointByAdmin
      .mockRejectedValueOnce(new ApiError('UNKNOWN_ERROR', '네트워크 응답을 확인하지 못했어요.', 500))
      .mockResolvedValueOnce(adjustmentResult);
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    fillForm();

    fireEvent.click(screen.getByRole('button', { name: '포인트 지급' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('네트워크 응답을 확인하지 못했어요.');
    fireEvent.click(screen.getByRole('button', { name: '포인트 지급' }));

    await waitFor(() => expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(2));
    expect(mocks.adjustPointByAdmin.mock.calls[0][2]).toBe(
      mocks.adjustPointByAdmin.mock.calls[1][2],
    );
  });
});
