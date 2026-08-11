import { beforeEach, describe, expect, it, vi } from 'vitest';
import { request } from '@/lib/api';
import { getPlantCareGuide } from '@/lib/care-guide-api';

vi.mock('@/lib/api', () => ({
  request: vi.fn(),
}));

const mockedRequest = vi.mocked(request);

describe('care guide api', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('종 id로 재배 가이드를 조회한다', async () => {
    mockedRequest.mockResolvedValueOnce({
      speciesName: '방울토마토',
      difficulty: '초급',
      difficultyReason: '베란다에서도 잘 자라요.',
      environment: { sunlight: '하루 6시간', watering: '겉흙이 마르면', temperature: '18~28도' },
      stages: [{ name: '파종', guide: '씨앗을 1cm 깊이로 심어요.' }],
      pitfalls: [{ problem: '잎이 노래져요', action: '물 주기를 줄여 주세요.' }],
      harvestTarget: '첫 열매까지 약 70일',
      cached: true,
    });

    const guide = await getPlantCareGuide(7, 'access-token');

    expect(mockedRequest).toHaveBeenCalledWith('/api/v1/ai/plants/species/7/care-guide', {
      accessToken: 'access-token',
      signal: undefined,
    });
    expect(guide.speciesName).toBe('방울토마토');
  });

  // 캐시 미스면 응답까지 수십 초가 걸릴 수 있어, 화면 이탈 시 요청을 끊을 수단이 필요하다.
  it('전달받은 AbortSignal을 그대로 넘긴다', async () => {
    const controller = new AbortController();
    mockedRequest.mockResolvedValueOnce({});

    await getPlantCareGuide(7, 'access-token', controller.signal);

    expect(mockedRequest).toHaveBeenCalledWith('/api/v1/ai/plants/species/7/care-guide', {
      accessToken: 'access-token',
      signal: controller.signal,
    });
  });
});
