# Paper-Analysis AI 🧬📄

논문 링크 하나로 **읽기 → 상세 해설 → 코드 분석 → 실제 실행 → 디자인 → HTML 리포트**까지
자동 산출하는 Claude Code 기반 멀티 에이전트 시스템.

링크를 주면 **OS(오케스트레이터)** 가 논문 복잡도를 측정해 단계별 **최적 에이전트 수**를 정하고,
7개 스킬 + 8개 에이전트로 전체 파이프라인을 게이트 검증과 함께 실행합니다.

## 구성

| 구분 | 위치 | 내용 |
|---|---|---|
| 스킬 7개 | `.claude/skills/` | analyzer · mydesign · html · detail · code · code-run · feedback |
| 에이전트 8개 | `.claude/agents/` | 각 스킬 전담 7개 + orchestrator |
| OS | `.claude/workflows/paper-os.js` | 복잡도 산정 → 에이전트 수 결정 → 파이프라인 실행 |
| 파이프라인 문서 | `PAPER-AI-PIPELINE.md` | 전체 단계·데이터 흐름 상세 |

## 사용법

Claude Code에서 논문 링크를 주면 됩니다:

```
이 논문 분석해줘: https://arxiv.org/abs/XXXX.XXXXX
```

또는 단계별 수동 실행:

```
/analyzer <링크>   →  /detail  →  /code  →  /code-run  →  /mydesign  →  /html
/feedback <단계>    # 임의 단계 검증
```

## 예시 산출물 (`output/`)

LiveEdit(*Towards Real-Time Diffusion-Based Streaming Video Editing*) 논문을 분석한 결과:

- `01_analysis.md` — 논문 분석
- `03_detail.md` — 상세 해설
- `04_code.md` — 구현 코드 분석 (논문↔코드 매핑)
- `05_run.md` + `run/` — **실제 실행 리포트 + 재현 코드** (4개 핵심 메커니즘 CPU 검증, ALL PASS)
- `report.html` — 최종 HTML 리포트 (자급식, 순백 디자인)

## 다른 PC에서 이어서 작업하기

```bash
git clone https://github.com/wagranungyo/my-claude-code-os.git
cd my-claude-code-os
git checkout step2-1
```

그 뒤 이 폴더를 **Claude Code 작업 디렉토리로 열면** `.claude/`의 스킬·에이전트·`paper-os` 워크플로우가 자동 인식됩니다.

- **경로 이식성**: `paper-os.js`의 `ROOT`는 기본값이 `.`(현재 작업 디렉토리)라 어느 PC·어느 경로에서도 그대로 동작합니다. 필요 시 `args`를 객체로 넘겨 `{ link, root, maxParallel }` 형태로 절대경로를 지정할 수 있습니다.
- **권한 설정**: `settings.local.json`은 머신별 절대경로/권한이 담겨 있어 git에서 제외됩니다(`.gitignore`). 새 PC에서는 권한 프롬프트가 다시 뜨며 그때 허용하면 됩니다.
- **`/code-run` 요구사항**: 실제 코드 실행 단계는 Python + torch·numpy가 필요합니다. 새 PC에 Python 환경을 준비하세요.

## 데이터 흐름

```
링크 ─▶ analyzer ─▶ 01_analysis ─┬─▶ detail ─▶ 03_detail ─┐
                                  ├─▶ code   ─▶ 04_code ──▶ code-run ─▶ 05_run
        mydesign ─▶ design.css ───┴──────────  html  ◀──────────┘─▶ report.html
        (각 화살표 뒤 feedback 게이트: FAIL 시 1회 재시도)
```

---
🤖 Built with [Claude Code](https://claude.com/claude-code)
