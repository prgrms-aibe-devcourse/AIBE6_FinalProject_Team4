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

// Emoji options offered as a photo substitute when registering a plant.
// thumbnailUrl has no dedicated column for "emoji instead of a photo", so a
// chosen emoji is encoded as `${EMOJI_THUMBNAIL_PREFIX}${emoji}` and stored in
// the same thumbnailUrl string field a real upload would use.
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
