// ============================================================================
// GET /api/jobs  — [MOCK-backed] 프론트 unblock 용 임시 핸들러
// ----------------------------------------------------------------------------
// OS.md 12.5/12.6 계약 형태를 그대로 반환한다. 데이터는 아직 DB 가 아니라
// src/lib/mock/jobs.ts 의 MOCK_JOBS 배열에 대한 인메모리 필터링이다.
// → 다음 M1 작업에서 Prisma DB 조회로 교체(응답 계약은 동일 → 프론트 무수정).
//
// 지원 쿼리(12.5): role, location(콤마 다중), experience(콤마 다중), keyword,
//   sort(deadline|recent), deadlineWithin(days), includeExpired(기본 false), cursor
// 응답(12.5): { items, nextCursor, totalCount, partialHiddenCount }
// ============================================================================

import { NextRequest, NextResponse } from "next/server";
import { MOCK_JOBS, MOCK_JOBS_EMPTY } from "@/lib/mock/jobs";
import type { JobDTO, JobsListResponse } from "@/types/contract";

const PAGE_SIZE = 20;
const TODAY = new Date("2026-07-01T00:00:00.000Z"); // M1 mock 기준 오늘

function isExpired(job: JobDTO): boolean {
  if (!job.deadline) return false; // 상시채용은 만료 아님
  return new Date(job.deadline) < TODAY;
}

function splitMulti(v: string | null): string[] {
  if (!v) return [];
  return v
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

export async function GET(req: NextRequest) {
  const sp = req.nextUrl.searchParams;

  // 빈/에러 형태도 mock 에서 표현 가능해야 함 → ?mock=empty
  if (sp.get("mock") === "empty") {
    const empty: JobsListResponse = {
      items: MOCK_JOBS_EMPTY,
      nextCursor: null,
      totalCount: 0,
      partialHiddenCount: 0,
    };
    return NextResponse.json(empty);
  }

  const role = sp.get("role");
  const locations = splitMulti(sp.get("location"));
  const experiences = splitMulti(sp.get("experience"));
  const keyword = sp.get("keyword")?.toLowerCase() ?? "";
  const sort = sp.get("sort") === "recent" ? "recent" : "deadline";
  const deadlineWithin = sp.get("deadlineWithin")
    ? Number(sp.get("deadlineWithin"))
    : null;
  const includeExpired = sp.get("includeExpired") === "true";
  const cursor = sp.get("cursor");

  let partialHiddenCount = 0;

  const filtered = MOCK_JOBS.filter((job) => {
    // 마감 지난 공고 기본 제외 (12.6)
    if (!includeExpired && isExpired(job)) return false;

    // PARTIAL 공고 보호(12.6): null 필드 때문에 필터로 가려지면 드롭하지 말고 카운트.
    const wouldHideByRole = role != null && job.jobRole == null;
    const wouldHideByLoc = locations.length > 0 && job.location == null;
    if (job.dataQuality === "PARTIAL" && (wouldHideByRole || wouldHideByLoc)) {
      partialHiddenCount += 1;
      return false;
    }

    if (role && job.jobRole !== role) return false;
    if (locations.length > 0 && (!job.location || !locations.includes(job.location)))
      return false;
    if (
      experiences.length > 0 &&
      !experiences.includes(job.experienceLevel)
    )
      return false;
    if (keyword) {
      const hay = `${job.title} ${job.companyName} ${job.description ?? ""}`.toLowerCase();
      if (!hay.includes(keyword)) return false;
    }
    if (deadlineWithin != null && job.deadline) {
      const days =
        (new Date(job.deadline).getTime() - TODAY.getTime()) / 86_400_000;
      if (days > deadlineWithin) return false;
    }
    return true;
  });

  // 정렬 (12.6)
  const sorted = [...filtered].sort((a, b) => {
    if (sort === "recent") {
      const pa = a.postedAt ? new Date(a.postedAt).getTime() : 0;
      const pb = b.postedAt ? new Date(b.postedAt).getTime() : 0;
      if (pb !== pa) return pb - pa;
      return a.id < b.id ? -1 : 1;
    }
    // sort === "deadline": 마감임박순, deadline=null(상시)은 항상 맨 뒤
    const da = a.deadline ? new Date(a.deadline).getTime() : Infinity;
    const db = b.deadline ? new Date(b.deadline).getTime() : Infinity;
    if (da !== db) return da - db;
    return a.id < b.id ? -1 : 1;
  });

  // 커서 페이지네이션 (mock: id 기반 단순 offset)
  let startIdx = 0;
  if (cursor) {
    const idx = sorted.findIndex((j) => j.id === cursor);
    startIdx = idx >= 0 ? idx + 1 : 0;
  }
  const page = sorted.slice(startIdx, startIdx + PAGE_SIZE);
  const nextCursor =
    startIdx + PAGE_SIZE < sorted.length ? page[page.length - 1].id : null;

  const body: JobsListResponse = {
    items: page,
    nextCursor,
    totalCount: sorted.length,
    partialHiddenCount,
  };
  return NextResponse.json(body);
}
