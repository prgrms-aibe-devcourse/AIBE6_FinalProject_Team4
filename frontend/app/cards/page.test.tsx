import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Cards from './page';
import { getCards } from '@/lib/card-api';

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

  it('전체 카드와 내 카드를 분리하지 않고 한 목록으로 보여준다', async () => {
    mockedGetCards.mockResolvedValue([
      card(1, '보유하지 않은 카드', 0),
      card(2, '보유한 카드', 3),
    ]);

    render(<Cards />);

    expect(await screen.findByText('보유하지 않은 카드')).toBeInTheDocument();
    expect(screen.getByText('보유한 카드')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '전체 카드' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '내 카드' }),
    ).not.toBeInTheDocument();
  });
});
