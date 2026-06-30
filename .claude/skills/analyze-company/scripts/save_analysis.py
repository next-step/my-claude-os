#!/usr/bin/env python3
"""분석 1건을 회고용 스냅샷으로 저장한다 (append-only). analyze-company의 기록 담당.

설계(OS.md "기록 책임 분리"):
- 진입/목표/손절 같은 분석 수치의 *단일 진실원천*은 이 파일이다.
  recommend-stocks의 추천 기록(save_run.py)은 이 파일을 '참조'만 하고 숫자를 복제하지 않는다.
- 단독 분석이든 추천 루프 안에서 돌든, analyze-company는 분석을 마칠 때마다 이걸 호출한다.
- 과거 기록은 절대 덮어쓰지 않는다. 같은 날 같은 회사를 또 분석하면 -2, -3 ... 새 파일.
- 저장 위치: 프로젝트 내 data/analyses/YYYY-MM-DD-<회사>[-N].md
- 형식: frontmatter(정량, 기계가 읽음 — 회고가 status를 갱신) + 본문(근거, 사람이 읽음).
- 출력(stdout): {"saved","ref","id","status"} JSON.
  - ref = 프로젝트 루트 기준 상대경로. save_run.py가 picks[].analysis 로 이 값을 받는다.

입력 JSON 스키마(메인 에이전트가 3.5단계까지 끝내고 조립해 넘김):
{
  "date": "2026-06-30",                 # 생략 시 오늘
  "code": "000270", "name": "기아",
  "price": 139000,                      # quote.py 현재가 (진입/손절의 앵커)
  "week52_high": 152000, "week52_low": 95000,   # 선택
  "entry": 137000, "target": 165000, "stop": 128000,
  "expected_return_pct": 18.7,          # 선택. 없으면 price→target 으로 계산
  "summary": "한 줄 요약",
  "axes": {                             # 분석 기준: 어떤 축을 봤고 요약은 무엇인가
    "trend": "최근 1년 +12%, 지지 130k/저항 165k",
    "company_news": "3분기 호실적, 신차 사이클",
    "market": "금리 동결·환율 부담"
  },
  "rationale": {                        # 가격대 근거 (한 줄씩)
    "entry": "현재가가 지지선 부근", "target": "컨센서스 평균", "stop": "지지선 -6%"
  },
  "sources": ["https://...", "..."],    # 선택
  "context": "standalone"               # standalone | recommend-stocks
}
"""
from __future__ import annotations
import datetime
import json
import os
import re
import sys

ANALYSES_DIR = os.path.join(os.getcwd(), "data", "analyses")


def _yaml_scalar(v) -> str:
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, str):
        return json.dumps(v, ensure_ascii=False)
    return str(v)


def _safe_name(name: str) -> str:
    """파일명 안전화: 경로 구분자·공백류만 정리. 한글은 그대로(가독성 — OS.md)."""
    name = re.sub(r"\s+", "", name.strip())
    return re.sub(r"[/\\:]", "_", name) or "unknown"


def _target_path(date: str, name: str) -> str:
    os.makedirs(ANALYSES_DIR, exist_ok=True)
    base = os.path.join(ANALYSES_DIR, f"{date}-{_safe_name(name)}")
    path = f"{base}.md"
    n = 2
    while os.path.exists(path):            # append-only: 기존 기록 보존
        path = f"{base}-{n}.md"
        n += 1
    return path


def render(data: dict, date: str) -> str:
    name = data.get("name", "")
    price = data.get("price")
    entry, target, stop = data.get("entry"), data.get("target"), data.get("stop")
    exp = data.get("expected_return_pct")
    if exp is None and price and target:
        exp = round((target - price) / price * 100, 1)
    axes = data.get("axes", {})
    rat = data.get("rationale", {})
    sources = data.get("sources", [])

    # --- frontmatter (회고가 status를 갱신하는 정량부) ---
    fm = ["---", f"date: {date}",
          f"code: {_yaml_scalar(data.get('code',''))}",
          f"name: {_yaml_scalar(name)}"]
    for k in ("price", "week52_high", "week52_low", "entry", "target", "stop"):
        if data.get(k) is not None:
            fm.append(f"{k}: {_yaml_scalar(data[k])}")
    if exp is not None:
        fm.append(f"expected_return_pct: {_yaml_scalar(exp)}")
    fm.append(f"context: {_yaml_scalar(data.get('context','standalone'))}")
    fm.append("status: open")              # 회고가 갱신: open|hit_target|stopped|watching
    fm.append("---")

    # --- 본문 (근거, 사람이 읽음) ---
    body = [f"\n# {name} 분석 스냅샷 — {date}\n"]
    if data.get("summary"):
        body += ["## 한 줄 요약", f"- {data['summary']}\n"]

    body += ["## 분석 기준 (무엇을 봤나)"]
    labels = {"trend": "주가 흐름", "company_news": "회사 뉴스", "market": "시장(거시)"}
    for key, label in labels.items():
        if axes.get(key):
            body.append(f"- **{label}**: {axes[key]}")
    for key, val in axes.items():          # 위 3축 외 추가 축도 누락 없이
        if key not in labels and val:
            body.append(f"- **{key}**: {val}")

    body += ["\n## 매매 가격대 (참고용)",
             f"- 현재가(앵커): {price if price is not None else '확인 불가'}",
             f"- 진입가: {entry if entry is not None else '산출 불가'}"
             + (f" — {rat['entry']}" if rat.get("entry") else ""),
             f"- 목표(기대치): {target if target is not None else '산출 불가'}"
             + (f" (+{exp}%)" if exp is not None else "")
             + (f" — {rat['target']}" if rat.get("target") else ""),
             f"- 손절가: {stop if stop is not None else '산출 불가'}"
             + (f" — {rat['stop']}" if rat.get("stop") else "")]

    if sources:
        body += ["\n## 출처"] + [f"- {s}" for s in sources]

    body += ["\n## ⚠️ 유의",
             "- 웹검색 기반 참고 자료이며 투자 권유가 아니다. 매매·주문은 사람이 결정한다.",
             "- 가격대는 관찰된 지지/저항·컨센서스에서 도출한 참고 수준이며 확정 예측이 아니다.",
             "- status 는 회고 스킬이 현재가와 대조해 갱신한다(open→hit_target|stopped|watching)."]
    return "\n".join(fm) + "\n" + "\n".join(body) + "\n"


def main() -> int:
    raw = open(sys.argv[1], encoding="utf-8").read() if len(sys.argv) > 1 else sys.stdin.read()
    data = json.loads(raw)
    date = data.get("date") or datetime.date.today().isoformat()
    path = _target_path(date, data.get("name", "unknown"))
    with open(path, "w", encoding="utf-8") as f:
        f.write(render(data, date))
    ref = os.path.relpath(path, os.getcwd())
    out = {"saved": path, "ref": ref,
           "id": os.path.splitext(os.path.basename(path))[0], "status": "open"}
    print(json.dumps(out, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
