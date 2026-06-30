#!/usr/bin/env python3
"""추천 실행 1건을 회고용 스냅샷으로 저장한다 (append-only). recommend-stocks의 '선정 결정' 담당.

설계(OS.md "기록 책임 분리"):
- 이 파일은 *선정 결정*만 박제한다: 필터 기준·단계별 개수·탈락 표본(입력) + 어떤 종목을 골랐나(출력).
- 진입/목표/손절 같은 분석 수치는 여기서 **복제하지 않는다.** 각 종목은 analyze-company가
  저장한 분석 기록(save_analysis.py 의 ref)을 picks[].analysis 로 '참조'만 한다.
  본문 표를 그릴 때 그 분석 파일의 frontmatter를 역참조해 진입/목표/손절을 끌어와 보여준다.
  → frontmatter엔 숫자가 중복되지 않아(드리프트 방지) 진실원천은 항상 분석 기록 한 곳이다.
- 과거 기록은 절대 덮어쓰지 않는다. 같은 날 두 번 실행하면 -2, -3 ... 으로 새 파일을 만든다.
- 저장 위치: 프로젝트 내 data/recommendations/YYYY-MM-DD-run[-N].md
- 형식: frontmatter(정량, 기계가 읽음) + 본문(근거, 사람이 읽음).

입력 JSON 스키마(메인 에이전트가 3단계까지 끝내고 조립해 넘김):
{
  "run_date": "2026-06-30",                 # 생략 시 오늘
  "filter": {"min_market_cap_eok":5000, ...},
  "counts": {"stage1":53, "stage2":10, "final":10},
  "picks": [
    {"code":"000270","name":"기아","stability":86.8,
     "analysis":"data/analyses/2026-06-30-기아.md",   # save_analysis.py 가 돌려준 ref
     "note":"안정성 1위"}                              # 선택: 추천 맥락의 한 줄(분석 근거 아님)
  ],
  "excluded_sample": [{"name":"삼성전자","reason":"PER 26.3 > 25 (과열)"}]
  # excluded_sample[].price 는 생략 가능 — 저장 시 quote.py 로 '당시가'를 채워 박제한다(회고 정량비교용).
  # excluded_sample[].code 가 있으면 종목코드로 정확 조회. no_fetch:true 면 당시가 조회를 생략한다.
}
"""
from __future__ import annotations
import datetime
import importlib.util
import json
import os
import re
import sys

REC_DIR = os.path.join(os.getcwd(), "data", "recommendations")

# 탈락 종목의 '당시가'(추천 시점가)를 박제하려고 analyze-company의 quote.py 를 재사용한다.
# 회고가 "빠뜨린 종목이 그 뒤 올랐나"를 정량 비교하려면 기준가가 있어야 하는데, 예전엔 미기록이라
# 정성 점검밖에 못 했다(evaluate_records.py 의 '탈락 당시 가격 미기록' 한계). 이 칸을 그때 채운다.
_QUOTE = os.path.normpath(os.path.join(
    os.path.dirname(__file__), "..", "..", "analyze-company", "scripts", "quote.py"))


def _load_quote():
    """quote.py 를 모듈로 로드(현재가의 단일 진실원천 재사용). 실패하면 None → 당시가 생략."""
    if not os.path.exists(_QUOTE):
        return None
    spec = importlib.util.spec_from_file_location("quote", _QUOTE)
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)  # type: ignore[union-attr]
        return mod
    except Exception:
        return None


def _excluded_price(quote_mod, e: dict):
    """탈락 종목의 당시가. 입력에 price 가 있으면 그대로, 없으면 quote.py 로 조회(실패 시 None)."""
    if e.get("price") is not None:
        return e["price"]
    if quote_mod is None:
        return None
    try:
        code = e.get("code") or quote_mod.resolve_code(e.get("name", ""))
        q = quote_mod.fetch_quote(code) if code else None
        return (q or {}).get("price")
    except Exception:
        return None


def _read_analysis_fm(ref: str) -> dict:
    """분석 기록(save_analysis.py 출력)의 frontmatter를 역참조해 진입/목표/손절/status를 끌어온다.
    숫자의 진실원천은 분석 기록이므로 여기선 읽기만 한다. 못 읽으면 빈 dict."""
    if not ref:
        return {}
    path = ref if os.path.isabs(ref) else os.path.join(os.getcwd(), ref)
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        return {}
    m = re.match(r"^---\n(.*?)\n---", text, re.S)
    if not m:
        return {}
    fm = {}
    for line in m.group(1).splitlines():
        mm = re.match(r"^([A-Za-z_0-9]+):\s*(.*)$", line)
        if mm:
            fm[mm.group(1)] = mm.group(2).strip().strip('"')
    return fm


