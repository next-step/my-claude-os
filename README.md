# 나만의 클로드 코드 OS 만들기 미션
## 진행 방법
- 나만의 클로드 코드 OS 만들기 주차별 요구사항을 파악한다.
- 요구사항에 대한 구현을 완료한 후 자신의 github 아이디에 해당하는 브랜치에 Pull Request(이하 PR)를 통해 리뷰 요청을 한다.
- 리뷰 피드백에 대한 개선 작업을 하고 다시 PUSH한다.
- 모든 피드백을 완료한 후 merge가 되면 다음 단계를 도전하고 앞의 과정을 반복한다.

## 온라인 코드 리뷰 과정
* [텍스트와 이미지로 살펴보는 온라인 코드 리뷰 과정](https://github.com/next-step/nextstep-docs/tree/master/codereview)

---

# 채용 리서치 웹 서비스 (M1)

취업 준비생용 채용 공고 모아보기 + 회사 리서치 서비스. 전체 청사진은 [`OS.md`](./OS.md) 참조(특히 12장이 개발 계약).

## 기술 스택
Next.js(App Router) + TypeScript + Prisma + SQLite (M1). 상세는 OS.md 12.1.

## 실행 방법
```bash
npm install
npm run db:migrate   # Prisma 마이그레이션 + SQLite(prisma/dev.db) 생성 + seed 자동 실행
npm run db:seed      # (필요 시) mock 데이터 재적재
npm run dev          # http://localhost:3000
npm run collect      # 수집 스텁(현재 MockAdapter 시연, 실 수집은 사람인 API 승인 후)
```

## 공유 타입 (프론트 단일 import 위치)
`src/types/contract.ts` — `JobDTO`, `Job`, `Bookmark`, `BookmarkStatus`, `UserPreference`,
`ExperienceLevel`/`DataQuality`, API 요청/응답 타입, `DEV_ROLE_OPTIONS` 등. 프론트는 `@/types/contract` 만 import.

## Mock API (계약 형태 그대로, 데이터는 mock)
- `GET /api/jobs` (필터/정렬/커서, 빈 결과 `?mock=empty`)
- `GET /api/jobs/:id`
- `GET|PUT /api/me/preferences`

## 사람인 공개 API 유의 (중요)
- 실 공고 수집(SaraminAdapter)에는 **사람인 개발자센터 이용신청 → 승인**이 필요하다. 승인 전까지는 MockAdapter 로 진행.
- 쿼터: **하루 500 콜, 요청당 count ≈ 110 상한**.
- 약관: **재판매·대가 수취 금지**. M1(단일 로컬·비상업 실습)은 무방하나, 공개 서비스화 시 약관/robots 재점검 필요.

---

# 클로드 코드 OS 구성 (`.claude/`)

이 저장소는 채용 리서치 서비스 그 자체이자, **그걸 만들기 위한 나만의 클로드 코드 OS**를 함께 담고 있다.
아래는 지금까지 만든 스킬·서브에이전트·협업(공유) 계층과 각각의 구현 방식이다.

## 공유 서브에이전트 (`.claude/agents/*.md`)
역할별로 나눈 전문가 3명. 각 파일은 **frontmatter(name·description·tools·model) + 시스템 프롬프트**로 구현했고,
셋 다 "작업 시작 전 루트의 `OS.md`(서비스 단일 진실 출처)를 먼저 읽는다"는 규칙을 공유한다.

**"공유"의 뜻**: 이 3명은 특정 스킬 하나에 종속되지 않고 **여러 스킬에서 공통으로 언급·활용**된다.
아래 표의 "공유 지점"처럼 셋 다 `orchestrate`(실제 호출)와 `handoff`(위임 문서의 수신자)에서 함께 쓰이므로,
스킬 사이를 넘나드는 **공유 자원**으로 본다.

| 에이전트 | 역할 | 관점 | 공유 지점(언급 스킬) |
|---|---|---|---|
| **product-planner** | 기획·총괄(PM/테크리드). 무엇을 어떤 순서로·어떤 스택으로 만들지, 백엔드/프론트 작업 범위를 정한다 | 상위 의사결정 | orchestrate, handoff |
| **backend-developer** | 서버 운용·DB 스키마·데이터 수집(API 우선 → 크롤링 → URL 폴백) 설계 | 서비스 지향 | orchestrate, handoff |
| **frontend-developer** | 화면 구성·화면 흐름·사용 편의성(UX) 설계 | 사용자 지향 | orchestrate, handoff |

> 실제로 **호출(spawn)** 하는 스킬은 현재 `orchestrate` 하나이고, `handoff`는 이들을 위임 문서의 수신자로 **이름만 명시**한다.
> 즉 지금은 "문서 수준의 공유"이며, 두 스킬이 한 파이프라인(orchestrate 2단계에서 handoff 양식으로 위임)으로 엮여 있어 같은 3명이 공유된다.

## 스킬 (`.claude/skills/*/SKILL.md`)
각 스킬은 **frontmatter(name·description) + 마크다운 절차서**로 구현했다. description의 트리거 문구로 호출 시점을 잡는다.

| 스킬 | 하는 일 | 구현 요점 |
|---|---|---|
| **commit** | 변경 검토 → 스테이징 → 명확한 메시지로 커밋(push·PR은 안 함) | 커밋 절차를 문서화한 순수 프로시저 스킬 |
| **orchestrate** | 기획자↔개발자 서브에이전트를 정해진 순서로 호출·중개해 기능 한 단위를 완성 | 서브에이전트는 서로 못 부르므로 **메인 Claude가 유일한 오케스트레이터**가 되는 절차 |
| **handoff** | 기획→개발 위임 시 "범위·참조 계약·완료 기준·의존성"을 표준 양식으로 정리 | orchestrate 2단계(개발자 병렬 호출) 직전에 위임 품질을 일정화 |
| **contract-check** | 공유 계약(OS.md 12장 ↔ backend 타입 ↔ frontend 사용처)의 드리프트 점검 | 세 지점의 타입/스펙 불일치를 잡아내는 검증 절차 |
| **skill-stat** | 지금까지 호출된 스킬의 횟수·마지막 호출 시각 통계 표시 | 아래 훅이 쌓은 `~/.claude/skill-stats.json`을 읽어 보여줌 |

## 협업·공유 계층 (에이전트 외의 공유 자원)
공유 서브에이전트(위 3명)에 더해, 아래 두 가지가 스킬·에이전트가 함께 딛는 공유 기반이다.
참고로 서브에이전트끼리는 **직접 호출이 불가능**하므로, 이들을 엮는 오케스트레이션은 항상 메인 Claude가 맡는다.

- **공유 계약**: `OS.md` 12장 + `src/types/contract.ts`. 백엔드가 정의·export한 타입을 프론트가 import해 쓰는 단일 진실 출처. (contract-check가 이걸 지킨다)
- **훅 (`.claude/hooks/`, `.claude/settings.json`에 연결)**: 협업 흔적을 자동 기록하는 백그라운드 장치.
  - `skill-usage-log.sh` — **PreToolUse** 훅. Skill 호출 payload에서 스킬 이름을 뽑아 `~/.claude/skill-stats.json`에 횟수·시각을 원자적으로 누적(항상 `exit 0`으로 도구 실행 불방해). → skill-stat이 소비.
  - `decision-log.sh` — **PostToolUse(Edit|Write)** 훅. `OS.md`가 수정될 때마다 `DECISIONS.md`에 "언제·어떤 파일·어떤 도구로" 한 줄을 append해 청사진 변경 추적선을 남김.
