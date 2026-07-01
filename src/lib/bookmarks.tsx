"use client";

// ============================================================================
// 북마크 스토어 — 실 DB API 연결 (낙관적 업데이트 + 실패 시 롤백)
// ----------------------------------------------------------------------------
// 진실의 출처는 서버 DB. 마운트 시 GET /api/bookmarks 로 전체 북마크를 불러와
// {jobId -> {bookmarkId, status}} 맵을 만든다. 이후 화면(피드/상세/저장)은
// 이 맵으로 별표·상태를 그린다.
//
// 모든 쓰기는 낙관적으로 화면을 먼저 바꾸고 서버에 반영한다:
//   - 성공: 서버가 준 실제 값(bookmarkId 등)으로 확정
//   - 실패: 직전 상태로 롤백 + 토스트로 알림
// 서버 호출은 src/lib/api.ts 의 createBookmark/updateBookmark/deleteBookmark/
// fetchBookmarks 로 일원화(데이터 접근 단일 창구).
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
import type { BookmarkStatus } from "@/types/contract";
import {
  createBookmark,
  deleteBookmark,
  fetchBookmarks,
  updateBookmark,
} from "@/lib/api";

export interface BookmarkEntry {
  bookmarkId: string;
  status: BookmarkStatus;
}

/** key = jobId */
type BookmarkMap = Record<string, BookmarkEntry>;

interface BookmarkContextValue {
  /** 초기 북마크 로드 완료 여부(로드 전 별표 깜빡임 방지) */
  ready: boolean;
  map: BookmarkMap;
  count: number;
  isBookmarked: (jobId: string) => boolean;
  entry: (jobId: string) => BookmarkEntry | null;
  /** 처리 중(중복 클릭 방지 → 버튼 disable) */
  isPending: (jobId: string) => boolean;
  /** 토글: 없으면 생성(POST), 있으면 삭제(DELETE) */
  toggle: (jobId: string) => void;
  setStatus: (jobId: string, status: BookmarkStatus) => void;
  remove: (jobId: string) => void;
}

const BookmarkContext = createContext<BookmarkContextValue | null>(null);

export function BookmarkProvider({ children }: { children: ReactNode }) {
  const [map, setMap] = useState<BookmarkMap>({});
  const [ready, setReady] = useState(false);
  const [pending, setPending] = useState<Record<string, boolean>>({});
  const [toast, setToast] = useState<string | null>(null);

  // 동기적으로 현재 맵을 읽기 위한 ref(낙관적 롤백에 필요)
  const mapRef = useRef<BookmarkMap>({});
  mapRef.current = map;

  // 초기 로드: 서버의 전체 북마크 → 맵
  useEffect(() => {
    let alive = true;
    fetchBookmarks()
      .then((res) => {
        if (!alive) return;
        const next: BookmarkMap = {};
        for (const job of res.items) {
          if (job.bookmark) {
            next[job.id] = {
              bookmarkId: job.bookmark.bookmarkId,
              status: job.bookmark.status,
            };
          }
        }
        setMap(next);
      })
      .catch(() => {
        // 초기 로드 실패해도 앱은 동작(별표만 비어 보임). 조용히 넘어간다.
      })
      .finally(() => alive && setReady(true));
    return () => {
      alive = false;
    };
  }, []);

  const showError = useCallback((msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(null), 3200);
  }, []);

  const setPendingFor = useCallback((jobId: string, v: boolean) => {
    setPending((prev) => {
      if (v) return { ...prev, [jobId]: true };
      const next = { ...prev };
      delete next[jobId];
      return next;
    });
  }, []);

  /** 북마크 생성(낙관적) */
  const add = useCallback(
    async (jobId: string) => {
      setPendingFor(jobId, true);
      // 낙관적: 임시 엔트리 표시
      setMap((prev) => ({
        ...prev,
        [jobId]: { bookmarkId: `temp_${jobId}`, status: "PLANNED" },
      }));
      try {
        const res = await createBookmark(jobId);
        setMap((prev) => ({
          ...prev,
          [jobId]: { bookmarkId: res.bookmarkId, status: res.status },
        }));
      } catch {
        // 롤백: 임시 엔트리 제거
        setMap((prev) => {
          const next = { ...prev };
          delete next[jobId];
          return next;
        });
        showError("북마크 저장에 실패했어요. 잠시 후 다시 시도해 주세요.");
      } finally {
        setPendingFor(jobId, false);
      }
    },
    [setPendingFor, showError]
  );

  /** 북마크 삭제(낙관적) */
  const remove = useCallback(
    async (jobId: string) => {
      const existing = mapRef.current[jobId];
      if (!existing) return;
      setPendingFor(jobId, true);
      // 낙관적: 즉시 제거
      setMap((prev) => {
        const next = { ...prev };
        delete next[jobId];
        return next;
      });
      try {
        await deleteBookmark(existing.bookmarkId);
      } catch {
        // 롤백: 되살림
        setMap((prev) => ({ ...prev, [jobId]: existing }));
        showError("저장 취소에 실패했어요. 잠시 후 다시 시도해 주세요.");
      } finally {
        setPendingFor(jobId, false);
      }
    },
    [setPendingFor, showError]
  );

  const toggle = useCallback(
    (jobId: string) => {
      if (mapRef.current[jobId]) void remove(jobId);
      else void add(jobId);
    },
    [add, remove]
  );

  const setStatus = useCallback(
    async (jobId: string, status: BookmarkStatus) => {
      const existing = mapRef.current[jobId];
      if (!existing || existing.status === status) return;
      const prevStatus = existing.status;
      setPendingFor(jobId, true);
      // 낙관적: 상태 먼저 반영
      setMap((prev) =>
        prev[jobId] ? { ...prev, [jobId]: { ...prev[jobId], status } } : prev
      );
      try {
        await updateBookmark(existing.bookmarkId, status);
      } catch {
        // 롤백: 이전 상태로
        setMap((prev) =>
          prev[jobId]
            ? { ...prev, [jobId]: { ...prev[jobId], status: prevStatus } }
            : prev
        );
        showError("상태 변경에 실패했어요. 잠시 후 다시 시도해 주세요.");
      } finally {
        setPendingFor(jobId, false);
      }
    },
    [setPendingFor, showError]
  );

  const value: BookmarkContextValue = {
    ready,
    map,
    count: Object.keys(map).length,
    isBookmarked: (jobId) => Boolean(map[jobId]),
    entry: (jobId) => map[jobId] ?? null,
    isPending: (jobId) => Boolean(pending[jobId]),
    toggle,
    setStatus,
    remove: (jobId) => void remove(jobId),
  };

  return (
    <BookmarkContext.Provider value={value}>
      {children}
      {toast && (
        <div className="toast" role="alert">
          {toast}
        </div>
      )}
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
