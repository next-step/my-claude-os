# tossctl 명령 참고 (Command Reference)

> 이 문서는 주식 OS가 쓰는 **`tossctl` 명령을 한곳에 모아둔 참고표**다.
> 스킬·에이전트는 tossctl 명령을 이 표 기준으로 실행하고, **문법이 바뀌거나 새 명령이 필요하면 여기만 보고/여기에 추가**하면 된다(명령 지식의 단일 출처).
>
> 원본 레퍼런스: https://tossinvest-cli.vercel.app/docs/reference/commands

## 공통 규칙 (엄수)
- **자동화는 항상 `--output json`.** (기본은 table — 사람이 볼 때만.)
- **시세·시장 READ 전용으로만 쓴다.** 아래 "✅ 사용" 표의 조회 명령만 사용.
- **❌ 절대 실행 금지:** 주문(`order place/cancel/amend`, `--execute`)·계좌/보유(`account`, `portfolio`)·거래내역(`transactions`)·주문내역(`orders`). 이유:
  - 주문 = OS 원칙1 **자동매매 금지**(사람이 직접 실행). `PreToolUse` 훅 `block-auto-trade.py`가 하네스 차원에서도 막는다.
  - 계좌/보유 = 내 토스 계좌는 비어 있음. **보유의 단일 출처는 `portfolio/holdings.md` 원장**이다.
- 실행 전 **세션 확인**: `tossctl auth status`. 만료면 웹 1차 출처(네이버 금융/KRX)로 폴백하고, 세션 연장은 사용자가 `tossctl auth extend`.

## ✅ 사용 (시세·시장 조회)

| 하고 싶은 것 | 명령 | 어디서 쓰나 |
|---|---|---|
| 세션 유효성 확인 | `tossctl auth status --output json` | 모든 스킬(실행 전) |
| 단일 종목 시세 | `tossctl quote get <티커> --output json` | stock-check, stock-explain, stock-watcher, fact-checker |
| 멀티 종목 시세 | `tossctl quote batch <t1,t2,...> --output json` | portfolio-review, stock-watcher |
| 캔들 흐름(ASCII) | `tossctl quote chart <티커> --interval 1m\|5m\|...\|60m` | stock-watcher(선택) |
| 매도가능수량 | `tossctl quote sellable <티커> --output json` | stock-check(선택) |
| 지수(한/미: KOSPI·KOSDAQ·나스닥·S&P500·다우) | `tossctl market index [<코드\|이름>] --output json` | stock-news-scraper, stock-explain, fact-checker |
| 환율·달러인덱스 | `tossctl market fx --output json` | stock-news-scraper, stock-explain, fact-checker |
| 투자자별(외국인·기관·개인) 순매수 | `tossctl market investors --output json` | stock-news-scraper |
| 업종별 등락 | `tossctl market sectors [<id>] --output json` | (필요 시) |
| 실시간 인기 종목 | `tossctl market ranking --size <N> --output json` | (필요 시) |
| 어닝콜 일정 | `tossctl market earnings [--major] --output json` | (필요 시) |
| 장 운영 시간 | `tossctl market hours --output json` | (필요 시) |

주요 필드(예): `quote get` → `last`(현재가)·`change`/`change_rate`(전일대비)·`fetched_at`(조회시각)·`high_52w`/`low_52w`·`market_cap`. `market fx` → `rates[]`의 `name`·`close`.

## ⚠️ 1차 출처로 쓰지 않음
- `tossctl market briefing` · `tossctl market signals` = **토스 AI 생성물**. 사실 근거로 인용 금지 — 참고만 하거나 `fact-checker` 검증 대상.

## ❌ 사용 안 함 (참고용으로만 나열 — 실행 금지)
- 주문: `tossctl order preview|place|cancel|amend`, `tossctl orders ...`
- 계좌/보유: `tossctl account ...`, `tossctl portfolio ...`
- 거래내역/내보내기: `tossctl transactions ...`, `tossctl export ...`

## 새 명령을 추가할 때
1. 원본 레퍼런스(위 URL)에서 정확한 문법을 확인한다.
2. **조회(READ) 명령인지** 확인한다(주문·계좌·거래내역이면 추가하지 않는다).
3. 이 문서 "✅ 사용" 표에 한 줄 추가한다.
4. 해당 명령을 쓸 스킬/에이전트에서 이 문서를 참고해 그대로 실행한다(`--output json` 잊지 말 것).
