'use client';
import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import AuthPanel from '@/components/AuthPanel';

function AuthContent() {
  const params = useSearchParams();
  const view = params.get('view') === 'signup' ? 'signup' : 'login';
  return <AuthPanel initialView={view} />;
}

export default function Auth() {
  return (
    <Suspense fallback={null}>
      <AuthContent />
    </Suspense>
  );
}
