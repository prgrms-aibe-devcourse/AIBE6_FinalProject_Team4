// Reusable shimmer placeholder for content that isn't ready yet (e.g. pre-hydration state).
export default function Skeleton({ className = '' }: { className?: string }) {
  return (
    <div
      className={`animate-shimmer rounded-md bg-[length:200%_100%] bg-[linear-gradient(90deg,#eceee5_25%,#f5f6f0_37%,#eceee5_63%)] ${className}`}
    />
  );
}
