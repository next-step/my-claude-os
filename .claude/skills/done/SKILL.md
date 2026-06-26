---
name: done
description: 미완료(draft·planned) 할일을 골라 done 상태로 완료 처리한다.
user-invocable: true
allowed-tools: Read Agent AskUserQuestion
---

# /done 스킬 — 할일 완료 처리 오케스트레이터

쌓인 할일 중 끝낸 것을 골라 `done` 상태로 바꾼다.
capture(생성) → plan(구체화) → **done(완료)** 로 이어지는 상태 흐름의 종착점이다.

## 사용법

```
/done            ← 완료할 항목을 목록에서 선택
/done 장보기      ← 제목 키워드로 바로 지정 (부분 일치)
```

---

## 실행 절차

### Step 1: 미완료 항목 조회

> **오케스트레이터 패턴 포인트**
> notion-agent의 read는 단일 status 필터만 지원한다.
> "아직 done이 아닌 것 전체"가 필요하므로, **필터 없이 전체를 읽고
> 오케스트레이터가 `status != done`으로 걸러낸다.** 책임 분리의 실제 사례:
> 에이전트는 단순 조회만, 복합 조건 판단은 오케스트레이터가.

1. Read `.claude/skills/_shared/notion-agent.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 요청
작업 유형: read
필터: 없음 (전체 조회)
```

3. 반환된 배열에서 `status`가 `done`이 **아닌** 항목만 남겨 `pending` 변수에 저장한다.
4. `pending`이 비어 있으면: "완료 처리할 항목이 없어요. 모두 끝냈거나, `/capture`로 할일을 추가해보세요." 출력 후 종료.

---

### Step 2: 완료 대상 선택

#### 2-A. 인자로 키워드가 들어온 경우 (`/done 장보기`)

`pending`에서 title에 키워드가 부분 일치하는 항목을 찾는다.

- 1개 일치 → 그 항목을 대상으로 확정하고 Step 3으로.
- 여러 개 일치 → 아래 2-B의 목록으로 후보만 좁혀 보여주고 선택받는다.
- 0개 일치 → "'{키워드}'와 일치하는 미완료 항목이 없어요." 출력 후 종료.

#### 2-B. 인자가 없는 경우 (`/done`)

`pending`을 캡처일 오래된 순으로 표시한다. (상태도 함께 보여 맥락 제공)

```
✅ 완료 처리할 항목을 골라주세요 ({N}개)

1. {title} ({category}, {status}) — {경과일}일 전 캡처
2. {title} ({category}, {status}) — {경과일}일 전 캡처
...
```

AskUserQuestion으로 어떤 항목을 완료할지 선택받는다. (복수 선택 허용)

---

### Step 3: done으로 업데이트 (루프)

> **오케스트레이터 패턴 포인트**
> plan이 `planned`로 바꾸듯, done은 같은 update 계약을 재사용한다.
> notion-agent를 한 줄도 고치지 않고 status 값만 바꿔 호출하는 것이
> 공유 에이전트 설계의 이점이다.

선택된 각 항목에 대해 반복한다.

1. Read `.claude/skills/_shared/notion-agent.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 요청
작업 유형: update
데이터:
  id: {항목 id}
  status: done
```

3. 응답에서 업데이트 성공을 확인한다.

---

### Step 4: 완료 요약

```
🎉 완료 처리했어요!

  • {title} ✅
  • {title} ✅

남은 미완료 항목: {M}개
```

남은 미완료 개수는 Step 1의 `pending`에서 이번에 처리한 항목 수를 뺀 값이다.
