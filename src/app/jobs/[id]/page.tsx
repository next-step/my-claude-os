"use client";

// ============================================================================
// 공고 상세
// ----------------------------------------------------------------------------
// - 직무 요건(description). null 이면 "사람인에서 상세 보기" URL 폴백을 1급 요소로.
// - "이 회사 리서치 보기" 진입점(M2 placeholder, 비활성)
// - 원문 출처 링크를 신뢰감 있게 노출
// - 북마크 저장 + 상태 변경
// - 로딩 / 404(공고 없음) / 에러 상태
// ============================================================================

import { use, useEffect, useState } from "react";
import Link from "next/link";
import type { JobDTO } from "@/types/contract";
import { fetchJob, ApiRequestError } from "@/lib/api";
import {
  deadlineInfo,
  experienceLabel,
  formatDate,
  roleLabel,
  statusLabel,
} from "@/lib/format";
import { useBookmarks } from "@/lib/bookmarks";
import BookmarkButton from "@/components/BookmarkButton";
import { ErrorState } from "@/components/states";
import StatusControl from "@/components/StatusControl";

export default function JobDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const { entry } = useBookmarks();

  const [job, setJob] = useState<JobDTO | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    const ctrl = new AbortController();
    setLoading(true);
    setError(null);
    setNotFound(false);
    fetchJob(id, ctrl.signal)
      .then((j) => {
        setJob(j);
      })
      .catch((e) => {
        if (e?.name === "AbortError") return;
        if (e instanceof ApiRequestError && e.status === 404) setNotFound(true);
        else
          setError(
            e instanceof ApiRequestError
              ? e.message
              : "공고를 불러오지 못했어요."
          );
      })
      .finally(() => setLoading(false));
    return () => ctrl.abort();
  };

  useEffect(load, [id]); // eslint-disable-line react-hooks/exhaustive-deps

  if (loading) {
    return (
      <div className="page">
        <div className="detailSkeleton">
          <div className="sk sk--line sk--w40" />
          <div className="sk sk--line sk--w80" />
          <div className="sk sk--block" />
        </div>
      </div>
    );
  }

  if (notFound) {
    return (
      <div className="page">
        <ErrorState
          title="공고를 찾을 수 없어요"
          message="이미 내려간 공고이거나 잘못된 주소일 수 있어요."
          action={
            <Link href="/jobs" className="btn btn--primary">
              공고 피드로 돌아가기
            </Link>
          }
        />
      </div>
    );
  }

  if (error || !job) {
    return (
      <div className="page">
        <ErrorState message={error ?? "알 수 없는 오류"} onRetry={load} />
      </div>
    );
  }

  const dl = deadlineInfo(job.deadline);
  const bookmark = entry(job.id);
  const isPartial = job.dataQuality === "PARTIAL";

  return (
    <div className="page detail">
      <Link href="/jobs" className="detail__back">
        ← 공고 피드
      </Link>

      <header className="detail__head">
        <div className="detail__company">{job.companyName}</div>
        <h1 className="detail__title">{job.title}</h1>

        <div className="detail__tags">
          {isPartial && <span className="badge badge--warn">조건 확인 필요</span>}
          {roleLabel(job.jobRole) && (
            <span className="badge badge--role">{roleLabel(job.jobRole)}</span>
          )}
          <span className="badge">{experienceLabel(job.experienceLevel)}</span>
          {job.location && <span className="badge">{job.location}</span>}
          {job.employmentType && (
            <span className="badge badge--muted">{job.employmentType}</span>
          )}
          <span className={`badge badge--deadline badge--${dl.tone}`}>
            {dl.tone === "always" ? "상시채용" : `마감 ${dl.label}`}
          </span>
        </div>

        <div className="detail__actions">
          <BookmarkButton jobId={job.id} size="lg" />
          {bookmark && (
            <div className="detail__statusInline">
              <span className="detail__statusLabel">
                상태: {statusLabel(bookmark.status)}
              </span>
              <StatusControl jobId={job.id} status={bookmark.status} />
            </div>
          )}
        </div>
      </header>

      {/* 회사 리서치 진입점 — M2 placeholder */}
      <section className="researchCta">
        <div>
          <strong>이 회사 리서치 보기</strong>
          <p className="researchCta__desc">
            공시 요약 · 인재상 · 자소서 관점 정리 (M2에서 제공 예정)
          </p>
        </div>
        <button type="button" className="btn btn--outline" disabled>
          곧 제공돼요
        </button>
      </section>

      {/* 직무 요건 — description null 이면 URL 폴백을 1급으로 */}
      <section className="detail__section">
        <h2 className="detail__sectionTitle">직무 요건</h2>
        {job.description ? (
          <p className="detail__desc">{job.description}</p>
        ) : (
          <div className="fallback">
            <p className="fallback__msg">
              이 공고는 상세 본문을 자동으로 가져오지 못했어요. 원문에서 직무 요건을
              직접 확인할 수 있어요.
            </p>
            <a
              href={job.url}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn--primary"
            >
              사람인에서 상세 보기 ↗
            </a>
          </div>
        )}
      </section>

      {/* 원문 출처 — 신뢰감 있게 */}
      <section className="detail__section">
        <h2 className="detail__sectionTitle">원문 출처</h2>
        <ul className="detail__meta">
          <li>
            <span className="detail__metaKey">등록일</span>
            <span>{formatDate(job.postedAt)}</span>
          </li>
          <li>
            <span className="detail__metaKey">마감일</span>
            <span>{job.deadline ? formatDate(job.deadline) : "상시채용/미정"}</span>
          </li>
          <li>
            <span className="detail__metaKey">수집 소스</span>
            <span>{job.sources.join(", ") || job.source}</span>
          </li>
        </ul>
        <a
          href={job.url}
          target="_blank"
          rel="noopener noreferrer"
          className="detail__sourceLink"
        >
          {job.url} ↗
        </a>
      </section>
    </div>
  );
}
