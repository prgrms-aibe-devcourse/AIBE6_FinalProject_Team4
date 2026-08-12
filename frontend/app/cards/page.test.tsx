import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Cards from './page';
import { getCards } from '@/lib/card-api';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));

vi.mock('@/lib/store', () => ({
  fmt: (value: number) => value.toLocaleString('ko-KR'),
  useStore: () => ({
    state: { accessToken: 'access-token' },
    hydrated: true,
  }),
}));

vi.mock('@/lib/card-api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/card-api')>();
  return {
    ...original,
    getCards: vi.fn(),
  };
});

const mockedGetCards = vi.mocked(getCards);

function card(id: number, name: string, ownedCount: number) {
  return {
    id,
    name,
    pointPrice: 100,
    exchangeProductId: id,
    exchangeProductName: `${name} 교환 상품`,
    exchangeProductDescription: null,
    exchangeProductImageUrl: null,
    exchangeProductStock: 10,
    requiredCountForExchange: 3,
    description: null,
    imageUrl: null,
    status: 'ON_SALE' as const,
    createdAt: `2026-07-${20 + id}T00:00:00Z`,
    updatedAt: `2026-07-${20 + id}T00:00:00Z`,
    ownedCount,
  };
}

describe('Cards', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('전체 쿠폰과 내 쿠폰을 분리하지 않고 한 목록으로 보여준다', async () => {
    mockedGetCards.mockResolvedValue([
      card(1, '보유하지 않은 카드', 0),
      card(2, '보유한 카드', 3),
    ]);

    render(<Cards />);

    expect(await screen.findByText('보유하지 않은 쿠폰')).toBeInTheDocument();
    expect(screen.getByText('보유한 쿠폰')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '전체 카드' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '내 카드' }),
    ).not.toBeInTheDocument();
  });

  it('수집중에는 1장 이상 보유했지만 교환 수량이 부족한 쿠폰만 보여준다', async () => {
    mockedGetCards.mockResolvedValue([
      card(1, '미보유 카드', 0),
      card(2, '수집 카드', 1),
      card(3, '교환 카드', 3),
    ]);

    render(<Cards />);

    await screen.findByText('미보유 쿠폰');
    fireEvent.click(screen.getByRole('button', { name: '수집중' }));

    expect(screen.queryByText('미보유 쿠폰')).not.toBeInTheDocument();
    expect(screen.getByText('수집 쿠폰')).toBeInTheDocument();
    expect(screen.queryByText('교환 쿠폰')).not.toBeInTheDocument();
  });

  it('교환가능에는 필요 수량 이상 보유한 쿠폰만 보여준다', async () => {
    mockedGetCards.mockResolvedValue([
      card(1, '미보유 카드', 0),
      card(2, '수집 카드', 2),
      card(3, '교환 카드', 3),
      card(4, '초과 카드', 5),
    ]);

    render(<Cards />);

    await screen.findByText('미보유 쿠폰');
    fireEvent.click(screen.getByRole('button', { name: '교환가능' }));

    expect(screen.queryByText('미보유 쿠폰')).not.toBeInTheDocument();
    expect(screen.queryByText('수집 쿠폰')).not.toBeInTheDocument();
    expect(screen.getByText('교환 쿠폰')).toBeInTheDocument();
    expect(screen.getByText('초과 쿠폰')).toBeInTheDocument();
  });
});
