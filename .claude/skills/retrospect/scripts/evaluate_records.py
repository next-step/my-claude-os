#!/usr/bin/env python3
"""회고 1단계 — 과거 분석 기록을 현재가와 대조해 status·실제수익률을 *결정론적으로* 확정한다.

설계(OS.md 회고 스킬):
- "목표 도달/손절 닿음" 같은 사실 판정은 토론(서브에이전트)이 아니라 *계산*이다.
  현재가 vs 진입/목표/손절을 비교하는 산수라, 정성 해석과 분리해 여기서 못 박는다.
  (OS 철학: 정성 해석=서브에이전트, 정밀 수치=결정론적 스크립트.)
- 현재가는 analyze-company의 quote.py 를 *재사용*한다(진실원천 한 곳, 드리프트 방지).
- 이 스크립트는 read-only다. status를 파일에 쓰는 건 update_status.py 의 몫(관심사 분리).
- 출력(stdout) JSON 을 토론 전문가들에게 '사실표'로 넘긴다.

status 판정 규칙:
- 터미널 상태(hit_target|stopped)는 **sticky**다 — 한 번 도달/손절이면 되돌리지 않는다.
  *왜:* 예측의 성패는 한 번 일어난 '사건'이다. 목표 찍고 다시 내려와도 "그땐 맞았다"가 진실.
  (주1회 스냅샷이라 실행 사이의 장중 돌파는 놓칠 수 있다 — 알려진 한계, 주기를 좁히면 줄어듦.)
- open/watching 만 재평가: 현재가 ≥ 목표 → hit_target / 현재가 ≤ 손절 → stopped / 그 사이 → watching.
- 진입·목표·손절이 "산출 불가"였던 기록은 평가 불가 → status 그대로, 사유 표기.

실제수익률: (현재가 − 진입가)/진입가. 진입가 없으면 분석 당시 현재가(price) 기준. 둘 다 없으면 null.

사용:  python3 evaluate_records.py                 # data/analyses 전체 평가
       python3 evaluate_records.py --no-fetch      # 현재가 조회 생략(오프라인/테스트 — status 미판정)
출력:  {"as_of","evaluated":[...],"excluded_context":[...],"errors":[...]} JSON
"""
from __future__ import annotations
import argparse
import datetime
import glob
import importlib.util
import json
import os
import re
import sys

ANALYSES_DIR = os.path.join(os.getcwd(), "data", "analyses")
REC_DIR = os.path.join(os.getcwd(), "data", "recommendations")

# analyze-company 의 quote.py 를 재사용(현재가의 단일 진실원천). 경로는 이 파일 기준 상대.
_QUOTE = os.path.normpath(os.path.join(
    os.path.dirname(__file__), "..", "..", "analyze-company", "scripts", "quote.py"))


def _load_quote():
    """quote.py 를 모듈로 로드해 fetch_quote/resolve_code 를 빌려 쓴다. 실패하면 None."""
    if not os.path.exists(_QUOTE):
        return None
    spec = importlib.util.spec_from_file_location("quote", _QUOTE)
    mod = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(mod)  # type: ignore[union-attr]
        return mod
    except Exception:
        return None


def _parse_frontmatter(text: str) -> dict:
    """save_analysis.py 가 쓴 frontmatter(평평한 key: value)를 dict 로. (save_run 의 파서와 동일 방식)"""
    m = re.match(r"^---\n(.*?)\n---", text, re.S)
    if not m:
        return {}
    fm = {}
    for line in m.group(1).splitlines():
        mm = re.match(r"^([A-Za-z_0-9]+):\s*(.*)$", line)
        if mm:
            fm[mm.group(1)] = mm.group(2).strip().strip('"')
    return fm


def _num(v):
    if v is None or v == "":
        return None
    try:
        return float(str(v).replace(",", ""))
    except ValueError:
        return None


def _judge(prev_status: str, cur: float | None,
           target: float | None, stop: float | None) -> tuple[str, str]:
    """(새 status, 사유). 터미널 상태는 sticky."""
    if prev_status in ("hit_target", "stopped"):
        return prev_status, "이미 확정된 사건(sticky) — 되돌리지 않음"
    if cur is None:
        return prev_status or "open", "현재가 미조회 — 판정 보류"
    if target is None and stop is None:
        return prev_status or "open", "진입/목표/손절 산출 불가 — 평가 불가"
    if target is not None and cur >= target:
        return "hit_target", f"현재가 {cur:g} ≥ 목표 {target:g}"
    if stop is not None and cur <= stop:
        return "stopped", f"현재가 {cur:g} ≤ 손절 {stop:g}"
    lo = f"{stop:g}" if stop is not None else "?"
    hi = f"{target:g}" if target is not None else "?"
    return "watching", f"현재가 {cur:g} (손절 {lo}~목표 {hi} 사이)"


def _days(date_str: str, as_of: datetime.date) -> int | None:
    try:
        d = datetime.date.fromisoformat(date_str)
        return (as_of - d).days
    except (ValueError, TypeError):
        return None


