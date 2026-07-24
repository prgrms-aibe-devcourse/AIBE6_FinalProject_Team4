import { ReactNode } from 'react';
import RequireAuth from '@/components/RequireAuth';

export default function JournalsLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
