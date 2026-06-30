#!/usr/bin/env python3
"""코스피 1차 스크리너 — 네이버 금융 시가총액 페이지를 긁어 하드컷 필터를 적용한다.

설계 메모(OS.md 종목 추천 파이프라인의 1차 필터):
- 1000개 전부를 개별 분석하면 느리고 비싸다. 여기서 '싼 일괄 컬럼'으로 먼저 줄인다.
- 출처: https://finance.naver.com/sise/sise_market_sum.naver (sosok=0=코스피)
  제공 컬럼: 종목명/현재가/시가총액/거래량/외국인비율/PER/ROE  ← 1차 하드컷에 충분.
- 표준 라이브러리만 사용(시스템 python3에서 pip 설치 없이 동작). EUC-KR 디코딩 주의.

부채비율·영업이익 추세 등 '2차 안정성 스코어링'은 개별 종목 재무 페이지가 필요하므로
이 스크립트가 아니라 다음 단계(SKILL.md 3단계)에서 다룬다.
"""
from __future__ import annotations
import argparse
import json
import re
import sys
import time
import urllib.request
from html.parser import HTMLParser

UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
      "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
BASE = "https://finance.naver.com/sise/sise_market_sum.naver?sosok=0&page={page}"

# --- 1차 하드컷 기준 (안정성 우선) -------------------------------------------
# 임계값과 그 근거는 SKILL.md의 "필터 기준" 표에 정리되어 있다. 여기 값과 동기화할 것.
DEFAULTS = dict(
    min_market_cap_eok=5000,   # 시가총액 ≥ 5,000억: 소형주·품절주 제외(유동성·안정성 하한)
    min_value_eok=10,          # 일 거래대금 ≥ 10억(현재가×거래량): 못 빠져나오는 종목 제외
    max_per=25.0,              # PER ≤ 25: 과열 제외. PER이 없거나 음수면 적자 → 제외
    min_per=0.0,               # PER > 0: 적자 제외
    min_roe=5.0,               # ROE ≥ 5%: 최소 수익성
)


class _SiseTableParser(HTMLParser):
    """네이버 시총 테이블(<table class="type_2">)의 데이터 행만 추출한다."""

    def __init__(self) -> None:
        super().__init__()
        self.in_target_table = False
        self.in_row = False
        self.in_cell = False
        self.cur_cells: list[str] = []
        self.cur_code: str | None = None
        self.rows: list[tuple[str | None, list[str]]] = []
        self._table_depth = 0

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "table" and "type_2" in (a.get("class") or ""):
            self.in_target_table = True
            self._table_depth = 1
        elif tag == "table" and self.in_target_table:
            self._table_depth += 1
        if not self.in_target_table:
            return
        if tag == "tr":
            self.in_row, self.cur_cells, self.cur_code = True, [], None
        elif tag == "td":
            self.in_cell = True
        elif tag == "a" and self.in_row:
            href = a.get("href", "")
            m = re.search(r"code=(\d{6})", href)
            if m and self.cur_code is None:
                self.cur_code = m.group(1)

    def handle_endtag(self, tag):
        if not self.in_target_table:
            return
        if tag == "td":
            self.in_cell = False
        elif tag == "tr" and self.in_row:
            if self.cur_cells:
                self.rows.append((self.cur_code, self.cur_cells))
            self.in_row = False
        elif tag == "table":
            self._table_depth -= 1
            if self._table_depth == 0:
                self.in_target_table = False

    def handle_data(self, data):
        if self.in_target_table and self.in_cell:
            t = data.strip()
            if t:
                self.cur_cells.append(t)


def _num(s: str) -> float | None:
    s = (s or "").replace(",", "").replace("%", "").strip()
    if s in ("", "N/A", "-"):
        return None
    try:
        return float(s)
    except ValueError:
        return None


def fetch_page(page: int) -> str:
    req = urllib.request.Request(BASE.format(page=page), headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=20) as r:
        return r.read().decode("euc-kr", errors="replace")


def parse_rows(html: str) -> list[dict]:
    """셀 매핑.

    왼쪽: [0]=순번 [1]=종목명 [2]=현재가  (항상 고정)
    중간의 '전일비 방향(상승/하락)' 셀은 보합 종목에서 사라져 행 길이가 가변적이다.
    그래서 나머지는 오른쪽 끝에서 센다(가변 셀의 영향을 받지 않음):
      [-6]=시가총액(억) [-5]=상장주식수 [-4]=외국인비율 [-3]=거래량 [-2]=PER [-1]=ROE
    """
    p = _SiseTableParser()
    p.feed(html)
    out = []
    for code, cells in p.rows:
        if code is None or len(cells) < 8:  # 데이터 행만(헤더/구분선은 짧아 탈락)
            continue
        try:
            name = cells[1]
            price = _num(cells[2])
            cap_eok = _num(cells[-6])     # 시가총액 (억 원 단위로 표기됨)
            volume = _num(cells[-3])      # 거래량 (주)
            per = _num(cells[-2])
            roe = _num(cells[-1])
        except IndexError:
            continue
        out.append(dict(code=code, name=name, price=price, cap_eok=cap_eok,
                        volume=volume, per=per, roe=roe))
    return out


def is_preferred(name: str) -> bool:
    """우선주/스팩 휴리스틱 제외."""
    return bool(re.search(r"(우[BC]?|우선주)$", name)) or "스팩" in name


def passes(stock: dict, cfg: dict) -> bool:
    cap, price, vol, per, roe = (stock["cap_eok"], stock["price"],
                                 stock["volume"], stock["per"], stock["roe"])
    if None in (cap, price, vol):
        return False
    if is_preferred(stock["name"]):
        return False
    if cap < cfg["min_market_cap_eok"]:
        return False
    value_eok = price * vol / 1e8          # 거래대금(원) → 억 환산
    if value_eok < cfg["min_value_eok"]:
        return False
    if per is None or not (cfg["min_per"] < per <= cfg["max_per"]):
        return False
    if roe is None or roe < cfg["min_roe"]:
        return False
    return True


def screen(cfg: dict, max_pages: int = 40, sleep: float = 0.3) -> list[dict]:
    candidates, seen = [], set()
    for page in range(1, max_pages + 1):
        rows = parse_rows(fetch_page(page))
        if not rows:
            break
        new = 0
        for s in rows:
            if s["code"] in seen:
                continue
            seen.add(s["code"])
            new += 1
            if passes(s, cfg):
                candidates.append(s)
        if new == 0:
            break
        time.sleep(sleep)
    candidates.sort(key=lambda s: s["cap_eok"], reverse=True)
    return candidates


def main() -> int:
    ap = argparse.ArgumentParser(description="코스피 1차 스크리너 (네이버 시총 페이지)")
    ap.add_argument("--max-pages", type=int, default=40)
    ap.add_argument("--json", action="store_true", help="JSON으로 출력")
    for k, v in DEFAULTS.items():
        ap.add_argument(f"--{k.replace('_', '-')}", type=type(v), default=v)
    args = ap.parse_args()
    cfg = {k: getattr(args, k) for k in DEFAULTS}

    res = screen(cfg, max_pages=args.max_pages)
    if args.json:
        print(json.dumps(res, ensure_ascii=False, indent=2))
    else:
        print(f"# 1차 통과 종목: {len(res)}개 (기준: {cfg})\n")
        print(f"{'종목명':<14}{'시총(억)':>10}{'PER':>8}{'ROE':>8}")
        for s in res:
            print(f"{s['name']:<14}{s['cap_eok']:>10,.0f}{s['per']:>8.1f}{s['roe'] or 0:>8.1f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