def evaluate(quote_mod, as_of: datetime.date, fetch: bool) -> dict:
    out = {"as_of": as_of.isoformat(), "evaluated": [],
           "excluded_context": [], "errors": []}

    files = sorted(glob.glob(os.path.join(ANALYSES_DIR, "*.md")))
    if not files:
        out["errors"].append(f"분석 기록이 없다: {ANALYSES_DIR} (먼저 analyze-company 를 돌려 기록을 쌓아야 회고할 게 생긴다)")

    seen_codes: dict[str, dict] = {}  # 종목별 현재가 캐시(같은 종목 여러 기록이면 1회만 조회)

    def current_price(code, name):
        if not fetch or quote_mod is None:
            return None
        key = code or name
        if key in seen_codes:
            return seen_codes[key].get("price")
        q = None
        try:
            c = code or quote_mod.resolve_code(name)
            q = quote_mod.fetch_quote(c) if c else None
        except Exception as e:  # 네트워크/파싱 실패는 그 종목만 건너뛴다
            out["errors"].append(f"현재가 조회 실패({name or code}): {e}")
        seen_codes[key] = q or {}
        return (q or {}).get("price")

    for path in files:
        text = open(path, encoding="utf-8").read()
        fm = _parse_frontmatter(text)
        ref = os.path.relpath(path, os.getcwd())
        name, code = fm.get("name", ""), fm.get("code", "")
        entry = _num(fm.get("entry"))
        target = _num(fm.get("target"))
        stop = _num(fm.get("stop"))
        price0 = _num(fm.get("price"))           # 분석 당시 현재가(수익률 폴백 기준)
        prev = fm.get("status", "open")

        cur = current_price(code, name)
        new_status, reason = _judge(prev, cur, target, stop)

        base = entry if entry is not None else price0   # 수익률 기준가
        actual_return_pct = None
        if cur is not None and base:
            actual_return_pct = round((cur - base) / base * 100, 1)

        out["evaluated"].append({
            "file": ref, "date": fm.get("date", ""), "name": name, "code": code,
            "context": fm.get("context", ""),
            "entry": entry, "target": target, "stop": stop,
            "expected_return_pct": _num(fm.get("expected_return_pct")),
            "price_at_analysis": price0, "current_price": cur,
            "prev_status": prev, "new_status": new_status, "status_changed": new_status != prev,
            "judge_reason": reason,
            "actual_return_pct": actual_return_pct,
            "days_elapsed": _days(fm.get("date", ""), as_of),
        })

    # 추천 탈락 표본: 회고에서 "빠뜨린 종목이 올랐나" 점검용 맥락. 단 탈락 당시 가격이 기록에
    # 없어 정량 수익률은 못 낸다(현재가만 제공). → 전문가가 정성으로 판단한다(알려진 한계).
    for rpath in sorted(glob.glob(os.path.join(REC_DIR, "*.md"))):
        rtext = open(rpath, encoding="utf-8").read()
        for name, why in re.findall(r"^-\s*([^:]+):\s*(.+)$", rtext, re.M):
            nm = name.strip()
            if nm and len(nm) <= 20:  # 본문 '탈락 표본' 불릿만 대략 추림
                cur = current_price(None, nm)
                out["excluded_context"].append({
                    "name": nm, "exclude_reason": why.strip(),
                    "current_price": cur, "run_file": os.path.relpath(rpath, os.getcwd()),
                    "note": "탈락 당시 가격 미기록 → 등락 정량비교 불가(정성 점검)",
                })

    # 집계 요약(전문가·리포트가 바로 쓰도록)
    ev = out["evaluated"]
    out["summary"] = {
        "total": len(ev),
        "hit_target": sum(1 for e in ev if e["new_status"] == "hit_target"),
        "stopped": sum(1 for e in ev if e["new_status"] == "stopped"),
        "watching": sum(1 for e in ev if e["new_status"] == "watching"),
        "open_or_unrated": sum(1 for e in ev if e["new_status"] == "open"),
        "status_changes": [e for e in ev if e["status_changed"]],
        "avg_actual_return_pct": (
            round(sum(e["actual_return_pct"] for e in ev if e["actual_return_pct"] is not None)
                  / max(1, sum(1 for e in ev if e["actual_return_pct"] is not None)), 1)
            if any(e["actual_return_pct"] is not None for e in ev) else None),
    }
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="회고 1단계: 분석 기록 vs 현재가 사실 판정")
    ap.add_argument("--no-fetch", action="store_true", help="현재가 조회 생략(오프라인/테스트)")
    ap.add_argument("--as-of", help="기준일 YYYY-MM-DD (생략 시 오늘)")
    args = ap.parse_args()

    as_of = datetime.date.fromisoformat(args.as_of) if args.as_of else datetime.date.today()
    quote_mod = None if args.no_fetch else _load_quote()
    if not args.no_fetch and quote_mod is None:
        # quote.py 를 못 불러오면 현재가 없이라도 기록 목록은 내보낸다(판정만 보류).
        print(json.dumps({"warning": f"quote.py 로드 실패: {_QUOTE} — 현재가 없이 진행"},
                         ensure_ascii=False), file=sys.stderr)
    result = evaluate(quote_mod, as_of, fetch=not args.no_fetch and quote_mod is not None)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
