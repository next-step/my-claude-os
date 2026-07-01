// GET /api/jobs/:id — 실 DB(Prisma). 계약: JobDTO (OS.md 12.5).
// description=null 이면 프론트는 원문 URL 폴백을 1급 요소로 처리.
import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/db";
import { toJobDTO, JOB_INCLUDE } from "@/lib/serialize";
import type { ApiError } from "@/types/contract";

export async function GET(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  const job = await prisma.job.findUnique({
    where: { id },
    include: JOB_INCLUDE,
  });
  if (!job) {
    const err: ApiError = {
      error: { code: "JOB_NOT_FOUND", message: `공고를 찾을 수 없습니다: ${id}` },
    };
    return NextResponse.json(err, { status: 404 });
  }
  return NextResponse.json(toJobDTO(job));
}
