#!/usr/bin/env python3
"""코스피 2차 안정성 스코어러 — 1차 통과 종목의 개별 재무를 보고 안정성 점수를 매긴다.

설계(OS.md 종목 추천 파이프라인 2차):
- 입력: screen_kospi.py 의 1차 통과 후보(JSON, code/name/per/roe...).
- 출처: https://finance.naver.com/item/main.nhn?code=XXXXXX 의 '기업실적분석' 표(FnGuide).
  여기서 영업이익(연간 3년)·부채비율·ROE 를 얻는다. (개별 페이지는 UTF-8 — 시총 목록의 EUC-KR과 다름)
- 하드컷: 부채비율 ≤ 150 AND 가장 최근 actual 연도 영업이익 흑자.
- 점수(0~100, 안정성 우선 / 상승 기대치는 넣지 않음):
    재무건전성(부채비율)            30
    이익안정성(흑자지속+영업이익변동성) 25
    주가 변동성(낮을수록↑)          15   ← ohlcv.py(일별 시세) 재사용
    배당(시가배당률)               10   ← 기업실적분석 표에 이미 있음
    수익성(ROE)                   10
    밸류에이션(PER, 낮을수록↑)      10
  → 안정성 계열(재무건전성+이익안정성+주가변동성+배당)=80, 성장/밸류(ROE+PER)=20. 안정성 우선 의도.

주가 변동성은 analyze-company/scripts/ohlcv.py 를 import 해 계산한다(같은 OHLCV 소스 재사용).
없거나 실패하면 변동성 가점은 중립값(절반)으로 graceful 처리하고 점수는 계속 낸다.
배점·임계값은 튜닝 대상 — 바꾸면 SKILL.md 점수표와 동기화할 것.
"""
from __future__ import annotations
import argparse
import json
import os
import re
import sys
import time
import urllib.request

UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
      "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
ITEM = "https://finance.naver.com/item/main.nhn?code={code}"

# 변동성 가점은 analyze-company 의 ohlcv.py 를 재사용(이 스킬은 이미 analyze-company 스킬에 의존).
# 못 찾으면 None → 변동성 가점만 중립 처리하고 나머지 점수는 정상 동작(graceful).
try:
    sys.path.insert(0, os.path.join(os.path.dirname(__file__),
                                    "..", "..", "analyze-company", "scripts"))
    import ohlcv as _ohlcv
except Exception:
    _ohlcv = None

DEFAULTS = dict(max_debt_ratio=150.0, top_n=10, vol_days=60,
                vol_lo=20.0, vol_hi=70.0, div_full=4.0)


def _num(s: str):
    s = (s or "").replace(",", "").replace("%", "").strip()
    if s in ("", "N/A", "-"):
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _txt(x: str) -> str:
    return re.sub(r"<[^>]+>", "", x).replace("&nbsp;", "").replace("\xa0", "").strip()


