"use client";

// ============================================================================
// 저장(Saved)
// ----------------------------------------------------------------------------
// 북마크한 공고 목록 + 상태(지원예정/지원함/마감) 관리.
// GET /api/bookmarks 미구현 → 전체 공고(마감 포함)를 가져와 클라이언트 북마크
// 스토어와 교집합. 실 엔드포인트가 나오면 api.ts 한 곳만 교체하면 된다.
// ============================================================================

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import type { BookmarkStatus, JobDTO } from "@/types/contract";
import { fetchAllJobsIncludingExpired } from "@/lib/api";
import { useBookmarks } from "@/lib/bookmarks";
import {
  deadlineInfo,
  experienceLabel,
  formatDate,
  roleLabel,
} from "@/lib/format";
import StatusControl from "@/components/StatusControl";
import { CardSkeletonList, EmptyState, ErrorState } from "@/components/states";

type Tab = "ALL" | BookmarkStatus;

const TABS: { key: Tab; label: string }[] = [
  { key: "ALL", label: "전체" },
  { key: "PLANNED", label: "지원 예정" },
  { key: "APPLIED", label: "지원함" },
  { key: "CLOSED", label: "마감" },
];

export default function SavedPage() {
  const { ready, map, entry, remove, seedFrom, isBookmarked } = useBookmarks();

  const [allJobs, setAllJobs] = useState<JobDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("ALL");

  const load = () => {
    const ctrl = new AbortController();
    setLoading(true);
    setError(null);
    fetchAllJobsIncludingExpired(ctrl.signal)
      .then((jobs) => {
        setAllJobs(jobs);
        seedFrom(jobs);
      })
      .catch((e) => {
        if (e?.name === "AbortError") return;
        setError("저장한 공고를 불러오지 못했어요.");
      })
      .finally(() => setLoading(false));
    return () => ctrl.abort();
  };

  useEffect(load, []); // eslint-disable-line react-hooks/exhaustive-deps

  // 북마크된 공고만(스토어 기준). 마감 공고도 표시.
  const saved = useMemo(
    () => allJobs.filter((j) => isBookmarked(j.id)),
    [allJobs, map] // eslint-disable-line react-hooks/exhaustive-deps
  );

  const counts = useMemo(() => {
    const c: Record<Tab, number> = {
      ALL: saved.length,
      PLANNED: 0,
      APPLIED: 0,
      CLOSED: 0,
    };
    for (const j of saved) {
      const st = entry(j.id)?.status;
      if (st) c[st] += 1;
    }
    return c;
  }, [saved, map]); // eslint-disable-line react-hooks/exhaustive-deps

  const visible = useMemo(
    () =>
      tab === "ALL"
        ? saved
        : saved.filter((j) => entry(j.id)?.status === tab),
    [saved, tab, map] // eslint-disable-line react-hooks/exhaustive-deps
  );

  return (
    <div className="page">
      <div className="pageHead">
        <div>
          <h1 className="pageHead__title">저장한 공고</h1>
          <p className="pageHead__sub">
            {ready && !loading
              ? `총 ${saved.length}건 · 지원 상태를 관리하세요`
              : "불러오는 중…"}
          </p>
        </div>
      </div>

      {/* 상태 탭 */}
      {saved.length > 0 && (
        <div className="tabs" role="tablist" aria-label="지원 상태 필터">
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={tab === t.key}
              className={`tab${tab === t.key ? " tab--on" : ""}`}
              onClick={() => setTab(t.key)}
            >
              {t.label}
              <span className="tab__count">{counts[t.key]}</span>
            </button>
          ))}
        </div>
      )}

      {loading ? (
        <CardSkeletonList count={3} />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : saved.length === 0 ? (
        <EmptyState
          title="아직 저장한 공고가 없어요"
          description="피드에서 별(☆)을 눌러 관심 공고를 저장하면 여기 모여요."
          action={
            <Link href="/jobs" className="btn btn--primary">
              공고 보러 가기
            </Link>
          }
        />
      ) : visible.length === 0 ? (
        <EmptyState
          title="이 상태의 공고가 없어요"
          description="다른 상태 탭을 확인해 보세요."
        />
      ) : (
        <ul className="cardList">
          {visible.map((job) => {
            const e = entry(job.id);
            const dl = deadlineInfo(job.deadline);
            const isPartial = job.dataQuality === "PARTIAL";
            return (
              <li key={job.id}>
                <article className="savedItem">
                  <div className="savedItem__main">
                    <div className="savedItem__company">{job.companyName}</div>
                    <h3 className="savedItem__title">
                      <Link href={`/jobs/${job.id}`}>{job.title}</Link>
                    </h3>
                    <div className="card__tags">
                      {roleLabel(job.jobRole) && (
                        <span className="badge badge--role">
                          {roleLabel(job.jobRole)}
                        </span>
                      )}
                      <span className="badge">
                        {experienceLabel(job.experienceLevel)}
                      </span>
                      {job.location && (
                        <span className="badge">{job.location}</span>
                      )}
                      <span className={`badge badge--deadline badge--${dl.tone}`}>
                        {dl.tone === "always" ? "상시" : `마감 ${dl.label}`}
                      </span>
                      {job.deadline && (
                        <span className="card__metaText card__metaText--dim">
                          ~ {formatDate(job.deadline)}
                        </span>
                      )}
                    </div>
                    {isPartial && (
                      <a
                        href={job.url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="savedItem__ext"
                      >
                        원문에서 조건 확인 ↗
                      </a>
                    )}
                  </div>

                  <div className="savedItem__side">
                    {e && <StatusControl jobId={job.id} status={e.status} />}
                    <button
                      type="button"
                      className="btn btn--ghost btn--sm savedItem__remove"
                      onClick={() => remove(job.id)}
                    >
                      저장 취소
                    </button>
                  </div>
                </article>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
