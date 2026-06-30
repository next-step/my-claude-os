#!/usr/bin/env python3
"""추천 실행 1건을 회고용 스냅샷으로 저장한다 (append-only).

설계(OS.md 회고 데이터 관리):
- 입력+출력을 함께 박제: 필터 기준·단계별 개수·탈락 표본(입력) + 추천 종목과 가격(출력).
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
     "entry":95000,"target":108000,"stop":86000,"rationale":"저부채·고ROE..."}
  ],
  "excluded_sample": [{"name":"삼성전자","reason":"PER 26.3 > 25 (과열)"}]
}
"""
from __future__ import annotations
import datetime
import json
import os
import sys

REC_DIR = os.path.join(os.getcwd(), "data", "recommendations")


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
        for key in ("name", "stability", "entry", "target", "stop"):
            if p.get(key) is not None:
                fm.append(f"    {key}: {_yaml_scalar(p[key])}")
        fm.append("    status: open")       # 회고가 나중에 갱신: open|hit_target|stopped|watching
    fm.append("---")

    # --- 본문 ---
    body = [f"\n# 종목 추천 스냅샷 — {run_date}\n",
            "## 추천 종목\n",
            "| 종목 | 안정성 | 진입가 | 목표(기대) | 손절가 | 근거 |",
            "|------|--------|--------|-----------|--------|------|"]
    for p in picks:
        body.append(f"| {p.get('name','')} | {p.get('stability','')} | "
                    f"{p.get('entry','-')} | {p.get('target','-')} | {p.get('stop','-')} | "
                    f"{p.get('rationale','')} |")
    if excluded:
        body.append("\n## 탈락 표본 (회고용 — 빠뜨린 종목 검증)\n")
        for e in excluded:
            body.append(f"- {e.get('name','')}: {e.get('reason','')}")
    body.append("\n## ⚠️ 유의")
    body.append("- 웹 데이터 기반 참고 자료이며 투자 권유가 아니다. 실제 매매·주문은 사람이 결정한다.")
    return "\n".join(fm) + "\n" + "\n".join(body) + "\n"


def main() -> int:
    raw = open(sys.argv[1], encoding="utf-8").read() if len(sys.argv) > 1 else sys.stdin.read()
    data = json.loads(raw)
    run_date = data.get("run_date") or datetime.date.today().isoformat()
    path = _target_path(run_date)
    with open(path, "w", encoding="utf-8") as f:
        f.write(render(data))
    print(f"saved: {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
