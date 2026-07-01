// ============================================================================
// npm run collect — 수집 실행 진입점 (OS.md 12.2/12.7: M1 = 수동 실행)
// ----------------------------------------------------------------------------
// [현 단계] 실 SaraminAdapter/Normalizer 미구현 → MockAdapter 로 파이프라인 형태만 시연.
//   사람인 API 실연동은 "이용신청 → 승인" 후 다음 M1 작업에서 교체.
//   (승인 전에는 이 스크립트가 mock RawJob 만 출력한다.)
//
// 다음 작업 예정 흐름:
//   adapter.fetchRaw() → Normalizer(RawJob→Job, 코드→라벨, dedupKey, dataQuality)
//     → prisma.job.upsert({ where:(source,sourceJobId) })   // idempotent 재수집
// ============================================================================

import { MockAdapter } from "../src/lib/collect/source-adapter";

async function main() {
  const adapter = new MockAdapter();
  const raws = await adapter.fetchRaw();
  console.log(`[collect] source=${adapter.source} fetched ${raws.length} raw jobs`);
  console.log("[collect] (Normalizer/DB upsert 는 다음 M1 작업에서 연결)");
  console.log(JSON.stringify(raws, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
