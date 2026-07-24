'use client';
import { ReactNode } from 'react';
import { usePathname } from 'next/navigation';

// Remounts on every route change, so the animation replays per navigation.
export default function Template({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return (
    <div key={pathname} className="animate-upIn">
      {children}
    </div>
  );
}
