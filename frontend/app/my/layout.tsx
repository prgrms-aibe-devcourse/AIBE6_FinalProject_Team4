import { ReactNode } from 'react';
import RequireAuth from '@/components/RequireAuth';

export default function MyLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>;
}
