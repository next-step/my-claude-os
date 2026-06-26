# 할일 관리 OS — 사용방법

이 프로젝트는 **할일을 캡처 → 구체화 → 리마인드** 하는 개인 할일 관리 OS입니다.
키워드 하나만 던지면 AI가 분류·저장하고, 나중에 구체화 인터뷰로 계획을 잡고, 매일 저녁 미처리 항목을 알려줍니다.

## 빠른 시작

```
/capture 스프링 강의 듣기     # ① 할일 캡처 (자동 분류 → draft 저장)
/plan                         # ② 쌓인 draft를 구체화 인터뷰 → planned
/remind                       # ③ 미처리 draft 리마인더 알럿 (자동 실행 가능)
```

## 스킬 (사용자 명령)

| 명령 | 하는 일 | 입력 → 결과 |
|------|--------|------------|
| `/capture <키워드>` | 키워드를 AI로 카테고리 분류 후 `draft` 상태로 저장 | `장보기` → `일상` 카테고리 draft |
| `/plan` | 쌓인 draft를 오래된 순으로 꺼내 언제·왜·어떻게를 인터뷰하고 `planned`로 업데이트 | draft → 마감일·상세 채워진 planned |
| `/remind` | 미처리 draft를 조회해 리마인더 알럿 발송 (없으면 조용히 종료) | Cron으로 매일 저녁 자동 호출 가능 |

## 할일의 상태 흐름

```
/capture          /plan
   │                │
   ▼                ▼
 draft ─────────► planned ─────────► done
(분류만 됨)    (마감·계획 확정)     (완료)
   │
   └──► /remind 가 매일 저녁 알림
```

데이터 스키마(`category`, `status`, `due_date` 등)는 [`.claude/skills/_shared/notion-agent.md`](.claude/skills/_shared/notion-agent.md) 참고.

---

# 아키텍처 — 오케스트레이터 패턴

이 OS의 핵심은 **스킬(오케스트레이터)이 직접 일하지 않고, 서브 에이전트에게 위임**하는 구조다.

- **스킬 = 오케스트레이터**: "무엇을, 어떤 순서로 할지"만 결정한다.
- **서브 에이전트 = 일꾼**: 분류, 저장/조회, 인터뷰, 알럿 메시지 작성 등 실제 작업을 수행한다.
- 서브 에이전트는 **콜드 스타트**(이전 대화 기억 없음)이므로, 스킬은 호출할 때 ① 에이전트 프롬프트 파일을 읽고 ② 입력 데이터를 붙여 `Agent()`를 호출한다.

```
사용자
  │  /capture 장보기
  ▼
┌─────────────────────────────┐
│ capture (오케스트레이터)     │
│  1. 입력 파싱                │
│  2. Classifier Agent 호출 ───┼──► 카테고리 분류 ("일상")
│  3. Notion Agent 호출    ────┼──► tasks.json 에 draft 저장
│  4. 결과 출력                │
└─────────────────────────────┘
```

## 에이전트 종류

| 에이전트 | 위치 | 역할 | 재사용 |
|---------|------|------|--------|
| Classifier | `_shared/classifier-agent.md` | 키워드 → 카테고리 분류 | capture |
| Notion | `_shared/notion-agent.md` | tasks.json 읽기/쓰기/수정 | **capture·plan·remind 공유** |
| Interviewer | `plan/_interviewer.md` | 할일 구체화 인터뷰 | plan 전용 |
| Alert | `remind/_alert.md` | 리마인더 메시지 생성 | remind 전용 |

> **공유(`_shared`) vs 로컬 에이전트**
> `Notion Agent`처럼 여러 스킬이 공통으로 쓰는 일꾼은 `_shared/`에 두어 중복 없이 재사용하고,
> `Interviewer`·`Alert`처럼 한 스킬에서만 쓰는 일꾼은 해당 스킬 폴더 안에 둔다.

## 디렉터리 구조

```
.claude/
├── skills/
│   ├── capture/SKILL.md          # /capture 오케스트레이터
│   ├── plan/
│   │   ├── SKILL.md              # /plan 오케스트레이터
│   │   └── _interviewer.md       # 구체화 인터뷰 에이전트 (로컬)
│   ├── remind/
│   │   ├── SKILL.md              # /remind 오케스트레이터
│   │   └── _alert.md             # 알럿 메시지 에이전트 (로컬)
│   └── _shared/
│       ├── classifier-agent.md   # 카테고리 분류 에이전트 (공유)
│       └── notion-agent.md       # 할일 저장소 에이전트 (공유)
└── data/
    └── tasks.json                # 할일 데이터 (Notion API 연동 전 Mock DB)
```

## 자동 리마인더 등록

`/remind`를 매일 저녁 자동 실행하려면 스케줄을 등록한다.

```
/schedule "/remind" 매일 저녁 21:00
```

---

> **참고**: 현재 할일 데이터는 `.claude/data/tasks.json`을 로컬 Mock DB로 사용한다.
> Notion API 연동은 추후 `notion-agent.md`만 교체하면 스킬 수정 없이 전환 가능하도록 설계되어 있다.
