---
name: factcheck
description: 하나의 주장(claim)을 놓고 웹 리서치 에이전트를 찬성·반대 두 관점으로 병렬 호출해 적대적으로 검증하고, 사실/거짓 판정과 근거·출처가 담긴 HTML 판정 카드를 만들어 브라우저로 띄운다. 사용자가 "팩트체크", "이거 사실이야?", "진짜인지 확인해줘"라고 요청할 때 사용한다.
user-invocable: true
allowed-tools:
  - Bash(python3:*)
  - Read
  - Write
---

# /factcheck — 주장 팩트체크 스킬

주장 하나를 받아 **찬성 근거와 반대 근거를 동시에(적대적으로) 조사**한 뒤,
판정(사실/거짓 등)과 근거·신뢰도를 담은 HTML 판정 카드를 만들어 브라우저로 띄운다.

전달된 인자: `$ARGUMENTS` — 검증할 주장. (예: `/factcheck 대한민국은 2026 월드컵 32강에 진출했다`)
인자가 없으면 사용자에게 검증할 주장을 물어본다.

이 스킬의 핵심은 **공유 에이전트 `web-researcher`를 찬/반 두 관점으로 병렬 호출**하는 것이다.
한 관점만 조사하면 확증편향에 빠진다 — 반박 근거를 일부러 찾게 해서 판정의 신뢰도를 높인다.

```
  /factcheck "<주장>"
        │  ① 같은 주장을 두 관점으로 동시에 조사
        ▼
  web-researcher(supporting)   web-researcher(refuting)
   뒷받침 근거 수집               반박 근거 수집
        │                          │
        └────────────┬─────────────┘
                     ▼  ② 양측 근거를 종합해 판정 결정
                     ▼  ③ generate_report.py 실행 → 판정 카드 HTML + 브라우저
```

> `web-researcher`는 `/briefing` 스킬도 함께 쓰는 **공유 서브에이전트**다. 조사 엔진은 하나,
> 사용하는 방식(중립 다각도 vs 찬반 적대)만 스킬마다 다르다.

---

## 진행 순서

### 1. web-researcher를 찬/반 병렬 호출
**반드시 한 번의 응답 안에서 두 개의 Agent 호출을 동시에** 실행한다(병렬).
- 호출 A — `brief`: "주장: <주장>", `angle`: `supporting`, `max_findings`: 3~4.
- 호출 B — `brief`: "주장: <주장>", `angle`: `refuting`, `max_findings`: 3~4.

각 에이전트는 정해진 스키마의 JSON(`verdict_hint`·`confidence`·`findings`·`sources` 포함)을 반환한다
(`.claude/agents/web-researcher.md` 참고). 한쪽이 실패하면 다른 쪽 결과만으로 진행하되, 판정 신뢰도를 낮춘다.

### 2. 양측 근거로 판정 결정
두 에이전트의 `findings`(supporting/refuting)와 `verdict_hint`·`confidence`를 종합해 **최종 판정**을 정한다:
- `verdict` — `사실` / `대체로 사실` / `부분적 사실` / `근거 불충분` / `대체로 거짓` / `거짓` 중 하나.
- `confidence` — `high` / `medium` / `low`. 양측 근거가 충돌하거나 출처가 빈약하면 낮춘다.
- 판정은 **근거의 무게로** 결정한다. 반박 근거가 더 확실하면 주저 없이 거짓 쪽으로 판정한다.

### 3. 결과를 data.json으로 병합
`.claude/skills/factcheck/data.json`을 Write 한다:
- `claim` — 검증한 주장.
- `updated_at` — 오늘 날짜(YYYY-MM-DD).
- `verdict`, `confidence` — 위에서 정한 값.
- `rationale` — 왜 그렇게 판정했는지 2~3문장(직접 작성).
- `supporting` — 뒷받침 근거 배열(각 `{ point, detail, source }`).
- `refuting` — 반박 근거 배열(각 `{ point, detail, source }`).
- `sources` — 양측 `sources`를 합쳐 URL 기준 중복 제거.

**키 이름과 구조를 그대로 지킬 것** — 렌더러가 이 구조에 의존한다.

### 4. 판정 카드 생성 & 브라우저 열기
```
python3 .claude/skills/factcheck/generate_report.py
```
스크립트가 `data.json`을 읽어 `.claude/factcheck-report.html`을 만들고 기본 브라우저로 연다.

### 5. 결과 보고
주장, 최종 판정, 신뢰도, 리포트 경로를 요약한다. 판정 근거의 핵심 1~2줄을 곁들인다.

---

## 설계 메모
- **적대적 검증**: 찬성/반대를 따로 병렬 조사해 확증편향을 줄인다. 팩트체크의 신뢰도는 반박을 얼마나 성실히 찾느냐에서 나온다.
- **공유 에이전트 재활용**: 조사 엔진은 `web-researcher` 하나. `/briefing`은 중립 다각도로, `/factcheck`는 찬반 적대로 — 같은 에이전트를 목적에 맞게 다르게 부린다.
- **데이터/표현 분리**: 판정 로직·데이터는 스킬이, 카드 디자인은 `generate_report.py`가 담당한다.

## 예외 처리
- 두 에이전트 모두 실패하면 `verdict`를 `근거 불충분`, `confidence`를 `low`로 두고 그 사실을 카드에 명시한다.
- `python3`가 없으면 리포트 경로를 알려주고 직접 열도록 안내한다.
