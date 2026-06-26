# 할일 관리 OS

## 개발 배경
개발자는 할 일을 아이폰 메모장에 1차로 정리한 후 옵시디언에서 분류, 다시 일정을 개별적으로 작성하고 있습니다.   
할 일을 완벽하게 작성해야한다는 생각때문에 작성하다가 지치거나, 혹은 나중에 작성해야지 라는 생각으로 까먹을때가 많았습니다.   
작성한다고해도 언제 할지 리마인드 하는것을 잊어 놓치는 일도 있었습니다. (예를 들어 집에 갈때 저녁 반찬용으로 깻잎 사가기 등)  
어떻게 하면 머릿속에 떠오르는 할일들을 빠르게 백로그로 저장하고, 실행력을 높일까 고민하다가 할일 관리 OS를 개발하였습니다.   
목표는 최소한의 노력으로 머릿속의 할일을 모두 기록하고, 시간이 날때 구체화함으로써 현재하는 일의 흐름이 끊기지 않도록 하는것입니다. 

## 사용방법
이 프로젝트는 **할일을 캡처 → 구체화 → 리마인드 → 완료** 하는 개인 할일 관리 OS입니다.
키워드 하나만 던지면 AI가 분류·저장하고, 나중에 구체화 인터뷰로 계획을 잡고, 매일 저녁 미처리 항목을 알려주고, 끝낸 일은 완료 처리합니다.
데이터는 **Notion DB**에 저장되어 iPad·iPhone·MacBook 어디서나 같은 할일에 접근할 수 있습니다.

## 빠른 시작

```
/capture 스프링 강의 듣기     # ① 할일 캡처 (자동 분류 → draft 저장)
/plan                         # ② 쌓인 draft를 구체화 인터뷰 → planned
/remind                       # ③ 미처리 draft 리마인더 알럿 (텔레그램 발송, 자동 실행 가능)
/done 스프링 강의 듣기         # ④ 끝낸 일을 완료 처리 → done
```

## 스킬 (사용자 명령)

| 명령 | 하는 일 | 입력 → 결과 |
|------|--------|------------|
| `/capture <키워드>` | 키워드를 AI로 카테고리 분류 후 `draft` 상태로 저장 | `장보기` → `일상` 카테고리 draft |
| `/plan` | 쌓인 draft를 오래된 순으로 꺼내 언제·왜·어떻게를 인터뷰하고 `planned`로 업데이트 | draft → 마감일·상세 채워진 planned |
| `/done [키워드]` | 미완료(draft·planned) 항목을 골라 `done`으로 완료 처리 | 키워드 부분 일치 또는 목록 선택(복수) |
| `/remind` | 미처리 draft를 조회해 리마인더 알럿을 **텔레그램으로 발송** (없으면 조용히 종료) | Cron으로 매일 저녁 자동 호출 가능 |
| `/remind-when` | remind 자동 알럿이 **몇 시에 실행되는지** crontab에서 조회 | 예: `매일 오후 5시 (17:00)` |
| `/sync-readme` | 실제 `.claude/` 상태(스킬·에이전트·디렉터리)를 스캔해 **이 README를 최신화** | 스킬 추가/삭제 후 문서 자동 동기화 |

> 💡 **자동 캡처 힌트**: `/capture`를 직접 치지 않아도, 프롬프트에 할일 뉘앙스("~해야지", "~사야" 등)가 감지되면 `/capture` 실행을 제안받습니다. (자동 저장이 아니라 제안만 — 결정은 사용자 몫)

## 할일의 상태 흐름

```
/capture          /plan              /done
   │                │                  │
   ▼                ▼                  ▼
 draft ─────────► planned ─────────► done
(분류만 됨)    (마감·계획 확정)      (완료)
   │
   └──► /remind 가 매일 저녁 미처리 항목 알림 (2일 이상 방치 항목은 ⚠️ 강조)
```

