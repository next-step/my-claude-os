// 현재 촬영된 스크린샷을 "기준선(before)"으로 고정(승인)한다. (OS.md 7단계 — 기준 도입)
//
// 시각 회귀의 before/after 에서 before 를 만드는 단계.
//   - 처음 한 번: 현재 모습을 기준선으로 박는다(visual-check 로 기준선이 안 깨졌는지 검증 후).
//   - 변경을 "이건 의도된 새 정상"으로 받아들일 때: 다시 찍어 기준선 갱신(accept/bless).
//
// 사용법: node scripts/snapshot-baseline.mjs [target]
//   target 기본값: card
// 산출물: screenshots/<target>/baseline/<id>.png, baseline/measurements.json
import { mkdir, readdir, copyFile, writeFile, readFile } from 'node:fs/promises'

const target = process.argv[2] || 'card'
const srcDir = `screenshots/${target}`
const dstDir = `${srcDir}/baseline`

let files
try {
  files = (await readdir(srcDir)).filter((f) => f.endsWith('.png'))
} catch {
  console.error(`촬영 결과가 없다: ${srcDir} — 먼저 npm run capture -- ${target} 를 돌려라.`)
  process.exit(1)
}
if (files.length === 0) {
  console.error(`${srcDir} 에 .png 가 없다 — 먼저 npm run capture -- ${target} 를 돌려라.`)
  process.exit(1)
}

await mkdir(dstDir, { recursive: true })
for (const f of files) await copyFile(`${srcDir}/${f}`, `${dstDir}/${f}`)

// measurements 도 함께 박아둔다(나중에 코드 사실의 before/after 비교용).
try {
  const m = await readFile(`${srcDir}/measurements.json`)
  await writeFile(`${dstDir}/measurements.json`, m)
} catch {
  /* measurements 없으면 스샷만 */
}

console.log(
  JSON.stringify({ ok: true, target, baseline: dstDir, frozen: files.length }, null, 2),
)