def fetch_financials(code: str) -> dict | None:
    """개별 종목의 '기업실적분석' 표에서 지표 행을 뽑아 {라벨: [연간 actual 값...]} 반환."""
    req = urllib.request.Request(ITEM.format(code=code), headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            html = r.read().decode("utf-8", errors="replace")
    except Exception:
        return None
    i = html.find("기업실적분석")
    if i < 0:
        return None
    seg = html[i:html.find("</table>", i)]

    # 기간 헤더로 연간 칸 개수와 (E) 추정치 위치 파악
    rows = re.findall(r"<tr[^>]*>(.*?)</tr>", seg, re.S)
    periods: list[str] = []
    for r in rows:
        ths = [_txt(t) for t in re.findall(r"<th[^>]*>(.*?)</th>", r, re.S)]
        cand = [t for t in ths if re.match(r"\d{4}\.\d{2}", t)]
        if cand:
            periods = cand
            break
    # 연간 블록 = 앞에서부터 .12 로 끝나며 연도가 증가하는 구간
    annual_n, prev_year = 0, None
    for p in periods:
        m = re.match(r"(\d{4})\.(\d{2})", p)
        if not m:
            break
        year, month = int(m.group(1)), m.group(2)
        if month != "12" or (prev_year is not None and year <= prev_year):
            break
        annual_n += 1
        prev_year = year
    est_mask = ["(E)" in p or "&#40;E&#41;" in p for p in periods[:annual_n]]

    metrics: dict[str, list] = {}
    for r in rows:
        th = re.findall(r"<th[^>]*>(.*?)</th>", r, re.S)
        td = re.findall(r"<td[^>]*>(.*?)</td>", r, re.S)
        if not th or not td:
            continue
        label = _txt(th[0])
        vals = [_num(_txt(x)) for x in td][:annual_n]  # 연간 칸만
        actual = [v for v, e in zip(vals, est_mask) if not e]  # 추정치(E) 제외
        metrics[label] = actual
    return metrics


def _pick(m: dict, prefix: str) -> list:
    for k in m:
        if k.startswith(prefix):
            return m[k]
    return []


def _roe_key(m: dict) -> list:
    return _pick(m, "ROE")


def _fetch_vol(code: str, cfg: dict) -> float | None:
    """ohlcv.py 로 연율 변동성(%)만 끌어온다. 모듈/네트워크 실패 시 None(→ 중립 처리)."""
    if _ohlcv is None or cfg.get("no_vol"):
        return None
    try:
        rows = _ohlcv.fetch_ohlcv(code, cfg["vol_days"])
        return _ohlcv.compute_volatility(rows).get("realized_vol_pct") if rows else None
    except Exception:
        return None


def score(stock: dict, fin: dict, cfg: dict, vol_pct: float | None = None) -> dict | None:
    op = fin.get("영업이익", [])
    debt = fin.get("부채비율", [])
    roe_series = _roe_key(fin)
    div_yield = next((v for v in reversed(_pick(fin, "시가배당률")) if v is not None), None)
    op_actual = [v for v in op if v is not None]
    debt_recent = next((v for v in reversed(debt) if v is not None), None)
    if not op_actual or debt_recent is None:
        return None

    # --- 하드컷 ---
    if debt_recent > cfg["max_debt_ratio"]:
        return None
    if op_actual[-1] <= 0:                 # 가장 최근 연도 영업적자 → 탈락
        return None

    # --- 점수 (안정성 우선, 합 100) ---
    # 재무건전성(30): 부채비율 50%↓=만점, 150%=0
    fin_health = max(0.0, min(1.0, (cfg["max_debt_ratio"] - debt_recent) / (cfg["max_debt_ratio"] - 50))) * 30

    # 이익안정성(25): 최근 3년 흑자비율(15) + 영업이익 변동성 낮을수록(10)
    last3 = op_actual[-3:]
    pos_ratio = sum(1 for v in last3 if v > 0) / len(last3)
    mean = sum(last3) / len(last3)
    if len(last3) >= 2 and mean > 0:
        var = sum((v - mean) ** 2 for v in last3) / len(last3)
        cov = (var ** 0.5) / mean                       # 변동계수
        earn_var = max(0.0, 1 - min(cov, 1.0))
    else:
        earn_var = 0.0
    earn_stab = pos_ratio * 15 + earn_var * 10

    # 주가 변동성(15): 연율 변동성 vol_lo↓=만점, vol_hi↑=0. 데이터 없으면 중립(절반).
    if vol_pct is None:
        vol_score = 15 * 0.5
    else:
        vol_score = max(0.0, min(1.0, (cfg["vol_hi"] - vol_pct) / (cfg["vol_hi"] - cfg["vol_lo"]))) * 15

    # 배당(10): 시가배당률 0%=0, div_full%↑=만점. 배당주는 대체로 안정적.
    div_score = max(0.0, min(1.0, (div_yield or 0) / cfg["div_full"])) * 10

    # 수익성(10): ROE 5%=0, 20%↑=만점
    roe = next((v for v in reversed(roe_series) if v is not None), stock.get("roe"))
    roe_score = max(0.0, min(1.0, ((roe or 0) - 5) / 15)) * 10

    # 밸류에이션(10): PER 10↓=만점, 25=0
    per = stock.get("per")
    val_score = max(0.0, min(1.0, (25 - (per or 25)) / 15)) * 10

    total = round(fin_health + earn_stab + vol_score + div_score + roe_score + val_score, 1)
    return dict(code=stock["code"], name=stock["name"], stability=total,
                price=stock.get("price"),          # 검증된 현재가 — 3단계 진입/손절 앵커로 전달
                debt_ratio=debt_recent, op_last3=last3, roe=roe, per=per,
                div_yield=div_yield, vol_pct=vol_pct,
                cap_eok=stock.get("cap_eok"))


def run(candidates: list[dict], cfg: dict, sleep: float = 0.25) -> list[dict]:
    scored = []
    for s in candidates:
        fin = fetch_financials(s["code"])
        time.sleep(sleep)
        if not fin:
            continue
        vol = _fetch_vol(s["code"], cfg)          # 일별 시세(여러 페이지) → 느린 단계
        r = score(s, fin, cfg, vol_pct=vol)
        if r:
            scored.append(r)
    scored.sort(key=lambda x: x["stability"], reverse=True)   # 안정성 우선 정렬
    return scored[: cfg["top_n"]]


def main() -> int:
    ap = argparse.ArgumentParser(description="코스피 2차 안정성 스코어러")
    ap.add_argument("--input", help="1차 결과 JSON 파일(없으면 stdin)")
    ap.add_argument("--max-debt-ratio", type=float, default=DEFAULTS["max_debt_ratio"])
    ap.add_argument("--top-n", type=int, default=DEFAULTS["top_n"])
    ap.add_argument("--vol-days", type=int, default=DEFAULTS["vol_days"],
                    help="변동성 산출용 거래일 수(기본 60). 클수록 정확하나 느림(종목당 페이지↑)")
    ap.add_argument("--no-vol", action="store_true",
                    help="변동성 가점 건너뜀(빠름). 변동성은 중립값으로 처리")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    raw = open(args.input, encoding="utf-8").read() if args.input else sys.stdin.read()
    candidates = json.loads(raw)
    cfg = dict(DEFAULTS, max_debt_ratio=args.max_debt_ratio, top_n=args.top_n,
               vol_days=args.vol_days, no_vol=args.no_vol)

    res = run(candidates, cfg)
    if args.json:
        print(json.dumps(res, ensure_ascii=False, indent=2))
    else:
        print(f"# 2차 안정성 상위 {len(res)}개 (하드컷: 부채비율≤{cfg['max_debt_ratio']}, 최근연도 흑자)\n")
        print(f"{'종목명':<14}{'안정성':>7}{'부채비율':>9}{'ROE':>7}{'PER':>7}{'배당%':>7}{'변동성%':>8}")
        for s in res:
            vol = "-" if s["vol_pct"] is None else f"{s['vol_pct']:.0f}"
            print(f"{s['name']:<14}{s['stability']:>7.1f}{s['debt_ratio']:>9.1f}"
                  f"{(s['roe'] or 0):>7.1f}{(s['per'] or 0):>7.1f}"
                  f"{(s['div_yield'] or 0):>7.2f}{vol:>8}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
