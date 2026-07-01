// ============================================================================
// DB seed — MOCK_JOBS(JobDTO[]) 를 DB(Job) 행으로 적재 + UserPreference 1행.
// ----------------------------------------------------------------------------
// 목적: Prisma 스키마가 실제로 동작하는지(마이그레이션 후 삽입/조회) 검증 + 이후
//   실 API 가 DB 를 읽을 때 쓸 초기 데이터. DTO 전용 필드(sources/duplicateCount/
//   bookmark)는 저장하지 않는다(응답 시 계산). 북마크가 있는 mock 은 Bookmark 로 분리 적재.
//
// 실행: npm run db:seed  (내부적으로 tsx prisma/seed.ts)
// ============================================================================

import { PrismaClient } from "@prisma/client";
import { MOCK_JOBS } from "../src/lib/mock/jobs";
import { MOCK_PREFERENCE } from "../src/lib/mock/preferences";

const prisma = new PrismaClient();

async function main() {
  // 1) 공고 적재 (upsert: (source, sourceJobId) idempotent)
  for (const j of MOCK_JOBS) {
    await prisma.job.upsert({
      where: { source_sourceJobId: { source: j.source, sourceJobId: j.sourceJobId } },
      update: {},
      create: {
        source: j.source,
        sourceJobId: j.sourceJobId,
        url: j.url,
        title: j.title,
        companyName: j.companyName,
        companyId: j.companyId,
        jobRole: j.jobRole,
        location: j.location,
        experienceLevel: j.experienceLevel,
        employmentType: j.employmentType,
        deadline: j.deadline ? new Date(j.deadline) : null,
        postedAt: j.postedAt ? new Date(j.postedAt) : null,
        description: j.description,
        dataQuality: j.dataQuality,
        dedupKey: j.dedupKey,
        collectedAt: new Date(j.collectedAt),
      },
    });
  }

  // 2) 북마크가 있는 mock → Bookmark 적재
  for (const j of MOCK_JOBS) {
    if (!j.bookmark) continue;
    const job = await prisma.job.findUnique({
      where: { source_sourceJobId: { source: j.source, sourceJobId: j.sourceJobId } },
    });
    if (!job) continue;
    await prisma.bookmark.upsert({
      where: { jobId: job.id },
      update: { status: j.bookmark.status },
      create: { jobId: job.id, status: j.bookmark.status },
    });
  }

  // 3) 단일 로컬 사용자 조건 1행 (id="local")
  await prisma.userPreference.upsert({
    where: { id: "local" },
    update: {},
    create: {
      id: "local",
      roles: JSON.stringify(MOCK_PREFERENCE.roles),
      locations: JSON.stringify(MOCK_PREFERENCE.locations),
      experience: MOCK_PREFERENCE.experience,
      keywords: JSON.stringify(MOCK_PREFERENCE.keywords),
    },
  });

  const jobCount = await prisma.job.count();
  const bmCount = await prisma.bookmark.count();
  console.log(`seed 완료: Job ${jobCount}건, Bookmark ${bmCount}건, UserPreference 1행`);
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
