// ============================================================================
// Prisma Job → JobDTO 직렬화 (OS.md 12.3/12.4 계약 유지)
// ----------------------------------------------------------------------------
// - 날짜(DateTime)를 ISO 8601 문자열로 변환(계약의 wire 형태).
// - sources[]=[source], duplicateCount=1 (M1: 소스 1개, 병합은 M3 — 자리만 확보).
// - bookmark 는 Job 컬럼이 아니라 Bookmark join 으로 계산(12.3).
//   M1 단일 로컬 사용자이므로 job.bookmarks 는 최대 1건(@@unique([jobId])).
// ============================================================================

import type { Prisma } from "@prisma/client";
import type {
  JobDTO,
  BookmarkStatus,
  ExperienceLevel,
  DataQuality,
} from "@/types/contract";

/** bookmarks 관계를 include 한 Job 행 타입 */
export type JobWithBookmarks = Prisma.JobGetPayload<{
  include: { bookmarks: true };
}>;

function iso(d: Date | null): string | null {
  return d ? d.toISOString() : null;
}

export function toJobDTO(job: JobWithBookmarks): JobDTO {
  const bm = job.bookmarks[0] ?? null;
  return {
    id: job.id,
    source: job.source,
    sourceJobId: job.sourceJobId,
    url: job.url,
    title: job.title,
    companyName: job.companyName,
    companyId: job.companyId,
    jobRole: job.jobRole,
    location: job.location,
    experienceLevel: job.experienceLevel as ExperienceLevel,
    employmentType: job.employmentType,
    deadline: iso(job.deadline),
    postedAt: iso(job.postedAt),
    description: job.description,
    dataQuality: job.dataQuality as DataQuality,
    dedupKey: job.dedupKey,
    collectedAt: job.collectedAt.toISOString(),
    // --- DTO 파생 필드 ---
    sources: [job.source],
    duplicateCount: 1,
    bookmark: bm
      ? { bookmarkId: bm.id, status: bm.status as BookmarkStatus }
      : null,
  };
}

/** GET include 옵션 재사용 상수 */
export const JOB_INCLUDE = { bookmarks: true } as const;
