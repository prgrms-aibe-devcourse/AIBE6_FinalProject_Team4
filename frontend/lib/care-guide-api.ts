import { request } from '@/lib/api';

export interface PlantCareGuideEnvironment {
  sunlight: string;
  watering: string;
  temperature: string;
}

export interface PlantCareGuideStage {
  name: string;
  guide: string;
}

export interface PlantCareGuidePitfall {
  problem: string;
  action: string;
}

export interface PlantCareGuideData {
  speciesName: string;
  // 서버 스키마는 초급/중급/고급만 허용하지만, 값이 늘어도 화면이 깨지지 않도록 string으로 받는다.
  difficulty: string;
  difficultyReason: string;
  environment: PlantCareGuideEnvironment;
  // 파종·새싹·성장·수확 네 단계가 순서대로 온다.
  stages: PlantCareGuideStage[];
  pitfalls: PlantCareGuidePitfall[];
  harvestTarget: string;
  // 저장본을 돌려줬는지 여부. AI 호출 비용을 통제하기 위한 내부 지표라 화면에는 노출하지 않는다
  // (ai 이슈 3 결정 — 사용자는 같은 가이드를 볼 뿐이라 캐시 여부를 알 이유가 없다).
  cached: boolean;
}

// GET이지만 저장본이 없으면 서버가 그 자리에서 AI로 생성한다 — 캐시 미스인 첫 요청은 수 초에서
// 수십 초까지 걸릴 수 있으므로 호출부는 반드시 로딩 상태를 보여줘야 한다.
export function getPlantCareGuide(
  speciesId: number,
  accessToken: string,
  signal?: AbortSignal,
): Promise<PlantCareGuideData> {
  return request<PlantCareGuideData>(`/api/v1/ai/plants/species/${speciesId}/care-guide`, {
    accessToken,
    signal,
  });
}
