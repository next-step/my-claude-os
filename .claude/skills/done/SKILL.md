---
name: done
description: 미완료(draft·planned) 할일을 골라 done 상태로 완료 처리한다.
user-invocable: true
allowed-tools: Read Bash Agent AskUserQuestion
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

> **속도 + 책임 분리 포인트**
> 조회는 결정론적이므로 `notion.sh`를 Bash로 직접 호출한다(콜드 스타트 제거).
> `notion.sh read`는 단일 status 필터만 지원하므로, **인자 없이 전체를 읽고
> 오케스트레이터가 `status != done`으로 걸러낸다.** 단순 조회는 헬퍼가,
> 복합 조건 판단은 오케스트레이터가 맡는다.

1. Bash로 아래를 실행한다. (인자 없이 전체 조회)

```bash
.claude/skills/_shared/notion.sh read
```

2. 반환된 배열에서 `status`가 `done`이 **아닌** 항목만 남겨 `pending` 변수에 저장한다.
3. `pending`이 비어 있으면: "완료 처리할 항목이 없어요. 모두 끝냈거나, `/capture`로 할일을 추가해보세요." 출력 후 종료.

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

> **공용 헬퍼 재사용 포인트**
> plan이 `planned`로 바꾸듯, done은 같은 `notion.sh update` 계약을 재사용한다.
> 헬퍼를 한 줄도 고치지 않고 status 값만 바꿔 호출하는 것이 공용 설계의 이점이다.

선택된 각 항목에 대해 반복한다.

1. Bash로 아래를 실행한다.

```bash
printf '%s' '{"status":"done"}' | .claude/skills/_shared/notion.sh update {항목 id}
```

2. 출력에서 `status`가 `done`으로 바뀐 것을 확인한다.

---

### Step 4: 완료 요약

```
🎉 완료 처리했어요!

  • {title} ✅
  • {title} ✅

남은 미완료 항목: {M}개
```

남은 미완료 개수는 Step 1의 `pending`에서 이번에 처리한 항목 수를 뺀 값이다.
