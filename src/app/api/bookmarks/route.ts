// ============================================================================
// GET /api/bookmarks   — 저장 목록 (OS.md 12.5)
// POST /api/bookmarks  — 북마크 생성 (idempotent)
// ----------------------------------------------------------------------------
// M1 단일 로컬 사용자. Bookmark 는 공고당 1건(@@unique([jobId])).
//
// GET: query status? 로 필터. includeExpired 무시 → 마감 공고도 표시(12.6).
//   응답 items 는 JobDTO[] (bookmark 필드가 채워짐).
// POST: { jobId } → { bookmarkId, status:"PLANNED" }. 이미 있으면 기존 것 반환(idempotent).
// ============================================================================

import { NextRequest, NextResponse } from "next/server";
import type { Prisma } from "@prisma/client";
import { prisma } from "@/lib/db";
import { toJobDTO, JOB_INCLUDE } from "@/lib/serialize";
import type {
  ApiError,
  BookmarksListResponse,
  BookmarkStatus,
  CreateBookmarkResponse,
} from "@/types/contract";

const VALID_STATUS: BookmarkStatus[] = ["PLANNED", "APPLIED", "CLOSED"];

export async function GET(req: NextRequest) {
  const status = req.nextUrl.searchParams.get("status");

  const where: Prisma.BookmarkWhereInput = {};
  if (status) {
    if (!VALID_STATUS.includes(status as BookmarkStatus)) {
      const err: ApiError = {
        error: { code: "INVALID_STATUS", message: `허용되지 않은 status: ${status}` },
      };
      return NextResponse.json(err, { status: 400 });
    }
    where.status = status;
  }

  const bookmarks = await prisma.bookmark.findMany({
    where,
    orderBy: { createdAt: "desc" },
    include: { job: { include: JOB_INCLUDE } },
  });

  // includeExpired 무시: 마감 공고도 그대로 표시. bookmark 필드는 job.bookmarks join 으로 채워짐.
  const body: BookmarksListResponse = {
    items: bookmarks.map((bm) => toJobDTO(bm.job)),
  };
  return NextResponse.json(body);
}

export async function POST(req: NextRequest) {
  let jobId: unknown;
  try {
    ({ jobId } = (await req.json()) as { jobId?: unknown });
  } catch {
    jobId = undefined;
  }

  if (typeof jobId !== "string" || !jobId) {
    const err: ApiError = {
      error: { code: "INVALID_BODY", message: "jobId 가 필요합니다." },
    };
    return NextResponse.json(err, { status: 400 });
  }

  const job = await prisma.job.findUnique({ where: { id: jobId } });
  if (!job) {
    const err: ApiError = {
      error: { code: "JOB_NOT_FOUND", message: `공고를 찾을 수 없습니다: ${jobId}` },
    };
    return NextResponse.json(err, { status: 404 });
  }

  // idempotent: 이미 있으면 기존 것 반환(공고당 1건). status 기본 PLANNED(스키마 default).
  const bookmark = await prisma.bookmark.upsert({
    where: { jobId },
    update: {},
    create: { jobId },
  });

  const res: CreateBookmarkResponse = {
    bookmarkId: bookmark.id,
    status: bookmark.status as BookmarkStatus,
  };
  return NextResponse.json(res, { status: 201 });
}
