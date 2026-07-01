# 주식 OS (Stock OS)

> "주린이"인 사용자가 Claude Code를 활용해 **주식을 배우고**, **시장 동향을 매일 받아보고**, **사람 승인 하에** 자신의 보유 종목을 관리·판단하는 개인 OS.

## 1. 목표 (Goals)

### 진짜 목표 (Primary)
- 한 달 뒤, 사용자가 **주식 기본 개념을 이해하고 스스로 매매 판단의 근거를 읽을 수 있게** 된다.
- 그 과정에서 Claude Code의 핵심 메커니즘(scheduled agents / skills / memory / MCP / hooks / workflow)을 직접 만들어 익힌다. (이번 미션의 상위 목표)

## 2. 핵심 원칙 (Principles)
1. **사람이 방아쇠를 당긴다 (Human-in-the-loop).** Claude는 *추천과 근거*까지만. 실제 매수/매도 주문은 항상 사용자가 승인/실행.
2. **내 실제 보유가 기준.** `portfolio/` 원장을 보유의 **단일 출처(single source of truth)**로 삼고, 모든 판단은 실제 보유를 토대로 한다. (현재는 사용자가 직접 기록 — 향후 계좌 정보 자동 조회로 확장 가능.)
3. **근거 없는 추천 금지.** 모든 매매 아이디어는 데이터·이유·리스크를 함께 제시.
4. **계좌 정보는 절대 평문/깃 저장 금지.** 키는 환경변수/시크릿으로만.
5. **교육 우선.** 매일 "왜 그런지"를 설명한다. 종목 찍어주기보다 읽는 법을 가르친다.

## 3. 설계 구상 (Design)

### 3.1 매매 권한 — Claude는 자동매매를 하지 않는다
- Claude는 항상 켜진 프로세스가 아니다(호출/스케줄 시에만 동작).
- **시세 조회는 `tossctl`(토스증권 CLI)로 연동돼 있으나 READ(조회) 전용이다.** 주문(`order`) 계열 명령은 `.claude/hooks/block-auto-trade.py` 훅이 하네스 차원에서 차단한다(§3.5).
- 실제 주문은 사용자가 증권사 앱/CLI에서 직접 한다.
- **흐름은 항상: Claude 제안 → 사용자 승인 → 사용자 실행.**

### 3.2 계좌
- 사용자가 자신의 보유를 `portfolio/` 원장에 기록하고, Claude는 그 기록만 사용한다.
- (증권사 API를 연동할 경우) 자격증명은 `.env`/시크릿으로 관리, `.gitignore` 처리.

### 3.3 매일 브리핑 (Daily Briefing)
- 매 거래일 아침 브리핑을 만든다(파일 기록 순서 = 발송 순서). *현재는 사용자가 `/daily-brief`로 수동 호출하며, 정시 자동 스케줄 등록은 보류 상태(§5).*
  1. 시장 동향 요약(지수, 환율, 주요 이슈)
  2. 관심/보유 종목 — 내 보유 변화·이유 + **유의해서 볼 종목**(미보유, 매수 추천 아님 — "왜 봐야 하는지")
  3. 오늘의 주식 기본 지식 1개 (예: PER, 분산투자, 손절 등)
- 결과를 정해진 채널(파일 리포트 + 모바일 알림 — **주제별 3개 메시지**)로 전달.

