export const meta = {
  name: 'paper-os',
  description: 'Paper-analysis OS: given a paper link, gauge complexity, decide optimal agent count per stage, run analyzer→detail→code→run→design→html with feedback gates.',
  whenToUse: 'User provides a paper link and wants the full end-to-end paper-analysis pipeline run automatically.',
  phases: [
    { title: 'Triage', detail: 'Fetch the link, measure complexity, decide agent counts' },
    { title: 'Analyze', detail: 'analyzer skill → 01_analysis.md (gated)' },
    { title: 'Detail', detail: 'detail skill → 03_detail.md (parallel by concept on complex papers)' },
    { title: 'Code', detail: 'code skill → 04_code.md (parallel by module on large repos)' },
    { title: 'Run', detail: 'code-run skill → 05_run.md' },
    { title: 'Design', detail: 'mydesign skill → design.css' },
    { title: 'Render', detail: 'html skill → report.html' },
  ],
}

// ── config ──────────────────────────────────────────────────────────────────
const ROOT = 'C:/Users/wagra/claude/code'
const SK = (n) => `${ROOT}/.claude/skills/${n}/SKILL.md`
// Custom .claude/agents/* are NOT resolvable inside the workflow runtime, so each
// stage runs on the default workflow agent and is told to READ its SKILL.md and
// follow it. Output files go under ROOT/output/.

const LINK = typeof args === 'string' ? args : (args && args.link)
const MAX_PARALLEL = (args && args.maxParallel) || 5
if (!LINK) throw new Error('paper-os: no paper link provided in args')

const GATE_SCHEMA = {
  type: 'object',
  required: ['verdict', 'score', 'must_fix'],
  properties: {
    verdict: { type: 'string', enum: ['PASS', 'FAIL'] },
    score: { type: 'number' },
    must_fix: { type: 'array', items: { type: 'string' } },
    path: { type: 'string' },
  },
}

const PLAN_SCHEMA = {
  type: 'object',
  required: ['complexity', 'detail_agents', 'code_agents', 'rationale', 'concepts', 'modules'],
  properties: {
    complexity: { type: 'string', enum: ['low', 'medium', 'high'] },
    detail_agents: { type: 'number' },
    code_agents: { type: 'number' },
    concepts: { type: 'array', items: { type: 'string' } },
    modules: { type: 'array', items: { type: 'string' } },
    rationale: { type: 'string' },
  },
}

// ── helper: run a stage, gate it, retry once on FAIL ────────────────────────
async function gated(stageName, file, runStage) {
  let out = await runStage()
  let gate = await agent(
    `${ROOT} 작업 디렉토리에서, ${SK('feedback')} 파일을 Read로 읽고 그 체크리스트에 따라 '${stageName}' 단계 산출물 ${ROOT}/${file} 을 검증하라. ${ROOT}/output/feedback_${stageName}.md 로 저장하고 구조화 결과(verdict/score/must_fix/path)를 반환.`,
    { phase: 'Gate:' + stageName, schema: GATE_SCHEMA, label: `gate:${stageName}` }
  )
  if (gate && gate.verdict === 'FAIL') {
    log(`[${stageName}] FAIL (${gate.score}/10) → 재시도: ${gate.must_fix.join('; ')}`)
    out = await runStage(gate.must_fix)
    gate = await agent(
      `${ROOT} 에서 ${SK('feedback')} 를 읽고 '${stageName}' 재검증: ${ROOT}/${file}. 직전 지적사항: ${gate.must_fix.join('; ')}. 구조화 결과 반환.`,
      { phase: 'Gate:' + stageName, schema: GATE_SCHEMA, label: `gate:${stageName}:retry` }
    )
  }
  return { out, gate }
}

// ── Phase 1: Triage ─────────────────────────────────────────────────────────
phase('Triage')
const plan = await agent(
  `다음 논문 링크를 WebFetch로 가볍게 훑어 복잡도를 산정하라: ${LINK}
신호: 길이/섹션 수, 수식·정리 밀도, 서브시스템/모듈 수, 코드 저장소 규모, 실험 수.
규칙:
- low → detail_agents=1, code_agents=1
- medium → detail_agents=2~3, code_agents=1
- high → detail_agents=3~5, code_agents=2~4
detail은 'concepts'(개념 그룹 라벨 배열)로, code는 'modules'(모듈 라벨 배열)로 분할 단위를 제시.
동시성 상한 ${MAX_PARALLEL}을 넘기지 말 것. 근거를 rationale에 적어라.`,
  { phase: 'Triage', schema: PLAN_SCHEMA, label: 'triage' }
)
const nDetail = Math.max(1, Math.min(plan.detail_agents || 1, MAX_PARALLEL))
const nCode = Math.max(1, Math.min(plan.code_agents || 1, MAX_PARALLEL))
log(`복잡도=${plan.complexity} · detail×${nDetail} · code×${nCode} — ${plan.rationale}`)

// ── Phase 2: Analyze (single, gated) ────────────────────────────────────────
phase('Analyze')
const analysis = await gated('analysis', 'output/01_analysis.md', (fixes) =>
  agent(
    `작업 디렉토리 ${ROOT}. ${SK('analyzer')} 를 Read로 읽고 그 절차를 정확히 따라, 이 논문을 분석해 ${ROOT}/output/01_analysis.md 를 Write로 생성하라: ${LINK}` +
      (fixes ? `\n이전 피드백 반영: ${fixes.join('; ')}` : '') +
      `\n끝나면 파일 경로 + 제목 + 한 줄 요약 + 공식 코드 저장소 링크(있으면)를 반환.`,
    { phase: 'Analyze', label: 'analyzer' }
  )
)

