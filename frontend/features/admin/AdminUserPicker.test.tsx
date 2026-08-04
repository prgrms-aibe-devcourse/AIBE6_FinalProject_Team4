import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import AdminUserPicker from '@/features/admin/AdminUserPicker';

const mocks = vi.hoisted(() => ({
  getAdminUsers: vi.fn(),
}));

vi.mock('@/features/admin/user-api', () => ({
  getAdminUsers: mocks.getAdminUsers,
}));

const user = {
  id: 10,
  email: 'green@example.com',
  nickname: '초록',
  name: '김초록',
  role: 'USER' as const,
  status: 'ACTIVE' as const,
  createdAt: '2026-08-01T10:00:00',
};

const page = {
  content: [user],
  number: 0,
  size: 10,
  totalElements: 11,
  totalPages: 2,
  numberOfElements: 1,
  first: true,
  last: false,
  empty: false,
};

describe('AdminUserPicker', () => {
  beforeEach(() => {
    mocks.getAdminUsers.mockReset().mockResolvedValue(page);
  });

  it('활성 회원을 기본 조회하고 다음 페이지를 요청한다', async () => {
    render(<AdminUserPicker accessToken="admin-token" onSelect={vi.fn()} />);

    await screen.findByText('green@example.com');
    expect(mocks.getAdminUsers).toHaveBeenCalledWith(expect.objectContaining({
      accessToken: 'admin-token',
      status: 'ACTIVE',
      page: 0,
      size: 10,
    }));

    fireEvent.click(screen.getByRole('button', { name: '다음' }));
    await waitFor(() => expect(mocks.getAdminUsers).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1 }),
    ));
  });

  it('검색어를 지연 적용하고 목록에서 회원을 선택한다', async () => {
    const onSelect = vi.fn();
    render(<AdminUserPicker accessToken="admin-token" onSelect={onSelect} />);
    await screen.findByText('green@example.com');

    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '초록' } });
    await waitFor(() => expect(mocks.getAdminUsers).toHaveBeenCalledWith(
      expect.objectContaining({ keyword: '초록', page: 0 }),
    ), { timeout: 1000 });

    fireEvent.click(screen.getByRole('button', { name: '초록 회원 선택' }));
    expect(onSelect).toHaveBeenCalledWith(user);
  });
});
