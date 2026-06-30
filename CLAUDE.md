# Claude OS — Habit Tracker

## 기본 규칙

1. 클로드 OS 관련 모든 파일(예. `.claude` 하위 md)은 반드시 프로젝트 안에 만들 것
2. 클로드 OS 만들기 실습 중이기 때문에 대화 과정에서 AI와의 협업을 배울 수 있도록 양질의 설명 제공할 것

---

## 프로젝트 구조

```
my-claude-code-os/
├── CLAUDE.md                    # 이 파일 — 전역 지침
├── .claude/
│   ├── agents/                  # 서브에이전트 (6개)
│   ├── skills/                  # 스킬 (8개)
│   ├── hooks/                   # 훅 스크립트 (3개)
│   └── settings.json            # 훅 이벤트 등록
└── habit-tracker/               # Spring Boot 앱 (Java 17 / Thymeleaf / H2)
    ├── docs/
    │   ├── planning/            # POLICY.md, PRD 문서
    │   ├── design/              # 화면 명세서, 디자인 시스템
    │   ├── api/                 # API 계약서
    │   ├── qa/                  # 테스트 케이스, 버그 리포트
    │   └── security/            # 보안 감사 리포트
    └── src/
```

---

## 에이전트 (`.claude/agents/`)

| 에이전트 | 역할 | 주요 도구 |
|----------|------|-----------|
| `planner` | 앱 정책(POLICY.md) 관리, 게이미피케이션 기획, PRD 작성 | Read, Write, Edit, Bash |
| `designer` | 화면 명세서 작성, 디자인 시스템 관리 | Read, Write, Edit, Bash |
| `backend-dev` | TDD 기반 Spring Boot 구현 (15년차 시니어) | Read, Write, Edit, Bash |
| `frontend-dev` | Thymeleaf/CSS/JS 화면 구현 (15년차 시니어) | Read, Write, Edit, Bash |
| `qa-engineer` | 코드 정적 분석, 테스트 케이스 작성, 버그 리포트 (**공유 에이전트**) | Read, Bash |
| `security-auditor` | OWASP Top 10 기반 취약점 점검, 보안 리포트 작성 | Read, Bash |

> **공유 에이전트**: `qa-engineer`는 `/plan` Step 5와 `/qa` 스킬 양쪽에서 독립적으로 호출된다.

---

## 스킬 (`.claude/skills/`)

| 스킬 | 호출 방법 | 설명 |
|------|-----------|------|
| `/plan` | `/plan [기획 요소]` 또는 인자 없이 | planner → designer → backend+frontend → qa-engineer 자동 파이프라인 |
| `/qa` | `/qa [기능명]` 또는 인자 없이 | qa-engineer 독립 실행, 테스트 케이스 + 버그 리포트 |
| `/security` | `/security [범위]` 또는 인자 없이 | security-auditor 점검 → 취약점 수정 지시까지 자동화 |
| `/refactor` | `/refactor [범위]` 또는 인자 없이 | backend-dev + frontend-dev에게 유지보수성·가독성 리팩토링 병렬 요청 |
| `/github-commit` | `커밋해줘` / `자동 커밋해줘` | diff 분석 후 커밋 메시지 작성, 일반/빠른 모드 |
| `/skill-stat` | `스킬 통계` / `스킬 사용 현황` | 프로젝트별 스킬 사용 통계 조회 |
| `/agent-log` | `작업 로그 저장해줘` | 세션 작업 내역을 마크다운으로 저장 |
| `/submit-pr` | `/submit-pr title="..." mission="..."` | next-step/my-claude-code-os minnseong 브랜치로 PR 자동 생성 |

---

## 훅 (`.claude/hooks/` + `settings.json`)

| 이벤트 | 스크립트 | 동작 |
|--------|----------|------|
| `UserPromptSubmit` | `log-prompt.py` | 사용자 프롬프트를 JSONL로 기록 |
| `PostToolUse` (Agent·Write·Edit·Bash) | `log-work.py` | 도구 사용 이력을 JSONL로 기록 |
| `Stop` | `auto-commit.sh` | 미커밋 변경사항을 안전망 커밋 |

> 전역(`~/.claude/settings.json`)에는 스킬 사용 통계 훅과 완료 알림음(`afplay Glass.aiff`)이 추가로 적용되어 있다.

---

## 앱 정책

`habit-tracker/docs/planning/POLICY.md` 가 앱의 유일한 정책 문서다.
기능 추가·변경 시 planner 에이전트가 이 파일을 먼저 읽고 정책과의 일관성을 확인한다.

---

## 개발 원칙

- **TDD 필수**: backend-dev는 테스트 먼저 작성 (Red → Green → Refactor)
- **에이전트 자체 커밋**: 각 에이전트는 작업 완료 후 직접 커밋한다 (`auto-commit.sh`는 안전망)
- **문서 우선**: PRD → 화면 명세서 → API 계약서 순으로 산출물을 만든 뒤 구현한다
- **qa-engineer는 코드를 수정하지 않는다**: 발견만 하고, 수정은 backend-dev/frontend-dev가 담당
- **security-auditor는 코드를 수정하지 않는다**: 발견만 하고, 수정은 `/security` 스킬이 개발자에게 지시
