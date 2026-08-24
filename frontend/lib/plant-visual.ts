import { grads } from '@/lib/theme';

export { formatDate } from '@/lib/format';

// Backend doesn't return emoji/gradient for a species — map by name for card visuals,
// with a fallback for any species not in this table (e.g. newly added ones).
// 종류별 아이콘/배경 구분 없이 항상 같은 새싹 아이콘 + 초록 배경 하나로 통일한다.
// icon: 'spa'는 material-symbols 이름이 아니라 "새싹 SVG를 쓰라"는 신호(sentinel)로만 쓰인다 — 렌더링 시 SEEDLING_ICON_SRC를 사용할 것.
export const SEEDLING_ICON_SRC = '/icons/seedling.svg';
export const SPECIES_VISUAL: Record<string, { icon: string; grad: string }> = {
  방울토마토: { icon: 'spa', grad: grads.sprout },
  바질: { icon: 'spa', grad: grads.sprout },
  상추: { icon: 'spa', grad: grads.sprout },
  딸기: { icon: 'spa', grad: grads.sprout },
  고추: { icon: 'spa', grad: grads.sprout },
  수박: { icon: 'spa', grad: grads.sprout },
  당근: { icon: 'spa', grad: grads.sprout },
  청경채: { icon: 'spa', grad: grads.sprout },
};
export const DEFAULT_VISUAL = { icon: 'spa', grad: grads.sprout };

export function plantVisual(speciesName: string) {
  return SPECIES_VISUAL[speciesName] || DEFAULT_VISUAL;
}

// 식물 등록 시 대표 사진 대신 고를 수 있는 이모지 옵션.
// thumbnailUrl에는 "사진 대신 이모지"를 위한 전용 컬럼이 없어서, 선택한 이모지를
// `${EMOJI_THUMBNAIL_PREFIX}${emoji}` 형태로 인코딩해 실제 업로드 시 쓰는 thumbnailUrl
// 문자열 필드에 그대로 저장한다.
export const PROFILE_EMOJI_OPTIONS: [string, string][] = [
  ['potted_plant', grads.sprout], ['light_mode', grads.sun], ['yard', grads.mint], ['local_florist', grads.strawberry],
  ['nutrition', grads.tomato], ['eco', grads.basil], ['spa', grads.lettuce], ['local_fire_department', grads.pepper],
  ['egg', grads.carrot], ['compost', grads.potato],
];
export const EMOJI_THUMBNAIL_PREFIX = 'emoji:';

export type PlantThumbnail =
  | { type: 'image'; url: string }
  | { type: 'emoji'; icon: string; grad: string };

export function plantThumbnail(thumbnailUrl: string | null | undefined, speciesName: string): PlantThumbnail {
  if (thumbnailUrl?.startsWith(EMOJI_THUMBNAIL_PREFIX)) {
    const icon = thumbnailUrl.slice(EMOJI_THUMBNAIL_PREFIX.length);
    const grad = PROFILE_EMOJI_OPTIONS.find(([e]) => e === icon)?.[1] ?? DEFAULT_VISUAL.grad;
    return { type: 'emoji', icon, grad };
  }
  if (thumbnailUrl) {
    return { type: 'image', url: thumbnailUrl };
  }
  const visual = plantVisual(speciesName);
  return { type: 'emoji', icon: visual.icon, grad: visual.grad };
}

export function dPlus(startDate: string): number {
  const start = new Date(startDate);
  if (Number.isNaN(start.getTime())) return 0;
  const diff = Date.now() - start.getTime();
  return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
}
