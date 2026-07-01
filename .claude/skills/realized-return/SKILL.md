---
name: realized-return
description: stock-os 매도 실현손익을 세후·수수료·환율 차감 실질 기준으로 계산. "실수익 계산", "이거 팔면 실수익 얼마", "실현손익 계산해줘" 등을 요청할 때 트리거. 표면 수익률과 실질 수익률을 나란히 보여주고 trade-journal에 붙일 형태로 반환한다.
---

# realized-return (온디맨드 계산 스킬)

stock-os의 **매도 실현손익을 실질(세후·수수료·환율) 기준으로 계산**하는 스킬.
커널 규칙 "실수익 기준"(모든 수익률은 세금·수수료·환율 차감 후로 말한다)을 도구로 구현한다.

**계산 자체는 `return-calculator` 서브에이전트에 위임한다** — realized-return과 log-trade(매도
경로)가 세율·환율 공식을 각자 구현하면 숫자가 어긋날 수 있어, 하나의 공유 SSOT로 모았다.
이 스킬은 **입력 수집 → 서브에이전트 호출 → present + 후속 제안**의 얇은 오케스트레이션이다.
(웹조회 없이 로컬 파일 읽기 + 계산뿐이라 **로컬 실행 전용**이다 — `holdings.md`가 gitignore.)

## 절차

### 1. 매매 상세 수집 (메인)
사용자에게 질문하거나 `holdings.md`에서 참조해 아래를 채운다:
- 시장(코스피/코스닥/US), 티커, 수량
- 매수가·(미국) 매수 시 환율
- 매도가·(미국) 매도 시 환율
- (선택) 매매 수수료·환전 우대율 — `tax-and-fees.md §3`가 빈칸이면 사용자에게 물어본다.

### 2. 계산 위임 (return-calculator 서브에이전트)
수집한 매매 상세를 채워 `return-calculator`를 호출한다. 서브에이전트가
`05_reference/tax-and-fees.md`(세율·수수료·매도 손익 공식 SSOT)를 읽어 결정론적으로 계산해
**표면 vs 실질 표 + 환차손익 분해 + trade-journal용 한 줄**을 반환한다.
> 세율·양도세·거래세 상세 규칙은 `return-calculator`(계산 SSOT)와 `tax-and-fees.md`가 소유한다 —
> 여기서 재구현하지 않는다.

### 3. present + 후속 제안 (메인)
- 서브에이전트 결과(표면 vs 실질 표·환차 분해·붙여넣을 한 줄)를 사용자에게 present한다.
- **수수료가 빈칸으로 반환됐으면**: `tax-and-fees.md §3`에 본인 증권사 값을 채워 넣을지
  제안한다(연 단위 갱신 항목 — 한 번 채우면 재사용).
- 매매 기록(holdings·trade-journal) 갱신은 사람/기록 흐름(`log-trade`)의 몫이며, 이 스킬은
  붙여넣을 문구까지만 제공한다.

관련: `.claude/agents/return-calculator.md`(공유 계산 SSOT) · `05_reference/tax-and-fees.md` ·
`03_journal/trade-journal.md` · log-trade 스킬 · rebalance-check 스킬
