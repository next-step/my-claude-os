// 시각 회귀 드릴다운 갤러리 — before | after | diff 를 변형마다 나란히 본다.
//
// 위치(OS.md 합의): 회귀 보고의 1순위 출력은 "대화창 마크다운 표"다. 이 HTML 은
//   빨간 줄(의도 외 변경)이 있을 때만 눈으로 확인하는 **드릴다운**이다.
//   바뀐 변형만 싣는다(안 바뀐 건 볼 게 없으므로).
//
// 사용법: node scripts/build-regress-gallery.mjs [target] [--open]
//   screenshots/<target>/regress.json (regress-diff 산출) 을 읽는다.
//   스킬이 comparator 판정을 각 result 에 verdict(same|expected|unexpected)/note 로 머지해 두면 함께 표시.
import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { spawn } from 'node:child_process'

const target = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : 'card'
const open = process.argv.includes('--open')
const dir = `screenshots/${target}`
const dataPath = `${dir}/regress.json`

if (!existsSync(dataPath)) {
  console.error(`${dataPath} 가 없다 — 먼저 npm run regress -- ${target} 를 돌려라.`)
  process.exit(1)
}

const data = JSON.parse(readFileSync(dataPath, 'utf8'))
const changed = data.results.filter((r) => r.changed)

const VERDICT = {
  unexpected: { tag: '🔴 의도 외 변경', cls: 'bad' },
  expected: { tag: '🟢 의도된 변경', cls: 'ok' },
  same: { tag: '⚪ 변화 없음', cls: 'neu' },
}

const esc = (s) => String(s ?? '').replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]))

const cell = (label, src) =>
  src && existsSync(`${dir}/${src}`)
    ? `<figure><figcaption>${label}</figcaption><img src="${src}" alt="${label}"></figure>`
    : `<figure class="missing"><figcaption>${label}</figcaption><div class="ph">${src ? '없음' : '—'}</div></figure>`

const rows = changed
  .map((r) => {
    const v = VERDICT[r.verdict] || { tag: '⚠️ 미판정', cls: 'neu' }
    const metric = r.dimensionMismatch
      ? `크기 변경 (픽셀 비교 불가)`
      : r.diffPercent != null
        ? `픽셀 차이 ${r.diffPercent}%`
        : r.reason || ''
    return `<section class="card ${v.cls}">
      <header><h2>${esc(r.id)}</h2><span class="verdict">${v.tag}</span></header>
      <div class="meta">${esc(metric)}${r.note ? ` · <em>${esc(r.note)}</em>` : ''}</div>
      <div class="trip">
        ${cell('before (기준선)', `baseline/${r.id}.png`)}
        ${cell('after (현재)', `${r.id}.png`)}
        ${cell('diff (바뀐 픽셀)', r.diffImage ? `diff/${r.id}.png` : null)}
      </div>
    </section>`
  })
  .join('\n')

const html = `<!doctype html><html lang="ko"><meta charset="utf-8">
<title>시각 회귀 드릴다운 — ${esc(target)}</title>
<style>
  body{font:14px/1.5 -apple-system,system-ui,sans-serif;margin:0;background:#0f172a;color:#e2e8f0;padding:24px}
  h1{font-size:18px;margin:0 0 4px}
  .sub{color:#94a3b8;margin:0 0 20px}
  .card{background:#1e293b;border:1px solid #334155;border-radius:12px;padding:16px;margin:0 0 16px}
  .card.bad{border-color:#ef4444}
  .card.ok{border-color:#22c55e}
  header{display:flex;align-items:center;justify-content:space-between;gap:12px}
  h2{font-size:15px;margin:0}
  .verdict{font-size:13px;font-weight:600}
  .meta{color:#94a3b8;font-size:13px;margin:6px 0 12px}
  .trip{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
  figure{margin:0;text-align:center}
  figcaption{font-size:12px;color:#94a3b8;margin-bottom:6px}
  img{max-width:100%;border-radius:8px;background:#fff;border:1px solid #334155}
  .ph{height:120px;display:flex;align-items:center;justify-content:center;color:#64748b;border:1px dashed #475569;border-radius:8px}
</style>
<h1>🔬 시각 회귀 드릴다운 — ${esc(target)}</h1>
<p class="sub">바뀐 변형 ${changed.length} / 전체 ${data.total} · before·after·diff 나란히 비교</p>
${rows || '<p>바뀐 변형이 없다 — 회귀 없음.</p>'}
</html>`

const out = `${dir}/regress.html`
writeFileSync(out, html)
console.log(JSON.stringify({ ok: true, target, out, changed: changed.length }))

if (open) {
  const cmd = process.platform === 'darwin' ? 'open' : process.platform === 'win32' ? 'start' : 'xdg-open'
  spawn(cmd, [out], { detached: true, stdio: 'ignore' }).unref()
}