// ── Phase 3: Detail (split by concept on complex papers, then merge) ─────────
phase('Detail')
const conceptLabels = (plan.concepts && plan.concepts.length ? plan.concepts : ['전체']).slice(0, nDetail)
let detailRun
if (conceptLabels.length <= 1) {
  detailRun = (fixes) => agent(
    `${ROOT} 에서 ${SK('detail')} 를 읽고 그 절차대로 ${ROOT}/output/01_analysis.md 를 풀어 ${ROOT}/output/03_detail.md 를 생성하라.` +
      (fixes ? `\n이전 피드백: ${fixes.join('; ')}` : ''),
    { phase: 'Detail', label: 'detail' })
} else {
  detailRun = async () => {
    await parallel(conceptLabels.map((c, i) => () =>
      agent(`${ROOT} 에서 ${SK('detail')} 의 방식대로 '${c}' 개념만 상세 해설하여 ${ROOT}/output/03_detail_part${i}.md 로 저장.`,
        { phase: 'Detail', label: `detail:${c}` })))
    return agent(`${ROOT}/output/03_detail_part*.md 들을 Read로 모두 읽어 하나로 병합·정리해 ${ROOT}/output/03_detail.md 생성(중복 제거, 목차 추가).`,
      { phase: 'Detail', label: 'detail:merge' })
  }
}
const detail = await gated('detail', 'output/03_detail.md', detailRun)

// ── Phase 4: Code (split by module on large repos, then merge) ───────────────
phase('Code')
const moduleLabels = (plan.modules && plan.modules.length ? plan.modules : ['전체']).slice(0, nCode)
let codeRun
if (moduleLabels.length <= 1) {
  codeRun = (fixes) => agent(
    `${ROOT} 에서 ${SK('code')} 를 읽고 그 절차대로 구현 저장소를 찾아 분석하고 ${ROOT}/output/04_code.md 를 생성하라. 논문 분석은 ${ROOT}/output/01_analysis.md 참고.` +
      (fixes ? `\n이전 피드백: ${fixes.join('; ')}` : ''),
    { phase: 'Code', label: 'code' })
} else {
  codeRun = async () => {
    await parallel(moduleLabels.map((m, i) => () =>
      agent(`${ROOT} 에서 ${SK('code')} 방식대로 구현 저장소에서 '${m}' 모듈/하위시스템만 분석하여 ${ROOT}/output/04_code_part${i}.md 저장. 단서는 ${ROOT}/output/01_analysis.md.`,
        { phase: 'Code', label: `code:${m}` })))
    return agent(`${ROOT}/output/04_code_part*.md 들을 Read로 읽어 병합해 ${ROOT}/output/04_code.md 생성(논문↔코드 매핑 표 통합).`,
      { phase: 'Code', label: 'code:merge' })
  }
}
const code = await gated('code', 'output/04_code.md', codeRun)

// ── Phase 5: Run ────────────────────────────────────────────────────────────
phase('Run')
const run = await gated('run', 'output/05_run.md', (fixes) =>
  agent(`${ROOT} 에서 ${SK('code-run')} 를 읽고 그 절차대로 ${ROOT}/output/04_code.md 기반 최소 재현 데모를 ${ROOT}/output/run/ 에 만들고 실제 실행하여 ${ROOT}/output/05_run.md 생성. 토이 입력으로 동작만 증명(대규모 학습 금지). 못 돌리면 사유와 필요조건을 솔직히 기록.` +
    (fixes ? `\n이전 피드백: ${fixes.join('; ')}` : ''),
    { phase: 'Run', label: 'code-run' }))

// ── Phase 6: Design ─────────────────────────────────────────────────────────
phase('Design')
const design = await agent(
  `${ROOT} 에서 ${SK('mydesign')} 를 읽고 그 절차대로 ${ROOT}/output/design.css 를 생성(순백·고대비·720px 규약).`,
  { phase: 'Design', label: 'mydesign' })

// ── Phase 7: Render ─────────────────────────────────────────────────────────
phase('Render')
const html = await gated('html', 'output/report.html', (fixes) =>
  agent(`${ROOT} 에서 ${SK('html')} 를 읽고 그 절차대로 ${ROOT}/output/01_analysis.md, 03_detail.md, 04_code.md 와 design.css 를 합쳐 자급식(인라인 CSS) ${ROOT}/output/report.html 생성.` +
    (fixes ? `\n이전 피드백: ${fixes.join('; ')}` : ''),
    { phase: 'Render', label: 'html' }))

// ── Summary ─────────────────────────────────────────────────────────────────
return {
  link: LINK,
  plan: { complexity: plan.complexity, detail_agents: nDetail, code_agents: nCode, rationale: plan.rationale },
  gates: {
    analysis: analysis.gate && analysis.gate.verdict,
    detail: detail.gate && detail.gate.verdict,
    code: code.gate && code.gate.verdict,
    run: run.gate && run.gate.verdict,
    html: html.gate && html.gate.verdict,
  },
  outputs: [
    'output/01_analysis.md', 'output/03_detail.md', 'output/04_code.md',
    'output/05_run.md', 'output/design.css', 'output/report.html',
  ],
  final: 'output/report.html',
}
