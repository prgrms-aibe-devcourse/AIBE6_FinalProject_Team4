import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/lib/api';
import AdminPointAdjustmentPanel from '@/features/point/AdminPointAdjustmentPanel';

const mocks = vi.hoisted(() => ({
  getAdminUsers: vi.fn(),
  getWalletByAdmin: vi.fn(),
  adjustPointByAdmin: vi.fn(),
  askConfirm: vi.fn(),
  showToast: vi.fn(),
}));

vi.mock('@/features/admin/user-api', () => ({
  getAdminUsers: mocks.getAdminUsers,
}));

vi.mock('@/features/point/api', () => ({
  getWalletByAdmin: mocks.getWalletByAdmin,
  adjustPointByAdmin: mocks.adjustPointByAdmin,
}));

vi.mock('@/features/point/AdminPointAdjustmentHistory', () => ({
  default: ({ refreshKey }: { refreshKey: number }) => (
    <div data-testid="adjustment-history">history-{refreshKey}</div>
  ),
}));

vi.mock('@/lib/ui', () => ({
  useUI: () => ({
    askConfirm: mocks.askConfirm,
    showToast: mocks.showToast,
  }),
}));

const selectedUser = {
  id: 10,
  email: 'green@example.com',
  nickname: '초록',
  name: '김초록',
  role: 'USER' as const,
  status: 'ACTIVE' as const,
  createdAt: '2026-08-01T10:00:00',
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
  updatedAt: '2026-08-03T10:00:00',
};

const adjustmentResult = {
  transactionId: 31,
  userId: 10,
  currencyType: 'FREE' as const,
  amount: 1000,
  balanceAfter: 2000,
  paidPoint: 3000,
  freePoint: 2000,
  balance: 5000,
};

async function selectUser() {
  fireEvent.click(await screen.findByRole('button', { name: '초록 회원 선택' }));
  await waitFor(() => expect(mocks.getWalletByAdmin).toHaveBeenCalledWith('admin-token', 10));
  await screen.findByText('4,000P');
}

function fillForm({ mode = 'GRANT', amount = '1000' } = {}) {
  fireEvent.change(screen.getByLabelText('조정 방식'), { target: { value: mode } });
  fireEvent.change(screen.getByLabelText(/조정 포인트/), { target: { value: amount } });
}

describe('AdminPointAdjustmentPanel', () => {
  beforeEach(() => {
    mocks.getAdminUsers.mockReset().mockResolvedValue(usersPage);
    mocks.getWalletByAdmin.mockReset().mockResolvedValue(wallet);
    mocks.adjustPointByAdmin.mockReset();
    mocks.askConfirm.mockReset();
    mocks.showToast.mockReset();
    mocks.askConfirm.mockImplementation((options) => options.onOk?.());
  });

  it('목록에서 회원을 선택한 뒤 포인트를 지급하고 최신 잔액을 확인한다', async () => {
    mocks.adjustPointByAdmin.mockResolvedValue(adjustmentResult);
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fillForm();

    fireEvent.click(screen.getByRole('button', { name: '포인트 지급' }));

    await waitFor(() => expect(mocks.adjustPointByAdmin).toHaveBeenCalledTimes(1));
    expect(mocks.adjustPointByAdmin).toHaveBeenCalledWith(
      'admin-token',
      { userId: 10, currencyType: 'FREE', amount: 1000 },
      expect.any(String),
    );
    expect((await screen.findAllByText('5,000P')).length).toBeGreaterThan(0);
    expect(screen.getByText('원장 번호 #31')).toBeInTheDocument();
    expect(screen.getByTestId('adjustment-history')).toHaveTextContent('history-1');
  });

  it('차감 전 회원과 현재 잔액을 확인하고 서버에는 음수 금액을 전달한다', async () => {
    mocks.adjustPointByAdmin.mockResolvedValue({
      ...adjustmentResult,
      amount: -500,
      balanceAfter: 500,
      freePoint: 500,
      balance: 3500,
    });
    render(<AdminPointAdjustmentPanel accessToken="admin-token" />);
    await selectUser();
    fillForm({ mode: 'DEDUCT', amount: '500' });

    fireEvent.click(screen.getByRole('button', { name: '포인트 차감' }));

    expect(mocks.askConfirm).toHaveBeenCalledWith(expect.objectContaining({
      body: expect.stringContaining('초록(green@example.com)'),
    }));
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
    await selectUser();
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
