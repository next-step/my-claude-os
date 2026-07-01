// ============================================================================
// PATCH /api/bookmarks/:id  — 상태 변경 { status } (OS.md 12.5)
// DELETE /api/bookmarks/:id — 북마크 삭제 → 204
// ----------------------------------------------------------------------------
// status: "PLANNED" | "APPLIED" | "CLOSED" (지원예정/지원함/마감)
// ============================================================================

import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/db";
import type {
  ApiError,
  BookmarkStatus,
  UpdateBookmarkResponse,
} from "@/types/contract";

const VALID_STATUS: BookmarkStatus[] = ["PLANNED", "APPLIED", "CLOSED"];

/** Prisma "레코드 없음"(P2025) 판별 */
function isNotFound(e: unknown): boolean {
  return (
    typeof e === "object" &&
    e !== null &&
    "code" in e &&
    (e as { code?: string }).code === "P2025"
  );
}

export async function PATCH(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;

  let status: unknown;
  try {
    ({ status } = (await req.json()) as { status?: unknown });
  } catch {
    status = undefined;
  }

  if (typeof status !== "string" || !VALID_STATUS.includes(status as BookmarkStatus)) {
    const err: ApiError = {
      error: {
        code: "INVALID_STATUS",
        message: `status 는 ${VALID_STATUS.join(", ")} 중 하나여야 합니다.`,
      },
    };
    return NextResponse.json(err, { status: 400 });
  }

  try {
    const bookmark = await prisma.bookmark.update({
      where: { id },
      data: { status },
    });
    const res: UpdateBookmarkResponse = {
      bookmarkId: bookmark.id,
      status: bookmark.status as BookmarkStatus,
    };
    return NextResponse.json(res);
  } catch (e) {
    if (isNotFound(e)) {
      const err: ApiError = {
        error: { code: "BOOKMARK_NOT_FOUND", message: `북마크를 찾을 수 없습니다: ${id}` },
      };
      return NextResponse.json(err, { status: 404 });
    }
    throw e;
  }
}

export async function DELETE(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  try {
    await prisma.bookmark.delete({ where: { id } });
    return new NextResponse(null, { status: 204 });
  } catch (e) {
    if (isNotFound(e)) {
      const err: ApiError = {
        error: { code: "BOOKMARK_NOT_FOUND", message: `북마크를 찾을 수 없습니다: ${id}` },
      };
      return NextResponse.json(err, { status: 404 });
    }
    throw e;
  }
}
