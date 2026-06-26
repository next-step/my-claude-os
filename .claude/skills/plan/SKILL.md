---
name: plan
description: draft 상태 할일을 오래된 순으로 꺼내 구체화 인터뷰를 진행하고 planned로 업데이트한다.
user-invocable: true
allowed-tools: Read Agent AskUserQuestion
---

# /plan 스킬 — 할일 구체화 오케스트레이터

쌓인 draft 항목을 하나씩 꺼내 언제·왜·어떻게 할지 구체화한다.

## 사용법

```
/plan
```

---

## 실행 절차

### Step 1: draft 항목 조회

> **오케스트레이터 패턴 포인트**
> 같은 Notion Agent를 읽기/쓰기 두 번 호출한다. 각 호출은 독립된 서브 에이전트이므로
> 요청 데이터를 매번 명확히 전달해야 한다.

1. Read `.claude/skills/_shared/notion-agent.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 요청
작업 유형: read
필터: { status: "draft" }
```

3. 반환된 배열을 `drafts` 변수에 저장한다.
4. `drafts`가 비어 있으면: "구체화할 항목이 없어요. `/capture`로 할일을 추가해보세요." 출력 후 종료.

---

### Step 2: 목록 표시 및 진행 확인

drafts를 캡처일 오래된 순으로 표시한다.

```
📋 구체화 대기 중인 항목 {N}개

1. {title} ({category}) — {경과일}일 전 캡처
2. {title} ({category}) — {경과일}일 전 캡처
...
```

AskUserQuestion으로 묻는다:
> "순서대로 구체화를 시작할까요?"
> - 처음부터 시작
> - 특정 항목만 선택
> - 취소

"특정 항목만 선택" 시 AskUserQuestion으로 번호를 입력받아 해당 항목만 처리한다.

---

### Step 3: 항목별 구체화 인터뷰 (루프)

처리할 각 항목에 대해 아래를 반복한다.

#### 3-1. Interviewer Agent 호출

> **오케스트레이터 패턴 포인트**
> Interviewer는 plan 전용 서브 에이전트다. 공유 에이전트(_shared)가 아닌
> 스킬 로컬 파일(_interviewer.md)을 사용하는 패턴을 보여준다.

1. Read `.claude/skills/plan/_interviewer.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 입력 항목
title: {항목 제목}
category: {카테고리}
captured_at: {캡처 시각}
```

3. Agent 응답(JSON)에서 `due_date`, `detail`을 추출한다.

#### 3-2. Notion Agent 호출 (planned로 업데이트)

1. Read `.claude/skills/_shared/notion-agent.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 요청
작업 유형: update
데이터:
  id: {항목 id}
  status: planned
  due_date: {due_date}
  detail: {detail}
```

---

### Step 4: 완료 요약

```
✅ 구체화 완료!

처리한 항목: {N}개
  • {title} → {due_date}
  • {title} → {due_date}

남은 draft: {M}개
```