### 3.4 디렉토리 구조
```
/                 (repo root)
├─ OS.md          ← 이 문서 (목표/설계의 단일 출처)
├─ MISSION.md     ← 합격 기준(필수/도전)·인벤토리·진행 로그
├─ CLAUDE.md      ← OS.md를 참조하라고 지시
├─ docs/          ← 사람이 보는 구성 문서(week1.html 등)
├─ scripts/       ← notify-telegram.sh · md-to-telegram.sh · tossctl-commands.md(명령 참고)
├─ stock/         ← 주식 도메인 (향후 english/ 등 다른 도메인과 분리)
│  ├─ briefings/  ← 날짜별 데일리 브리핑 기록
│  └─ lessons/    ← curriculum.md (학습 순서 + 진도 체크박스). 용어 설명 본문은 그날 briefings/에 남음
├─ portfolio/     ← 보유 원장(holdings.md) — 단일 출처(개인 금융정보, git 제외)
└─ .claude/
   ├─ skills/     ← /daily-brief · /stock-check · /stock-explain · /portfolio-review · /git-commit
   ├─ agents/     ← 서브에이전트 5(공유 portfolio-reader + daily-brief 전용 4)
   └─ hooks/      ← block-auto-trade.py (자동매매 차단 PreToolUse 훅)
```

### 3.5 안전장치 (훅)
- **`block-auto-trade.py` (`PreToolUse`/Bash 훅):** 모든 Bash 명령을 검사해 tossctl 실주문(`order place/cancel/amend`, `--execute`)을 **거부**한다. 원칙 1(Human-in-the-loop)을 모델의 판단에만 맡기지 않고 **하네스가 기계적으로 강제**하는 최종 방어선. 조회·dry-run(`order preview`)은 허용한다.

