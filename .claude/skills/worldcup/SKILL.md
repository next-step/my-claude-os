---
name: worldcup
description: 2026 FIFA 월드컵의 전체 현황과 주요 뉴스를 모던한 HTML 리포트로 만들어 브라우저로 띄운다. 경기정보 수집 에이전트와 뉴스 조사 에이전트를 병렬 호출해 데이터를 모은다. 사용자가 "월드컵 리포트", "월드컵 현황 보여줘", "월드컵 보고서"라고 요청할 때 사용한다.
user-invocable: true
allowed-tools:
  - Bash(python3:*)
  - Read
  - Write
---

# /worldcup — 월드컵 현황 리포트 스킬

진행 중인 2026 FIFA 월드컵의 **전체 현황 + 주요 뉴스**를 수집해
월드컵 공식 홈페이지 감성의 모던 HTML 리포트로 만들어 브라우저로 띄운다.

이 스킬의 핵심은 **두 전문 에이전트를 병렬로 호출**해 역할을 나눠 수집하는 것이다.

```
            ┌─────────────────────────┐
  /worldcup │  ① 두 에이전트 병렬 호출  │
            └─────────────────────────┘
               │ (한 메시지에서 동시에)
        ┌──────┴───────┐
        ▼              ▼
 worldcup-fixtures   worldcup-news
 (경기·순위·진출)     (뉴스·골든부트)
        │              │
        └──────┬───────┘
               ▼
   ② 결과 JSON 병합 → data.json
               ▼
   ③ generate_report.py 실행 → HTML 생성 + 브라우저 열기
```

---

## 진행 순서

### 1. 두 에이전트를 병렬로 호출
**반드시 한 번의 응답 안에서 두 개의 Agent 호출을 동시에** 실행해 병렬로 돌린다.

- `worldcup-fixtures` 에이전트 — 경기 결과·조별 순위·32강 진출 현황·A조(한국) 스포트라이트 수집.
- `worldcup-news` 에이전트 — 오늘의 주요 헤드라인·골든부트 레이스 수집.

각 에이전트는 정해진 스키마의 JSON을 반환한다(에이전트 정의 파일 참고).
한쪽 에이전트가 실패하거나 빈 결과를 주면, 기존 `data.json`의 해당 부분을 유지한다.

### 2. 결과를 data.json으로 병합
두 에이전트의 JSON 결과를 합쳐 `.claude/skills/worldcup/data.json`을 갱신한다(Write).

- `updated_at` — 오늘 날짜(YYYY-MM-DD).
- `tournament` — 고정 메타(name/edition/hosts/dates)는 유지하고, fixtures의 `phase`·`next_phase`로 갱신.
- `qualified`, `spotlight` ← worldcup-fixtures 결과.
- `headlines`, `golden_boot` ← worldcup-news 결과.
- `stats` — 4개 카드를 조립한다: 참가국 48 / 개최국 3 / 32강 확정(fixtures의 `qualified_count`) / 골든부트 선두(news의 `lead_scorer_label`).
- `sources` — 두 에이전트의 `sources`를 합쳐 중복 제거.

스키마는 기존 `data.json`을 템플릿으로 삼는다. **키 이름과 구조를 그대로 지킬 것** —
렌더러(`generate_report.py`)가 이 구조에 의존한다.

### 3. 리포트 생성 & 브라우저 열기
```
python3 .claude/skills/worldcup/generate_report.py
```
스크립트가 `data.json`을 읽어 `.claude/worldcup-report.html`을 만들고 기본 브라우저로 연다.

### 4. 결과 보고
스크립트가 출력한 요약(업데이트 기준일, 주요 소식 수, 32강 진출팀 수, 리포트 경로, 브라우저 열기 여부)을
사용자에게 정리해 전달한다. 특히 한국 등 주목할 만한 소식 1~2줄을 곁들인다.

---

## 설계 메모 (왜 이렇게 나눴나)
- **데이터와 표현의 분리**: 에이전트는 `data.json`만 갱신하고, 디자인은 `generate_report.py`만 바꾼다.
  뉴스가 매일 바뀌어도 코드는 그대로다.
- **병렬 전문화**: 경기 데이터와 뉴스는 출처·조사 방식이 달라, 따로 가진 에이전트가 동시에 일하면
  더 빠르고 각자 정확하다.
- **빠른 재실행**: 에이전트 호출 없이 디자인만 바꾸고 싶으면 3번 스크립트만 다시 돌리면 된다.

## 예외 처리
- 두 에이전트가 모두 실패하면, 마지막으로 저장된 `data.json`으로 리포트를 만들고 "데이터 갱신 실패, 이전 데이터 사용" 사실을 알린다.
- `python3`가 없으면 사용자에게 알리고 리포트 경로를 직접 열도록 안내한다.
