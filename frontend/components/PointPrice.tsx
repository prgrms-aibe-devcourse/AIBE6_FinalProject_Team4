import { fmt } from '@/lib/store';

const SIZES: Record<string, { dot: string; text: string }> = {
  sm: { dot: 'w-[15px] h-[15px] text-[9px]', text: 'text-sm' },
  md: { dot: 'w-[18px] h-[18px] text-[10px]', text: 'text-base' },
  lg: { dot: 'w-6 h-6 text-xs', text: 'text-xl' },
};

interface PointPriceProps {
  value: number | string;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

// Yellow circular "P" badge followed by the amount — the standard point price
// display across shop, cards and detail pages.
export default function PointPrice({ value, size = 'md', className = '' }: PointPriceProps) {
  const s = SIZES[size] || SIZES.md;
  return (
    <span className={`inline-flex items-center gap-[5px] ${className}`}>
      <span className={`grid place-items-center rounded-full bg-gold font-extrabold text-gold-text ${s.dot}`}>P</span>
      <b className={`font-extrabold text-ink ${s.text}`}>{fmt(value)}P</b>
    </span>
  );
}
