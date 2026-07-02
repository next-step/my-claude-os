# 논문 분석 AI 파이프라인 (Paper-Analysis AI)

링크 하나로 논문을 **의도 확정 → 읽고 → 상세 해설 → 코드 분석 → 실행 준비 → 디자인 → HTML 리포트**까지
자동 산출하는 멀티 에이전트 시스템. 각 단계는 스킬 + 전담 에이전트로 구성되고,
오케스트레이터/OS가 복잡도에 따라 **최적 에이전트 수**를 정해 전체를 실행한다.

맨 앞의 **interview** 단계가 사용자 의도를 `00_intent.md`로 확정하면, 이후 모든 단계가 그 파일을
스킬 기본값보다 **우선** 읽어 "의도와 어긋난 산출물"을 시작 시점에 방지한다.

---

## 1. 스킬 (8개) — `.claude/skills/`

| 스킬 | 호출 | 역할 | 산출물 |
|---|---|---|---|
| interview | `/interview <링크>` | 파이프라인 전에 **의도 확정**(가정추출·빈틈탐지·대안제시) | `output/<slug>/00_intent.md` |
| analyzer | `/analyzer <링크>` | 논문을 읽고 구조적으로 분석 | `output/<slug>/01_analysis.md` |
| detail | `/detail` | 누구나 이해할 수 있게 상세 해설 | `output/<slug>/03_detail.md` |
| code | `/code` | 논문의 구현 코드 분석(논문↔코드 매핑) | `output/<slug>/04_code.md` |
| code-run | `/code-run` (= 코드실행!) | **원본 레포**를 사용자 터미널에서 돌릴 복붙 명령·가이드 발행(클로드 미실행) | `output/<slug>/05_run.md`, `run/` |
| mydesign | `/mydesign` | 깔끔한 하얀색·고가독성 디자인 시스템(CSS) | `output/<slug>/design.css` |
| html | `/html` | mydesign으로 분석 결과를 HTML로 렌더 | `output/<slug>/report.html` |
| feedback | `/feedback <단계>` | 각 단계가 잘 됐는지 검증·피드백(게이트) | `output/<slug>/feedback_<단계>.md` |

> 참고 1: `/코드실행!` 은 슬래시 이름 규칙(영문 소문자/하이픈) 때문에 **`/code-run`** 으로 만들었습니다.
> 참고 2: code-run은 이제 **재구현 토이를 클로드가 자동실행하지 않습니다.** 원본 레포 clone→실행 명령 블록과
> 관찰 가이드를 발행하고, 실행·관찰은 사용자가 자기 터미널에서 합니다(GPU 없을 때만 오프라인 스모크 테스트 fallback).

## 2. 에이전트 (9개) — `.claude/agents/`

각 스킬을 실행하는 전담 에이전트 + 총괄 오케스트레이터:

- `interview-agent`, `analyzer-agent`, `detail-agent`, `code-agent`, `code-run-agent`,
  `mydesign-agent`, `html-agent`, `feedback-agent`
- `orchestrator` — 링크를 받아 복잡도를 재고 단계별 에이전트 수를 정해 전 과정을 지휘

> `interview-agent`는 **대화형**(사용자에게 되묻기)이라 백그라운드 워크플로우 안에서 돌리지 않는다.
> `paper-os` 실행 **전에** 메인 대화에서 단독 실행해 `00_intent.md`를 만든다.

## 3. OS (오케스트레이션 시스템) — `.claude/workflows/paper-os.js`

링크를 입력받아 **자동으로** 전체를 돌리는 워크플로우 "OS".

1. **Triage**: 링크를 가볍게 훑어 복잡도(low/medium/high) 산정 + 논문 slug 확정
   (이미 `output/<slug>/00_intent.md`가 있으면 그 폴더명을 slug으로 재사용)
2. **Intent**: `00_intent.md`가 있으면 로드 → 이후 모든 단계가 그 의도를 기본값보다 우선 적용
3. **에이전트 수 산정**:
   - low → 각 단계 1개
   - medium → detail 2~3 병렬, 나머지 1
   - high → detail 3~5 병렬 + code 2~4 병렬(모듈별), 이후 병합
   - 동시성 상한(기본 5) 준수
4. **실행**: analyzer → detail → code → code-run → mydesign → html
5. **게이트**: 매 단계 뒤 feedback로 PASS/FAIL 검증, FAIL이면 1회 재시도
6. **산출**: 모든 마크다운 + 최종 `output/<slug>/report.html`

---

## 실행 방법

### A. 권장 흐름 — 의도 확정 후 OS 자동 실행
```
/interview https://arxiv.org/abs/XXXX.XXXXX   # ① 대화형: 의도를 00_intent.md로 확정
```
그 다음 링크를 주면 `paper-os`가 그 의도를 읽어 전 과정을 돌립니다:

> "이 논문 분석해줘: https://arxiv.org/abs/XXXX.XXXXX"

(워크플로우 실행은 멀티 에이전트라 비용이 크므로, 사용자가 명시적으로 요청할 때만 구동합니다.
 interview를 건너뛰면 각 단계는 스킬 기본값으로 진행합니다.)

### B. 단계별 수동 실행
```
/interview https://arxiv.org/abs/XXXX.XXXXX   # 선택: 의도 먼저 확정
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

## 산출물 디렉토리 (`output/<slug>/`)
각 논문은 자체 폴더(`output/<slug>/`)에 정리된다(예: `output/joyaivl_2606.14777/`).
```
output/<slug>/
├─ 00_intent.md       # 의도 스펙 (/interview) ← 모든 단계가 우선 참조
├─ 01_analysis.md     # 논문 분석
├─ 03_detail.md       # 상세 해설
├─ 04_code.md         # 구현 코드 분석
├─ 05_run.md          # 원본 실행 준비 리포트(사용자 터미널용 가이드)
├─ run/               # (fallback) GPU 없을 때 배선 확인용 오프라인 스모크 테스트
├─ design.css         # 디자인 시스템
├─ report.html        # 최종 HTML 리포트 ← 결과물
└─ feedback_*.md      # 단계별 검증 리포트
```

## 데이터 흐름
```
/interview ─▶ 00_intent.md ─────────────────────────────────────────┐
                                                (모든 단계가 우선 참조) │
링크 ─▶ analyzer ─▶ 01_analysis.md ─┬─▶ detail ─▶ 03_detail.md ─┐     │
                                     ├─▶ code   ─▶ 04_code.md ──▶ code-run ─▶ 05_run.md
                                     │                                          │
        mydesign ─▶ design.css ──────┴───────────────  html  ◀─────────────────┘
                                                         └─▶ report.html
        (각 화살표 뒤에 feedback 게이트)
```
