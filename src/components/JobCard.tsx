"use client";

// 피드/저장 공용 공고 카드.
// - FULL: 회사·직무·경력·지역·마감(D-day) 을 스캔하기 좋게 위계화.
// - PARTIAL: 자동수집이 제한된 공고 전용 카드. 원문 직접 확인 CTA 를 1급으로.
// 카드 전체 클릭 → 상세. (title 링크의 ::after 로 stretched-link 구현, CSS 참고)
import Link from "next/link";
import type { JobDTO } from "@/types/contract";
import {
  deadlineInfo,
  experienceLabel,
  formatDate,
  roleLabel,
  statusLabel,
} from "@/lib/format";
import { useBookmarks } from "@/lib/bookmarks";
import BookmarkButton from "./BookmarkButton";

export default function JobCard({ job }: { job: JobDTO }) {
  const { entry } = useBookmarks();
  const bookmark = entry(job.id);
  const dl = deadlineInfo(job.deadline);
  const isPartial = job.dataQuality === "PARTIAL";

  return (
    <article className={`card${isPartial ? " card--partial" : ""}`}>
      <div className="card__top">
        <div className="card__company">
          <span className="card__companyName">{job.companyName}</span>
          {isPartial && (
            <span className="badge badge--warn" title="자동 수집이 제한된 공고">
              조건 확인 필요
            </span>
          )}
          {bookmark && (
            <span
              className={`badge badge--status badge--${bookmark.status.toLowerCase()}`}
            >
              {statusLabel(bookmark.status)}
            </span>
          )}
        </div>
        <BookmarkButton jobId={job.id} />
      </div>

      <h3 className="card__title">
        <Link href={`/jobs/${job.id}`} className="card__titleLink">
          {job.title}
        </Link>
      </h3>

      {isPartial ? (
        <>
          <p className="card__partialNote">
            자동 수집으로 직무·지역 등 조건을 다 채우지 못했어요. 원문에서 직접
            확인하세요.
          </p>
          <div className="card__meta">
            <span className={`badge badge--deadline badge--${dl.tone}`}>
              {dl.label}
            </span>
            <span className="card__source">출처 {job.sources[0] ?? job.source}</span>
          </div>
          <div className="card__ctaRow">
            <a
              href={job.url}
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn--outline btn--sm card__extLink"
            >
              사람인에서 직접 확인 ↗
            </a>
          </div>
        </>
      ) : (
        <>
          <div className="card__tags">
            {roleLabel(job.jobRole) && (
              <span className="badge badge--role">{roleLabel(job.jobRole)}</span>
            )}
            <span className="badge">{experienceLabel(job.experienceLevel)}</span>
            {job.location && <span className="badge">{job.location}</span>}
            {job.employmentType && (
              <span className="badge badge--muted">{job.employmentType}</span>
            )}
          </div>
          <div className="card__meta">
            <span className={`badge badge--deadline badge--${dl.tone}`}>
              {dl.tone === "always" ? "상시" : `마감 ${dl.label}`}
            </span>
            {job.deadline && (
              <span className="card__metaText">~ {formatDate(job.deadline)}</span>
            )}
            <span className="card__metaText card__metaText--dim">
              등록 {formatDate(job.postedAt)}
            </span>
          </div>
        </>
      )}
    </article>
  );
}
