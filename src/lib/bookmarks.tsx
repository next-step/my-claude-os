"use client";

// ============================================================================
// 북마크 클라이언트 스토어 (localStorage 기반) — 실 API 교체 지점(SEAM)
// ----------------------------------------------------------------------------
// 북마크 쓰기 API(POST/PATCH/DELETE)가 아직 없어서, M1 은 이 컨텍스트가 북마크
// 상태의 단일 진실 출처다. 낙관적 업데이트를 위해 localStorage 에 즉시 반영한다.
//
// [최초 시딩] 첫 방문이면 서버 응답의 JobDTO.bookmark 필드로 스토어를 시드한다
//   (seedFrom). 한 번 시드하면 그 뒤로는 로컬 스토어가 진실 → 사용자가 삭제한
//   북마크가 mock 필드 때문에 되살아나지 않는다.
//
// [실 API 전환] toggle/setStatus/remove 의 본문을 src/lib/api.ts 의
//   createBookmark/updateBookmark/deleteBookmark 호출로 바꾸면 된다.
//   화면(useBookmarks 사용부)은 수정 불필요.
// ============================================================================

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import type { BookmarkStatus, JobDTO } from "@/types/contract";

export interface BookmarkEntry {
  bookmarkId: string;
  status: BookmarkStatus;
}

/** key = jobId */
type BookmarkMap = Record<string, BookmarkEntry>;

const STORAGE_KEY = "chaeyong.bookmarks.v1";
const SEED_KEY = "chaeyong.bookmarks.seeded.v1";

interface BookmarkContextValue {
  /** localStorage 로드 완료 여부(SSR 하이드레이션 깜빡임 방지용) */
  ready: boolean;
  map: BookmarkMap;
  count: number;
  isBookmarked: (jobId: string) => boolean;
  entry: (jobId: string) => BookmarkEntry | null;
  /** 북마크 토글: 없으면 PLANNED 로 추가, 있으면 제거 */
  toggle: (jobId: string) => void;
  setStatus: (jobId: string, status: BookmarkStatus) => void;
  remove: (jobId: string) => void;
  /** 최초 1회 서버 DTO 로 시드(이미 시드됐으면 무시) */
  seedFrom: (jobs: JobDTO[]) => void;
}

const BookmarkContext = createContext<BookmarkContextValue | null>(null);

export function BookmarkProvider({ children }: { children: ReactNode }) {
  const [map, setMap] = useState<BookmarkMap>({});
  const [ready, setReady] = useState(false);
  const seededRef = useRef(false);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) setMap(JSON.parse(raw) as BookmarkMap);
      seededRef.current = localStorage.getItem(SEED_KEY) === "true";
    } catch {
      /* localStorage 접근 불가 시 메모리 상태로만 동작 */
    }
    setReady(true);
  }, []);

  /** 상태 갱신 + localStorage 반영을 한 번에 */
  const write = useCallback(
    (updater: (prev: BookmarkMap) => BookmarkMap) => {
      setMap((prev) => {
        const next = updater(prev);
        try {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
        } catch {
          /* noop */
        }
        return next;
      });
    },
    []
  );

  const seedFrom = useCallback(
    (jobs: JobDTO[]) => {
      if (seededRef.current) return;
      seededRef.current = true;
      try {
        localStorage.setItem(SEED_KEY, "true");
      } catch {
        /* noop */
      }
      write((prev) => {
        const next = { ...prev };
        for (const j of jobs) {
          if (j.bookmark && !next[j.id]) {
            next[j.id] = { ...j.bookmark };
          }
        }
        return next;
      });
    },
    [write]
  );

  const toggle = useCallback(
    (jobId: string) =>
      write((prev) => {
        const next = { ...prev };
        if (next[jobId]) delete next[jobId];
        else next[jobId] = { bookmarkId: `local_${jobId}`, status: "PLANNED" };
        return next;
      }),
    [write]
  );

  const setStatus = useCallback(
    (jobId: string, status: BookmarkStatus) =>
      write((prev) => {
        if (!prev[jobId]) return prev;
        return { ...prev, [jobId]: { ...prev[jobId], status } };
      }),
    [write]
  );

  const remove = useCallback(
    (jobId: string) =>
      write((prev) => {
        if (!prev[jobId]) return prev;
        const next = { ...prev };
        delete next[jobId];
        return next;
      }),
    [write]
  );

  const value: BookmarkContextValue = {
    ready,
    map,
    count: Object.keys(map).length,
    isBookmarked: (jobId) => Boolean(map[jobId]),
    entry: (jobId) => map[jobId] ?? null,
    toggle,
    setStatus,
    remove,
    seedFrom,
  };

  return (
    <BookmarkContext.Provider value={value}>
      {children}
    </BookmarkContext.Provider>
  );
}

export function useBookmarks(): BookmarkContextValue {
  const ctx = useContext(BookmarkContext);
  if (!ctx) {
    throw new Error("useBookmarks 는 <BookmarkProvider> 안에서만 사용할 수 있어요.");
  }
  return ctx;
}
