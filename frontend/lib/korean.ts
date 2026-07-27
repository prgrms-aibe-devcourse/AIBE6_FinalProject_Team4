export function withTopicParticle(value: string): string {
  const normalized = value.trim();
  if (!normalized) return value;

  const lastCharacter = normalized.at(-1);
  if (!lastCharacter) return value;

  const codePoint = lastCharacter.charCodeAt(0);
  const isHangulSyllable = codePoint >= 0xac00 && codePoint <= 0xd7a3;
  const hasFinalConsonant = isHangulSyllable && (codePoint - 0xac00) % 28 !== 0;

  return `${value}${hasFinalConsonant ? '은' : '는'}`;
}
