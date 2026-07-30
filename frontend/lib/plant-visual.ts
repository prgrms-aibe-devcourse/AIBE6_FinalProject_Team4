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

export function dPlus(startDate: string): number {
  const start = new Date(startDate);
  if (Number.isNaN(start.getTime())) return 0;
  const diff = Date.now() - start.getTime();
  return Math.max(0, Math.floor(diff / (1000 * 60 * 60 * 24)));
}