## 4. 결정 사항 (Decisions)
- **시세·시장 데이터:** `tossctl`(토스증권 CLI)을 **시세 READ 전용**으로 사용 — 지수·환율·개별 시세·수급 등 숫자. **명령 문법·목록은 `scripts/tossctl-commands.md` 참고 문서 한 곳에서 관리**(명령 지식의 단일 출처, 원본 레퍼런스: https://tossinvest-cli.vercel.app/docs/reference/commands). tossctl 실패/세션만료 시 웹 1차 출처(네이버 금융 / KRX)로 대체. **시세만 쓰고 계좌·보유(`portfolio`·`account`)·주문(`order`)은 조회하지 않는다**(내 토스 계좌는 비어 있음 — 보유는 아래 원장이 단일 출처; 주문은 `block-auto-trade.py` 훅이 차단).
- **뉴스·공시 데이터:** 제도권 매체(연합·한경·매경 등)·DART를 웹에서 확인(블로그·SNS·커뮤니티 배제).
- **출처 노출 정책:** 출처는 **내부 검증(fact-check)용으로만** 확인하고, **사용자에게 보이는 출력(브리핑 본문·스킬 응답)엔 출처(매체명·URL·tossctl)를 적지 않는다.** 대신 초보자가 이해하기 쉽게 내용을 풀어 쓰고, 검증 못 한 값은 `확인필요`로 표시.
- **보유 기록:** 사용자가 `portfolio/holdings.md`에 직접 기록(단일 출처).
- **관심종목(watchlist):** 별도 고정 목록을 두지 않는다. "관심 종목" = ① 내 보유 종목(원장) + ② 매일 `stock-watcher`가 뽑는 **유의 종목**(선정 기준: 내 보유 연관·오늘 시장 주도주·학습 가치 있는 사례). **필터: 국내(한국) 반도체는 제외**(내 보유(삼성전자우)가 이미 국내 반도체라 중복 관찰 방지), **최대한 서로 다른 분야에서 하나씩(섹터 분산)**. **유의 종목은 매수 추천이 아니라 "왜 봐야 하는지"를 가르치는 관찰 목록**(OS 원칙 5·종목 찍어주기 금지 준수).
- **브리핑 채널:** **파일** `stock/briefings/YYYY-MM-DD.md` 저장 + **모바일 푸시는 Telegram 봇**(주제별 **3개 메시지**: 시장 동향 → 관심/보유 → 오늘의 용어). 전송은 `scripts/notify-telegram.sh`(토큰은 `.env`), 마크다운→HTML 변환은 `scripts/md-to-telegram.sh`.

## 5. 열린 질문 (Open Questions)
- 정시 자동 브리핑 스케줄 등록 시점.

## 6. 스킬 설계 (Skill Designs)

> 각 스킬은 **오케스트레이터(메인 에이전트=팀장)** 이고, 내부에서 일을 쪼개 **서브에이전트(팀원)** 로 돌린다.
> 서브에이전트는 각자 독립 컨텍스트에서 한 가지 일만 하고 **결과만** 반환한다.

### 스킬 1) `/daily-brief` — 데일리 브리핑 (정시 모바일 알림)

**목적:** 매 거래일 아침, 주린이가 알아야 할 (1) 시장 동향(지수·환율·이슈) + (2) 관심/보유 종목 변화·이유 + **유의해서 볼 종목(미보유·매수 추천 아님)** + (3) 주식 기본 지식 1개를 쉽게 정리해, 정해진 시각에 모바일로 **주제별 3개 메시지**로 받는다.
**핵심 제약:** **가짜 정보 금지.** 모든 뉴스는 `fact-checker` 검증을 통과한 것만 싣는다. 단, **출처(매체명·URL)는 내부 검증용으로만 쓰고 브리핑 본문엔 표기하지 않으며**, 사용자가 초보자임을 감안해 **이해하기 쉽게 풀어 쓴다.** 검증 못 한 수치는 "확인필요"로 표시.
**데이터 출처:** 시세/공시는 KRX·DART·네이버금융 등 1차 출처 우선, 뉴스는 제도권 매체 화이트리스트만(블로그·SNS·커뮤니티 배제). 용어는 사용자가 알려줄 필요 없이 `stock/lessons/curriculum.md`(초보→중급 학습 순서)를 따라 `stock-term-teacher`가 매일 다음 순번을 가르친다.

**서브에이전트 분할:** (모두 `.claude/agents/`에 **독립 파일**로 분리 — daily-brief가 `subagent_type`으로 호출)
| 서브에이전트 | 하는 일 | 도구 |
|---|---|---|
| `stock-news-scraper` | 오늘 시장 동향(지수·환율은 tossctl) + 움직인 실제 뉴스 수집(한국+미국) | Bash(tossctl), WebSearch, WebFetch |
| `stock-term-teacher` | `stock/lessons/curriculum.md` 체크박스 확인해 안 배운(`- [ ]`) 용어 1개를 골라 쉽게 설명 | Read |
| `stock-watcher` | (a) 내 보유 종목의 오늘 변화·이유 + (b) 미보유 **유의 종목** 2~3개(보유 연관·시장 주도주·학습 사례 기준, 매수 추천 아님)를 실제 근거로 정리 | Read, Bash(tossctl), WebSearch, WebFetch |
| `fact-checker` | 수집된 뉴스/이유를 **반증 시도**로 검증 — 숫자는 tossctl로 교차확인, 거르기 | Bash(tossctl), WebSearch, WebFetch |

**오케스트레이션 흐름:**
1. (병렬) `stock-news-scraper` + `stock-term-teacher` + `stock-watcher` 동시 실행.
2. `stock-news-scraper`/`stock-watcher`의 뉴스 근거 → `fact-checker`로 검증·필터.
3. 팀장이 결과를 **모바일용 짧은 브리핑**으로 합쳐 `stock/briefings/YYYY-MM-DD.md` 저장 + **브리핑 전문을 Telegram으로 발송**(요약본 아님 — 파일을 안 열어도 메시지로 끝). 같은 날 파일이 이미 있으면 **재생성하지 않고 기존 파일로 발송**.
4. 용어 설명은 **브리핑 본문에만** 남기고, `stock/lessons/curriculum.md`의 해당 줄을 `- [x]`로 체크(진도의 단일 출처). 별도 `lessons/NNN` 본문 파일은 만들지 않는다.

**쓰는 Claude Code 기능:** Subagent 병렬 호출, 검증 서브에이전트(반증), Scheduled agent(정시 실행), Push 알림, Memory(`curriculum.md` 체크박스로 중복 학습 방지).
**구현 상태:** `.claude/skills/daily-brief/SKILL.md` 완성. 서브에이전트는 `.claude/agents/`에 **파일로 분리**(stock-news-scraper·stock-term-teacher·stock-watcher·fact-checker) — 오케스트레이터는 호출·조립만 담당.

### 스킬 2) `/stock-check` — 종목 현황 조회 (추천 없음)

**목적:** 한 종목에 대해 "내가 지금 어떤 상태로 들고 있나"(보유 여부·평단·수량·현재가·수익률·평가손익)를 조회해 보여준다. **추천은 하지 않는다 — 조회 전용.**
**서브에이전트:** 공유 서브에이전트 `portfolio-reader`(보유 원장 읽기)을 호출하고, 현재가는 `tossctl quote get`(시세 READ 전용, 문법은 `scripts/tossctl-commands.md`)으로 조회해 팀장이 수익률을 계산. **출처는 사용자 출력에 적지 않는다**(내부 확인용).
**구현 상태:** `.claude/skills/stock-check/SKILL.md` 구현됨.

### 스킬 3) `/stock-explain` — 내 눈높이 과외선생 (추천 없음)

**목적:** 증권사 앱은 숫자만 보여주고 *설명하지 않는다.* 사용자가 묻는 용어·개념·궁금증(`공매도`, `우선주가 뭐야?`, `S&P500 ETF가 뭐야?`)을 주린이 눈높이로 풀어주고, 가능하면 **사용자의 실제 보유와 연결**한다. 이 OS의 정체성("대시보드가 아니라 과외선생", OS.md 원칙 5)을 가장 직접적으로 구현한 스킬.
**핵심 제약:** 매수/매도 추천 금지. 사실형 질문은 1차·제도권 출처(시세는 `tossctl`, 문법은 `scripts/tossctl-commands.md`)로 **내부에서 확인**하되(가짜 정보 금지), **사용자 출력엔 출처를 적지 않는다**(내부 확인용). `stock/lessons/curriculum.md`의 체크박스를 읽어 이미 배운 건 반복하지 않고, 새로 다룬 용어는 그 줄을 `- [x]`로 체크(daily-brief와 진도 공유 — 별도 본문 파일은 안 만듦).
**구현 상태:** `.claude/skills/stock-explain/SKILL.md` 구현됨.

### 스킬 4) `/portfolio-review` — 내 보유 구성 진단 (추천 아님)

**목적:** 증권사 앱은 종목별 수익률을 *나열*만 한다. 이 스킬은 보유를 **합쳐서** 비중·집중도·섹터 쏠림·환노출(해외자산)·현금 비중을 계산해 "내 포트폴리오가 어떤 모양인지"를 쉽게 설명한다. **"진단"이지 "처방(추천)"이 아니다.**
**서브에이전트:** 공유 서브에이전트 `portfolio-reader`(보유 원장 읽기)을 호출 → `/stock-check`에 이어 **두 번째 호출처**(공유 서브에이전트 요건 충족). 현재가는 `tossctl quote batch`(시세 READ 전용, 문법은 `scripts/tossctl-commands.md`)로 조회해 팀장이 비중을 계산. **출처는 사용자 출력에 적지 않는다.**
**구현 상태:** `.claude/skills/portfolio-review/SKILL.md` 구현됨.

### 유틸리티 스킬 & 안전장치
- **`/git-commit`** — 변경사항을 접두사 규칙(`feature`·`fix`·`refactor`·`docs` 등)에 맞춰 커밋한다(푸시는 안 함). 구현됨.
- **자동매매 차단 훅** `block-auto-trade.py` — 모든 Bash 호출을 감시해 tossctl 실주문을 거부(§3.5). 원칙 1을 기계적으로 강제. 구현됨