데이터 스키마(`category`, `status`, `due_date` 등)는 [`.claude/skills/_shared/notion-agent.md`](.claude/skills/_shared/notion-agent.md) 참고.

---

# 아키텍처 — 오케스트레이터 패턴

이 OS의 핵심은 **스킬(오케스트레이터)이 직접 일하지 않고, 서브 에이전트에게 위임**하는 구조다.

- **스킬 = 오케스트레이터**: "무엇을, 어떤 순서로 할지"만 결정한다.
- **서브 에이전트 = 일꾼**: 분류, 저장/조회, 인터뷰, 알럿 작성, 발송 등 실제 작업을 수행한다.
- 서브 에이전트는 **콜드 스타트**(이전 대화 기억 없음)이므로, 스킬은 호출할 때 ① 에이전트 프롬프트 파일을 읽고 ② 입력 데이터를 붙여 `Agent()`를 호출한다.

```
사용자
  │  /capture 장보기
  ▼
┌─────────────────────────────┐
│ capture (오케스트레이터)     │
│  1. 입력 파싱                │
│  2. Classifier Agent 호출 ───┼──► 카테고리 분류 ("일상")
│  3. Notion Agent 호출    ────┼──► Notion DB 에 draft 저장
│  4. 결과 출력                │
└─────────────────────────────┘
```

## 에이전트 종류

| 에이전트 | 위치 | 역할 | 재사용 |
|---------|------|------|--------|
| Classifier | `_shared/classifier-agent.md` | 키워드 → 카테고리 분류 | capture |
| Notion | `_shared/notion-agent.md` | Notion DB 읽기/쓰기/수정 | **capture·plan·done·remind 공유** |
| Telegram | `_shared/telegram-agent.md` | 알럿 메시지를 텔레그램으로 발송 | remind (발송 채널 교체 지점) |
| Interviewer | `plan/_interviewer.md` | 할일 구체화 인터뷰 | plan 전용 |
| Alert | `remind/_alert.md` | 리마인더 메시지 생성 (2일 이상 방치 항목 강조) | remind 전용 |

> **공유(`_shared`) vs 로컬 에이전트**
> `Notion`·`Telegram`처럼 여러 곳에서 공통으로 쓰거나 교체 지점이 되는 일꾼은 `_shared/`에 두어 중복 없이 재사용하고,
> `Interviewer`·`Alert`처럼 한 스킬에서만 쓰는 일꾼은 해당 스킬 폴더 안에 둔다.
> 덕분에 알림 채널을 슬랙·이메일로 바꿔도 `telegram-agent.md`만 교체하면 되고, 저장소를 바꿔도 `notion-agent.md`만 교체하면 된다.

## 훅(Hook) 종류

훅은 **사용자가 스킬을 직접 치지 않아도 동작을 자동으로 트리거**하는 장치다. 에이전트가 "위임받아 일하는 일꾼"이라면, 훅은 "알아서 일을 거는 방아쇠"다.

| 훅 | 위치 | 트리거 | 하는 일 |
|----|------|--------|---------|
| detect-todo | `hooks/detect-todo.js` | Claude Code `UserPromptSubmit` 이벤트 (세션 내부) | 프롬프트에 할일 뉘앙스("~사야", "~해야지") 감지 시 `/capture` 제안 힌트 주입 (자동 저장 X, 제안만) |
| remind-cron | `hooks/remind-cron.sh` | crontab 매일 17:00 (세션 외부) | `claude -p "/remind"` 실행 → 미처리 draft를 텔레그램으로 알럿 |
| telegram-listener | `hooks/telegram-listener.sh` | crontab 1분 폴링 (세션 외부) | 폰에서 봇에 보낸 `/capture` 등 슬래시 명령을 받아 `claude -p`로 실행 → 결과를 폰으로 회신 |

