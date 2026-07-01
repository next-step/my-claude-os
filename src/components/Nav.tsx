"use client";

// 상단 글로벌 네비게이션. 피드/저장/내 조건 간 이동 + 저장 개수 뱃지.
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useBookmarks } from "@/lib/bookmarks";

const ITEMS = [
  { href: "/jobs", label: "공고 피드" },
  { href: "/saved", label: "저장" },
  { href: "/onboarding", label: "내 조건" },
];

export default function Nav() {
  const pathname = usePathname();
  const { count, ready } = useBookmarks();

  return (
    <header className="nav">
      <div className="nav__inner">
        <Link href="/jobs" className="nav__brand">
          채용 리서치 <span className="nav__brandTag">M1</span>
        </Link>
        <nav className="nav__links" aria-label="주요 화면">
          {ITEMS.map((it) => {
            const active =
              pathname === it.href ||
              (it.href === "/jobs" && pathname.startsWith("/jobs"));
            return (
              <Link
                key={it.href}
                href={it.href}
                className={`nav__link${active ? " nav__link--active" : ""}`}
                aria-current={active ? "page" : undefined}
              >
                {it.label}
                {it.href === "/saved" && ready && count > 0 && (
                  <span className="nav__count">{count}</span>
                )}
              </Link>
            );
          })}
        </nav>
      </div>
    </header>
  );
}
