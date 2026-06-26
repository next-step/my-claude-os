---
name: agent-log
description: |
  현재 세션의 작업 내역을 읽기 쉬운 마크다운으로 저장하는 스킬.
  JSONL 원시 로그(훅이 자동 수집)를 pid로 프롬프트와 매핑해 요약 파일을 생성한다.

  다음 표현에 반드시 사용한다:
  - "작업 로그 남겨줘", "지금까지 한 거 기록해줘", "세션 로그 만들어줘"
  - "작업 내역 저장해줘", "뭐 했는지 파일로 남겨줘"
  - "agent log", "agent-log", "작업 요약 저장"
---

# Agent Log 스킬

세션 작업 내역을 `.claude/logs/sessions/YYYY-MM-DD-HH-MM-summary.md`에 저장한다.

---

## 데이터 소스

두 JSONL 파일을 읽는다. 각 줄이 하나의 JSON 항목이다.

**프롬프트 로그** — `.claude/logs/prompts/YYYY-MM-DD.jsonl`
```
{"id":"163045","t":"16:30","p":"프롬프트 내용 (최대 200자)"}
```

**작업 로그** — `.claude/logs/sessions/YYYY-MM-DD.jsonl`
```
{"pid":"163045","t":"16:31","op":"write","path":"파일경로","size":1234}
{"pid":"163045","t":"16:31","op":"edit","path":"파일경로","from":"변경 전","to":"변경 후"}
{"pid":"163045","t":"16:31","op":"bash","desc":"설명","cmd":"명령어"}
{"pid":"163045","t":"16:31","op":"agent","desc":"설명","agent":"타입","result":"결과"}
```

`pid == id` 이면 같은 프롬프트에서 발생한 작업이다.

---

## 워크플로우

### 1단계: 로그 읽기

CWD가 어느 서브 디렉토리여도 동작하도록 git 루트를 기준으로 경로를 잡는다.

```bash
ROOT=$(git rev-parse --show-toplevel)
cat "$ROOT/.claude/logs/prompts/$(date +%Y-%m-%d).jsonl" 2>/dev/null || echo ""
cat "$ROOT/.claude/logs/sessions/$(date +%Y-%m-%d).jsonl" 2>/dev/null || echo ""
date +%Y-%m-%d-%H-%M
```

### 2단계: 요약 파일 작성

`pid`로 프롬프트와 작업을 묶어 아래 구조로 Write 도구로 저장한다.
저장 경로는 반드시 절대경로(`$ROOT/.claude/logs/sessions/YYYY-MM-DD-HH-MM-summary.md`)로 지정한다.

```markdown
# 작업 로그 — YYYY-MM-DD HH:MM

## 요약
| 항목 | 내용 |
|------|------|
| 날짜 | ... |
| 프롬프트 수 | N건 |
| 주요 변경 파일 | 파일1, 파일2, ... |

---

## [16:30] 프롬프트 내용 요약

- (write) `.claude/hooks/log-work.py` 생성 (1234자)
- (edit) `.claude/settings.json` — matcher 범위 확장
- (bash) 훅 디렉토리 파일 목록 확인

**결과:** 무엇이 달라졌는지 1~2줄 서술

---

## [16:45] 다음 프롬프트 요약

...
```

### 3단계: 저장 완료 안내

```
✅ .claude/logs/sessions/YYYY-MM-DD-HH-MM-summary.md
```

---

## 작성 원칙

- 각 프롬프트 섹션은 5줄 이내, 한국어로
- "무엇을" 보다 "왜, 결과는"에 집중
- 로그에 없는 내용은 현재 대화 컨텍스트에서 보완