> **세션 내부 훅 vs 외부 스케줄 훅**
> `detect-todo`는 Claude Code의 **네이티브 훅**이다. 대화 세션 안에서 사용자가 입력하는 순간 끼어들어 힌트를 주입한다 — 내가 무언가 칠 때만 동작한다.
> `remind-cron`·`telegram-listener`는 **crontab이 세션 밖에서** 주기적으로 `claude -p`(헤드리스)를 띄우는 방식이다. 터미널을 보고 있지 않아도 — 정해진 시각이든, 폰에서 메시지가 오든 — 자동으로 돈다. (단, 실행 주체가 로컬 머신이라 맥이 켜져 있어야 한다.)
> 한 줄 요약: **detect-todo는 "들어올 때 돕고", cron 훅들은 "내가 없을 때 일한다".**

> 🔒 **인바운드 보안**: `telegram-listener`는 사실상 텔레그램 메시지로 로컬 `claude`를 실행시키는 통로다. 그래서 ① 내 `chat_id`가 보낸 메시지만 처리(화이트리스트), ② 미리 등록한 슬래시 명령(`/capture`)만 실행, ③ 사용자 텍스트를 셸로 재평가하지 않음(eval 미사용) — 3종 안전장치를 둔다.

## 디렉터리 구조

```
.claude/
├── OS.md                          # 시스템 설계 문서 (원칙·흐름·스키마)
├── skills/
│   ├── capture/SKILL.md           # /capture 오케스트레이터
│   ├── plan/
│   │   ├── SKILL.md               # /plan 오케스트레이터
│   │   └── _interviewer.md        # 구체화 인터뷰 에이전트 (로컬)
│   ├── done/SKILL.md              # /done 오케스트레이터
│   ├── remind-when/SKILL.md       # /remind-when (crontab 시각 조회)
│   ├── sync-readme/SKILL.md       # /sync-readme (실제 상태 스캔 → README 갱신)
│   ├── remind/
│   │   ├── SKILL.md               # /remind 오케스트레이터
│   │   └── _alert.md              # 알럿 메시지 에이전트 (로컬)
│   └── _shared/
│       ├── classifier-agent.md    # 카테고리 분류 에이전트 (공유)
│       ├── notion-agent.md        # 할일 저장소 에이전트 (공유, Notion API)
│       └── telegram-agent.md      # 알럿 발송 에이전트 (공유)
├── hooks/
│   ├── detect-todo.js             # UserPromptSubmit 훅: 자연어 할일 감지 → /capture 제안 힌트
│   ├── remind-cron.sh             # crontab(매일 17:00)이 호출하는 /remind 실행 스크립트
│   └── telegram-listener.sh       # crontab(1분 폴링) 인바운드 브리지: 폰의 슬래시 명령 → claude -p 실행
└── data/
    ├── notion.json                # Notion 토큰·DB ID (비밀값, git 제외)
    ├── telegram.json              # 텔레그램 봇 토큰·chat_id (비밀값, git 제외)
    └── telegram-offset.txt        # 텔레그램 폴링 오프셋 (런타임 상태, git 제외)
```

## 저장소: Notion 연동

할일 데이터는 **Notion DB "할일 (Claude OS)"** 에 저장된다. 크로스 디바이스(iPad·iPhone·MacBook) 접근이 목적이다.

- 자격증명은 `.claude/data/notion.json`(토큰·DB ID)에서 읽으며, 이 파일은 `.gitignore`로 커밋에서 제외된다.
- 스킬·오케스트레이터는 저장소 종류를 모른다. 저장 방식이 바뀌어도 `notion-agent.md` 내부만 교체하면 되도록 설계되어 있다. (실제로 로컬 JSON Mock → Notion API 전환을 스킬 수정 없이 완료)

## 자동 리마인더 등록

`/remind`를 매일 저녁 자동 실행하려면 스케줄을 등록한다.

```
/schedule "/remind" 매일 17:00
```

알럿은 텔레그램으로 발송되며, 2일 이상 방치된 항목은 `⚠️` 강조로 별도 표시된다.
