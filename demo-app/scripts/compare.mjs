// 두 PNG(변경 전/후)를 픽셀 단위로 비교해 차이를 수치로 보고하고,
// 차이 영역을 표시한 diff 이미지를 저장한다.
//
// 사용법:
//   node scripts/compare.mjs <before.png> <after.png> [diff출력.png]
//
// 출력: 마지막 줄에 JSON 한 줄 (서브에이전트가 파싱)
//   { identical, diffPixels, totalPixels, diffPercent, dimensionMismatch, diffImage }
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'
import { PNG } from 'pngjs'
import pixelmatch from 'pixelmatch'

const beforePath = process.argv[2]
const afterPath = process.argv[3]
const diffPath = process.argv[4] || 'screenshots/diff.png'

if (!beforePath || !afterPath) {
  console.error('사용법: node scripts/compare.mjs <before.png> <after.png> [diff.png]')
  process.exit(1)
}

const before = PNG.sync.read(readFileSync(beforePath))
const after = PNG.sync.read(readFileSync(afterPath))

// 크기가 다르면 레이아웃 자체가 바뀐 것 — 픽셀 비교 불가, 그 사실을 명확히 보고
if (before.width !== after.width || before.height !== after.height) {
  console.log(
    JSON.stringify({
      identical: false,
      dimensionMismatch: true,
      before: { width: before.width, height: before.height },
      after: { width: after.width, height: after.height },
      note: '이미지 크기가 달라 픽셀 비교 불가 — 요소 크기가 변경됨',
    }),
  )
  process.exit(0)
}

const { width, height } = before
const diff = new PNG({ width, height })

const diffPixels = pixelmatch(before.data, after.data, diff.data, width, height, {
  threshold: 0.1, // 민감도(0=가장 민감, 1=둔감). 2단계에서 조절 대상.
})

mkdirSync(dirname(diffPath), { recursive: true })
writeFileSync(diffPath, PNG.sync.write(diff))

const totalPixels = width * height
const diffPercent = Number(((diffPixels / totalPixels) * 100).toFixed(3))

console.log(
  JSON.stringify({
    identical: diffPixels === 0,
    dimensionMismatch: false,
    diffPixels,
    totalPixels,
    diffPercent,
    width,
    height,
    diffImage: diffPath,
  }),
)
