---
name: capture
description: 할일 키워드를 입력받아 AI로 카테고리를 분류하고 draft로 저장한다.
user-invocable: true
allowed-tools: Read Agent
---

# /capture 스킬 — 할일 캡처 오케스트레이터

키워드 하나만 던지면 분류부터 저장까지 자동으로 처리한다.

## 사용법

```
/capture 장보기
/capture 스프링 강의 듣기
```

---

## 실행 절차

### Step 1: 입력 파싱

스킬 인자에서 키워드를 추출한다.

- 키워드가 없으면: "캡처할 내용을 입력해주세요. 예: `/capture 장보기`" 출력 후 종료.

---

### Step 2: Classifier Agent 호출 (카테고리 분류)

> **오케스트레이터 패턴 포인트**
> 서브 에이전트를 호출할 때는 ① 프롬프트 파일을 읽고, ② 그 내용에 입력 데이터를 붙여 Agent()를 호출한다.
> 서브 에이전트는 콜드 스타트이므로 필요한 정보를 프롬프트에 모두 담아야 한다.

1. Read `.claude/skills/_shared/classifier-agent.md` 를 읽는다.
2. 읽은 내용 뒤에 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 입력
키워드: {Step 1에서 추출한 키워드}
```

3. Agent 응답(카테고리 이름)을 `category` 변수에 저장한다.

---

### Step 3: Notion Agent 호출 (draft 저장)

1. Read `.claude/skills/_shared/notion-agent.md` 를 읽는다.
2. 읽은 내용 뒤에 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 요청
작업 유형: write
데이터:
  title: {키워드}
  category: {category}
  status: draft
  captured_at: {현재 시각, ISO 8601}
  due_date: null
  detail: null
```

3. Agent 응답에서 저장된 항목의 `id`를 확인한다.

---

### Step 4: 결과 출력

```
✅ 캡처 완료!

📝 {키워드}
🏷️  카테고리: {category}
📌 상태: draft (구체화 필요)

/plan 을 실행하면 언제·어떻게 할지 구체화할 수 있어요.
```
