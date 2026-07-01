# 논문 분석 AI 파이프라인 (Paper-Analysis AI)

링크 하나로 논문을 **읽고 → 상세 해설 → 코드 분석 → 실행 → 디자인 → HTML 리포트**까지
자동 산출하는 멀티 에이전트 시스템. 각 단계는 스킬 + 전담 에이전트로 구성되고,
오케스트레이터/OS가 복잡도에 따라 **최적 에이전트 수**를 정해 전체를 실행한다.

---

## 1. 스킬 (7개) — `.claude/skills/`

| 스킬 | 호출 | 역할 | 산출물 |
|---|---|---|---|
| analyzer | `/analyzer <링크>` | 논문을 읽고 구조적으로 분석 | `output/01_analysis.md` |
| mydesign | `/mydesign` | 깔끔한 하얀색·고가독성 디자인 시스템(CSS) | `output/design.css` |
| html | `/html` | mydesign으로 분석 결과를 HTML로 렌더 | `output/report.html` |
| detail | `/detail` | 누구나 이해할 수 있게 상세 해설 | `output/03_detail.md` |
| code | `/code` | 논문의 구현 코드 분석(논문↔코드 매핑) | `output/04_code.md` |
| code-run | `/code-run` (= 코드실행!) | 구현 코드를 최소 재현으로 **실제 실행** | `output/05_run.md`, `output/run/` |
| feedback | `/feedback <단계>` | 각 단계가 잘 됐는지 검증·피드백(게이트) | `output/feedback_<단계>.md` |

> 참고: `/코드실행!` 은 슬래시 이름 규칙(영문 소문자/하이픈) 때문에 **`/code-run`** 으로 만들었습니다.

## 2. 에이전트 (8개) — `.claude/agents/`

각 스킬을 실행하는 전담 에이전트 + 총괄 오케스트레이터:

- `analyzer-agent`, `detail-agent`, `code-agent`, `code-run-agent`,
  `mydesign-agent`, `html-agent`, `feedback-agent`
- `orchestrator` — 링크를 받아 복잡도를 재고 단계별 에이전트 수를 정해 전 과정을 지휘

## 3. OS (오케스트레이션 시스템) — `.claude/workflows/paper-os.js`

링크를 입력받아 **자동으로** 전체를 돌리는 워크플로우 "OS".

1. **Triage**: 링크를 가볍게 훑어 복잡도(low/medium/high) 산정
2. **에이전트 수 산정**:
   - low → 각 단계 1개
   - medium → detail 2~3 병렬, 나머지 1
   - high → detail 3~5 병렬 + code 2~4 병렬(모듈별), 이후 병합
   - 동시성 상한(기본 5) 준수
3. **실행**: analyzer → detail → code → code-run → mydesign → html
4. **게이트**: 매 단계 뒤 feedback로 PASS/FAIL 검증, FAIL이면 1회 재시도
5. **산출**: 모든 마크다운 + 최종 `output/report.html`

---

## 실행 방법

### A. OS로 전 과정 자동 실행 (권장)
오케스트레이터/OS에게 링크만 주면 됩니다. 예:

> "이 논문 분석해줘: https://arxiv.org/abs/XXXX.XXXXX"

내부적으로 `paper-os` 워크플로우가 복잡도를 재고 에이전트 수를 정해 파이프라인을 돌립니다.
(워크플로우 실행은 멀티 에이전트라 비용이 크므로, 사용자가 명시적으로 요청할 때만 구동합니다.)

### B. 단계별 수동 실행
```
/analyzer https://arxiv.org/abs/XXXX.XXXXX
/detail
/code
/code-run
/mydesign
/html
/feedback analysis      # 임의 단계 검증
```

### C. 오케스트레이터 에이전트 직접 호출
"orchestrator 에이전트로 이 링크 전체 파이프라인 돌려줘: <링크>"

---

## 산출물 디렉토리 (`output/`)
```
output/
├─ 01_analysis.md     # 논문 분석
├─ 03_detail.md       # 상세 해설
├─ 04_code.md         # 구현 코드 분석
├─ 05_run.md          # 실행 리포트
├─ run/               # 실행 가능한 최소 재현 코드
├─ design.css         # 디자인 시스템
├─ report.html        # 최종 HTML 리포트 ← 결과물
└─ feedback_*.md      # 단계별 검증 리포트
```

## 데이터 흐름
```
링크 ─▶ analyzer ─▶ 01_analysis.md ─┬─▶ detail ─▶ 03_detail.md ─┐
                                     ├─▶ code   ─▶ 04_code.md ──▶ code-run ─▶ 05_run.md
                                     │                                          │
        mydesign ─▶ design.css ──────┴───────────────  html  ◀─────────────────┘
                                                         └─▶ report.html
        (각 화살표 뒤에 feedback 게이트)
```
