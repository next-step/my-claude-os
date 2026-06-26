---
name: remind
description: draft 항목을 조회해 리마인더 알럿을 발송한다. CronCreate로 매일 저녁 자동 호출된다.
user-invocable: true
allowed-tools: Read Agent
---

# /remind 스킬 — 리마인더 오케스트레이터

매일 저녁 Cron이 호출하는 알럿 스킬. draft 항목이 있을 때만 사용자에게 알린다.

## 사용법

```
/remind          ← 수동 실행 (테스트용)
```

자동 실행은 CronCreate로 등록된 스케줄이 매일 저녁 호출한다.

---

## 실행 절차

### Step 1: draft 항목 조회

> **오케스트레이터 패턴 포인트**
> capture·plan·remind 세 스킬 모두 같은 Notion Agent 파일을 참조한다.
> 이것이 방식 B(공유 에이전트)의 핵심: 중복 없이 하나의 파일을 세 곳에서 재사용한다.

1. Read `.claude/skills/_shared/notion-agent.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 요청
작업 유형: read
필터: { status: "draft" }
```

3. 반환된 배열을 `drafts` 변수에 저장한다.
4. `drafts`가 비어 있으면: 아무것도 출력하지 않고 조용히 종료. (알럿 없음)

---

### Step 2: Alert Agent 호출 (알럿 메시지 생성)

> **오케스트레이터 패턴 포인트**
> 오케스트레이터는 "무엇을 할지"만 결정한다.
> "어떻게 메시지를 포맷할지"는 Alert Agent에게 위임한다.

1. Read `.claude/skills/remind/_alert.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 입력 데이터
오늘 날짜: {오늘 날짜, YYYY-MM-DD}
draft 항목 목록:
{drafts 배열을 JSON으로 붙여넣기}
```

3. Agent가 반환한 알럿 메시지를 `alertMessage` 변수에 저장한다.

---

### Step 3: Telegram Agent 호출 (알럿 발송)

> **오케스트레이터 패턴 포인트**
> 메시지를 "만드는" 책임(Alert Agent)과 "보내는" 책임(Telegram Agent)을 분리한다.
> 채널을 슬랙·이메일로 바꿔도 remind 본체는 그대로 두고 발송 에이전트만 교체하면 된다.
> 이 단계 덕분에 클라우드 routine에서 실행돼도 알럿이 사용자 텔레그램으로 도착한다.

1. Read `.claude/skills/_shared/telegram-agent.md` 를 읽는다.
2. 아래 데이터를 붙여 Agent 도구를 호출한다.

```
---
## 요청
메시지:
{alertMessage}
```

3. 발송 결과를 확인한다. 동시에 `alertMessage`를 터미널에도 그대로 출력한다.
   (수동 실행 `/remind` 시 터미널에서도 바로 볼 수 있도록)

---

## Cron 등록 방법

매일 저녁 알럿 시간을 결정한 후 아래 명령으로 등록한다.

```
/schedule "/remind" 매일 17:00
```

> 알럿 수신 채널(터미널 알림 / 이메일 등)은 OS.md 미결 사항 참고.
