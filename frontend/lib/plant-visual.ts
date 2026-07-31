import { grads } from '@/lib/theme';

export { formatDate } from '@/lib/format';

// Backend doesn't return emoji/gradient for a species — map by name for card visuals,
// with a fallback for any species not in this table (e.g. newly added ones).
export const SPECIES_VISUAL: Record<string, { emoji: string; grad: string }> = {
  방울토마토: { emoji: '🍅', grad: grads.tomato },
  바질: { emoji: '🌿', grad: grads.basil },
  상추: { emoji: '🥬', grad: grads.lettuce },
  딸기: { emoji: '🍓', grad: grads.strawberry },
  고추: { emoji: '🌶️', grad: grads.pepper },
  수박: { emoji: '🍉', grad: grads.mint },
  당근: { emoji: '🥕', grad: grads.carrot },
  청경채: { emoji: '🥬', grad: grads.sprout },
};
export const DEFAULT_VISUAL = { emoji: '🌱', grad: grads.sprout };

export function plantVisual(speciesName: string) {
  return SPECIES_VISUAL[speciesName] || DEFAULT_VISUAL;
}

// 식물 등록 시 대표 사진 대신 고를 수 있는 이모지 옵션.
// thumbnailUrl에는 "사진 대신 이모지"를 위한 전용 컬럼이 없어서, 선택한 이모지를
// `${EMOJI_THUMBNAIL_PREFIX}${emoji}` 형태로 인코딩해 실제 업로드 시 쓰는 thumbnailUrl
// 문자열 필드에 그대로 저장한다.
export const PROFILE_EMOJI_OPTIONS: [string, string][] = [
  ['🌱', grads.sprout], ['☀️', grads.sun], ['🪴', grads.mint], ['🌸', grads.strawberry],
  ['🍅', grads.tomato], ['🌿', grads.basil], ['🥬', grads.lettuce], ['🌶️', grads.pepper],
  ['🥕', grads.carrot], ['🥔', grads.potato],
];
export const EMOJI_THUMBNAIL_PREFIX = 'emoji:';

export type PlantThumbnail =
  | { type: 'image'; url: string }
  | { type: 'emoji'; emoji: string; grad: string };

export function plantThumbnail(thumbnailUrl: string | null | undefined, speciesName: string): PlantThumbnail {
  if (thumbnailUrl?.startsWith(EMOJI_THUMBNAIL_PREFIX)) {
    const emoji = thumbnailUrl.slice(EMOJI_THUMBNAIL_PREFIX.length);
    const grad = PROFILE_EMOJI_OPTIONS.find(([e]) => e === emoji)?.[1] ?? DEFAULT_VISUAL.grad;
    return { type: 'emoji', emoji, grad };
  }
  if (thumbnailUrl) {
    return { type: 'image', url: thumbnailUrl };
  }
  const visual = plantVisual(speciesName);
  return { type: 'emoji', emoji: visual.emoji, grad: visual.grad };
}

export function dPlus(startDate: string): number {
  const start = new Date(startDate);
  if (Number.isNaN(start.getTime())) return 0;
  const diff = Date.now() - start.getTime();
  return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
}
