'use client';
import { ReactNode, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import RequireAuth from '@/components/RequireAuth';

function JournalsLayoutInner({ children }: { children: ReactNode }) {
  const searchParams = useSearchParams();
  // 게시판 글에 연동된 일지를 보러 온 경우는 작성자가 아니어도(비로그인 포함) 볼 수 있어야
  // 하므로, 이 딥링크로 들어왔을 때만 로그인 게이트를 건너뛴다. 그 외 /journals 경로는 그대로
  // 로그인 필수를 유지한다.
  if (searchParams.get('viaBoardPost')) {
    return <>{children}</>;
  }
  return <RequireAuth>{children}</RequireAuth>;
}

export default function JournalsLayout({ children }: { children: ReactNode }) {
  return (
    <Suspense fallback={null}>
      <JournalsLayoutInner>{children}</JournalsLayoutInner>
    </Suspense>
  );
}
