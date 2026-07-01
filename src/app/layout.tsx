import type { Metadata } from "next";
import "./globals.css";
import { BookmarkProvider } from "@/lib/bookmarks";
import Nav from "@/components/Nav";

export const metadata: Metadata = {
  title: "채용 리서치 (M1)",
  description: "취업 준비생용 채용 공고 모아보기 · 회사 리서치",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body>
        {/* 북마크 상태(실 DB API + 낙관적 업데이트)를 전 화면에서 공유 → 최상단 provider */}
        <BookmarkProvider>
          <Nav />
          <main className="container">{children}</main>
        </BookmarkProvider>
      </body>
    </html>
  );
}
