# 🤖 My Claude Code OS

이 저장소는 **"클로드 OS 만들기"** 실습 프로젝트다. Claude Code를 단순 도구가 아니라,
스킬·에이전트·훅으로 구성된 하나의 **개인 운영체제(OS)** 처럼 키워 나간다.
`OS.md`는 이 OS의 정체성과 탑재 모듈을 기록하는 헌법 같은 문서다.

---

## 🎯 대표 주제 — 2026 월드컵 추적기 (World Cup Tracker)

이 OS의 첫 번째 플래그십 모듈. 지금 진행 중인 **2026 FIFA 월드컵**(미국·캐나다·멕시코 공동 개최,
2026.06.11 – 07.19)의 **전체 현황과 주요 뉴스를 추적**해, 월드컵 공식 홈페이지 감성의
**모던 HTML 리포트**로 보여 준다.

### 무엇을 보여 주나
- 대회 전체 현황(현재 라운드, 다음 라운드 일정)과 핵심 통계 카드
- 오늘의 주요 뉴스(기록 경신·이변·빅매치·오늘 경기)
- 32강 진출 확정 현황(조별)
- 🇰🇷 대한민국이 속한 A조 스포트라이트 순위표
- 골든부트(최다 득점) 레이스

### 어떻게 동작하나 — 병렬 에이전트 오케스트레이션
```
/worldcup (스킬)
   ├─ 병렬 호출 ─┬─ worldcup-fixtures (경기·순위·진출 수집)
   │            └─ worldcup-news     (뉴스·골든부트 수집)
   ├─ 두 결과를 data.json 으로 병합
   └─ generate_report.py 로 HTML 생성 + 브라우저 열기
```

핵심 설계 원칙:
1. **데이터/표현 분리** — 에이전트는 `data.json`(데이터)만 갱신, 디자인은 `generate_report.py`만 담당.
2. **병렬 전문화** — 출처와 조사 방식이 다른 두 영역(경기 vs 뉴스)을 전담 에이전트가 동시에 수집.
3. **재실행 친화** — 디자인만 바꾸려면 렌더러 스크립트만 다시 돌리면 된다.

---

## 🧩 탑재 모듈 맵

### 스킬 (`.claude/skills/`)
| 스킬 | 역할 |
|------|------|
| `worldcup` | 월드컵 현황·뉴스를 병렬 수집해 HTML 리포트로 렌더 (대표 모듈) |
| `briefing` | 주제 하나를 여러 각도로 나눠 `web-researcher`를 **병렬** 호출 → HTML 브리핑 리포트 |
| `factcheck` | 주장 하나를 `web-researcher` **찬·반 병렬**(적대적)로 검증 → HTML 판정 카드 |
| `commit` | 변경사항을 분석해 커밋 메시지 작성 후 GitHub 푸시 |
| `skill-report` | skill 사용 로그를 분석해 HTML 리포트 생성 |

### 에이전트 (`.claude/agents/`)
| 에이전트 | 역할 |
|----------|------|
| `web-researcher` | **공유 모듈** — 어떤 주제·주장이든 스니펫 기반으로 조사해 근거(찬/반/중립)+출처 JSON 반환. `briefing`·`factcheck`가 함께 재활용 |
| `worldcup-fixtures` | 경기 결과·조별 순위·32강 진출·A조 스포트라이트 수집 → JSON |
| `worldcup-news` | 주요 헤드라인·골든부트 레이스 조사·요약 → JSON |

> 🔁 **공유 서브에이전트**: `web-researcher`는 조사 엔진을 한 곳에 모은 재활용 모듈이다.
> `briefing`은 그것을 **중립 다각도**로, `factcheck`는 **찬·반 적대**로 부린다. 리서치 품질을
> 이 에이전트 하나에서 개선하면 두 스킬이 함께 좋아진다 — OS 모듈 재활용의 대표 사례.

### 산출물
- `.claude/worldcup-report.html` — 월드컵 리포트(브라우저로 열림)
- `.claude/briefing-report.html` — 주제 브리핑 리포트
- `.claude/factcheck-report.html` — 팩트체크 판정 카드
- `.claude/skills/*/data.json` — 각 스킬에서 에이전트 결과가 병합되는 데이터 소스

---

## 🛠 사용법
```
/worldcup                       # 월드컵 최신 현황을 수집해 리포트 생성 + 브라우저 열기
/briefing 2026 AI 반도체 시장     # 주제를 여러 각도로 병렬 조사해 브리핑 리포트
/factcheck 한국은 2026 월드컵 32강에 진출했다   # 주장을 찬·반 병렬 검증해 판정 카드
```
디자인만 다시 렌더하려면 각 스킬의 렌더러만 다시 돌린다:
```
python3 .claude/skills/worldcup/generate_report.py
python3 .claude/skills/briefing/generate_report.py
python3 .claude/skills/factcheck/generate_report.py
```

---

## 📐 OS 운영 원칙
1. 클로드 OS 관련 모든 파일은 반드시 이 프로젝트 안에 만든다.
2. 실습 프로젝트이므로, 작업 과정에서 AI와의 협업 방법을 배울 수 있도록 충분한 설명을 제공한다.
3. 새 기능은 가능하면 **스킬 + (필요 시) 전담 에이전트** 형태로 모듈화하고, 이 문서의 모듈 맵에 등록한다.