def _yaml_scalar(v) -> str:
    if isinstance(v, str):
        return json.dumps(v, ensure_ascii=False)
    if isinstance(v, bool):
        return "true" if v else "false"
    return str(v)


def _target_path(run_date: str) -> str:
    os.makedirs(REC_DIR, exist_ok=True)
    base = os.path.join(REC_DIR, f"{run_date}-run")
    path = f"{base}.md"
    n = 2
    while os.path.exists(path):           # append-only: 기존 기록 보존
        path = f"{base}-{n}.md"
        n += 1
    return path


def render(data: dict) -> str:
    run_date = data.get("run_date") or datetime.date.today().isoformat()
    flt = data.get("filter", {})
    counts = data.get("counts", {})
    picks = data.get("picks", [])
    excluded = data.get("excluded_sample", [])

    # --- frontmatter ---
    fm = ["---", f"run_date: {run_date}", "filter:"]
    for k, v in flt.items():
        fm.append(f"  {k}: {_yaml_scalar(v)}")
    fm.append("counts:")
    for k, v in counts.items():
        fm.append(f"  {k}: {_yaml_scalar(v)}")
    fm.append("picks:")
    for p in picks:
        fm.append(f"  - code: {_yaml_scalar(p.get('code',''))}")
        for key in ("name", "stability"):
            if p.get(key) is not None:
                fm.append(f"    {key}: {_yaml_scalar(p[key])}")
        # 진입/목표/손절·status는 복제하지 않는다 — 분석 기록을 ref로 가리키기만 한다.
        fm.append(f"    analysis: {_yaml_scalar(p.get('analysis',''))}")
    fm.append("---")

    # --- 본문 (진입/목표/손절은 분석 기록을 역참조해 표시) ---
    body = [f"\n# 종목 추천 스냅샷 — {run_date}\n",
            "## 추천 종목\n",
            "| 종목 | 안정성 | 진입가 | 목표(기대) | 손절가 | 분석기록 |",
            "|------|--------|--------|-----------|--------|----------|"]
    for p in picks:
        a = _read_analysis_fm(p.get("analysis", ""))
        ref = p.get("analysis", "")
        entry = a.get("entry", "참조불가")
        stop = a.get("stop", "참조불가")
        target = a.get("target", "참조불가")
        if a.get("expected_return_pct"):
            target = f"{target} (+{a['expected_return_pct']}%)"
        ref_cell = f"`{ref}`" if ref else "(없음)"
        body.append(f"| {p.get('name','')} | {p.get('stability','')} | "
                    f"{entry} | {target} | {stop} | {ref_cell} |")
    body.append("\n> 진입/목표/손절·status의 진실원천은 각 **분석 기록**이다. "
                "회고 스킬은 분석 기록의 status를 갱신한다(이 표는 그때의 역참조 스냅샷).")
    if excluded:
        body.append("\n## 탈락 표본 (회고용 — 빠뜨린 종목 검증)\n")
        for e in excluded:
            px = e.get("price")
            px_str = f" (당시가 {px})" if px is not None else ""
            body.append(f"- {e.get('name','')}: {e.get('reason','')}{px_str}")
    body.append("\n## ⚠️ 유의")
    body.append("- 웹 데이터 기반 참고 자료이며 투자 권유가 아니다. 실제 매매·주문은 사람이 결정한다.")
    return "\n".join(fm) + "\n" + "\n".join(body) + "\n"


def main() -> int:
    raw = open(sys.argv[1], encoding="utf-8").read() if len(sys.argv) > 1 else sys.stdin.read()
    data = json.loads(raw)
    # 탈락 종목 당시가 박제: 입력에 price 가 없으면 저장 시점에 quote.py 로 한 번 조회해 채운다.
    # (no_fetch=true 면 네트워크 생략 — 오프라인/테스트.)
    quote_mod = None if data.get("no_fetch") else _load_quote()
    for e in data.get("excluded_sample", []):
        if e.get("price") is None:
            px = _excluded_price(quote_mod, e)
            if px is not None:
                e["price"] = px
    run_date = data.get("run_date") or datetime.date.today().isoformat()
    path = _target_path(run_date)
    with open(path, "w", encoding="utf-8") as f:
        f.write(render(data))
    print(f"saved: {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
