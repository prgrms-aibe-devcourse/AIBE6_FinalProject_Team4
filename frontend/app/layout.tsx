import './globals.css';
import { ReactNode } from 'react';
import { StoreProvider } from '@/lib/store';
import { UIProvider } from '@/lib/ui';
import Navbar from '@/components/Navbar';
import Footer from '@/components/Footer';

export const metadata = {
  title: '키워볼래 🌱',
  description: '식물을 키우고, 기록하고, 진짜 열매를 받아보세요',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="ko">
      <head>
        <link href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.css" rel="stylesheet" />
        <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0" />
      </head>
      {/* pb-[66px] leaves room for the mobile bottom tab bar */}
      <body className="flex min-h-screen flex-col pb-[66px] md:pb-0">
        <StoreProvider>
          <UIProvider>
            <Navbar />
            {/* min-height keeps the footer's top half in view even on short pages, instead of it sitting flush under a tiny amount of content */}
            <main className="min-h-[calc(100vh-62px-120px)] flex-1">{children}</main>
            <Footer />
          </UIProvider>
        </StoreProvider>
      </body>
    </html>
  );
}
