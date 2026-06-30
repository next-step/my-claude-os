#!/usr/bin/env python3
"""코스피 2차 안정성 스코어러 — 1차 통과 종목의 개별 재무를 보고 안정성 점수를 매긴다.

설계(OS.md 종목 추천 파이프라인 2차):
- 입력: screen_kospi.py 의 1차 통과 후보(JSON, code/name/per/roe...).
- 출처: https://finance.naver.com/item/main.nhn?code=XXXXXX 의 '기업실적분석' 표(FnGuide).
  여기서 영업이익(연간 3년)·부채비율·ROE 를 얻는다. (개별 페이지는 UTF-8 — 시총 목록의 EUC-KR과 다름)
- 하드컷: 부채비율 ≤ 150 AND 가장 최근 actual 연도 영업이익 흑자.
- 점수(0~100, 안정성 우선 / 상승 기대치는 넣지 않음):
    재무건전성(부채비율)        35
    이익안정성(흑자지속+변동성)  35
    수익성(ROE)               15
    밸류에이션(PER, 낮을수록↑)  15

주가 변동성·배당 가점은 데이터 추가 시 확장(현재 미반영) — SKILL.md 점수표와 동기화할 것.
"""
from __future__ import annotations
import argparse
import json
import re
import sys
import time
import urllib.request

UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
      "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
ITEM = "https://finance.naver.com/item/main.nhn?code={code}"

DEFAULTS = dict(max_debt_ratio=150.0, top_n=10)


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


def _roe_key(m: dict) -> list:
    for k in m:
        if k.startswith("ROE"):
            return m[k]
    return []


def score(stock: dict, fin: dict, cfg: dict) -> dict | None:
    op = fin.get("영업이익", [])
    debt = fin.get("부채비율", [])
    roe_series = _roe_key(fin)
    op_actual = [v for v in op if v is not None]
    debt_recent = next((v for v in reversed(debt) if v is not None), None)
    if not op_actual or debt_recent is None:
        return None

    # --- 하드컷 ---
    if debt_recent > cfg["max_debt_ratio"]:
        return None
    if op_actual[-1] <= 0:                 # 가장 최근 연도 영업적자 → 탈락
        return None

    # --- 점수 ---
    # 재무건전성(35): 부채비율 50%↓=만점, 150%=0
    fin_health = max(0.0, min(1.0, (cfg["max_debt_ratio"] - debt_recent) / (cfg["max_debt_ratio"] - 50))) * 35

    # 이익안정성(35): 최근 3년 흑자비율(20) + 영업이익 변동성 낮을수록(15)
    last3 = op_actual[-3:]
    pos_ratio = sum(1 for v in last3 if v > 0) / len(last3)
    mean = sum(last3) / len(last3)
    if len(last3) >= 2 and mean > 0:
        var = sum((v - mean) ** 2 for v in last3) / len(last3)
        cov = (var ** 0.5) / mean                       # 변동계수
        stability = max(0.0, 1 - min(cov, 1.0))
    else:
        stability = 0.0
    earn_stab = pos_ratio * 20 + stability * 15

    # 수익성(15): ROE 5%=0, 20%↑=만점
    roe = next((v for v in reversed(roe_series) if v is not None), stock.get("roe"))
    roe_score = max(0.0, min(1.0, ((roe or 0) - 5) / 15)) * 15

    # 밸류에이션(15): PER 10↓=만점, 25=0
    per = stock.get("per")
    val_score = max(0.0, min(1.0, (25 - (per or 25)) / 15)) * 15

    total = round(fin_health + earn_stab + roe_score + val_score, 1)
    return dict(code=stock["code"], name=stock["name"], stability=total,
                price=stock.get("price"),          # 검증된 현재가 — 3단계 진입/손절 앵커로 전달
                debt_ratio=debt_recent, op_last3=last3, roe=roe, per=per,
                cap_eok=stock.get("cap_eok"))


def run(candidates: list[dict], cfg: dict, sleep: float = 0.25) -> list[dict]:
    scored = []
    for s in candidates:
        fin = fetch_financials(s["code"])
        time.sleep(sleep)
        if not fin:
            continue
        r = score(s, fin, cfg)
        if r:
            scored.append(r)
    scored.sort(key=lambda x: x["stability"], reverse=True)   # 안정성 우선 정렬
    return scored[: cfg["top_n"]]


def main() -> int:
    ap = argparse.ArgumentParser(description="코스피 2차 안정성 스코어러")
    ap.add_argument("--input", help="1차 결과 JSON 파일(없으면 stdin)")
    ap.add_argument("--max-debt-ratio", type=float, default=DEFAULTS["max_debt_ratio"])
    ap.add_argument("--top-n", type=int, default=DEFAULTS["top_n"])
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()

    raw = open(args.input, encoding="utf-8").read() if args.input else sys.stdin.read()
    candidates = json.loads(raw)
    cfg = dict(max_debt_ratio=args.max_debt_ratio, top_n=args.top_n)

    res = run(candidates, cfg)
    if args.json:
        print(json.dumps(res, ensure_ascii=False, indent=2))
    else:
        print(f"# 2차 안정성 상위 {len(res)}개 (하드컷: 부채비율≤{cfg['max_debt_ratio']}, 최근연도 흑자)\n")
        print(f"{'종목명':<14}{'안정성':>7}{'부채비율':>9}{'ROE':>7}{'PER':>7}")
        for s in res:
            print(f"{s['name']:<14}{s['stability']:>7.1f}{s['debt_ratio']:>9.1f}"
                  f"{(s['roe'] or 0):>7.1f}{(s['per'] or 0):>7.1f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
