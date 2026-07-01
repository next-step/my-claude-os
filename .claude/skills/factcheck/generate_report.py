#!/usr/bin/env python3
"""
factcheck 판정 카드 렌더러.

`data.json`(web-researcher 찬성/반대 에이전트 결과 + 종합 판정이 담긴 파일)을 읽어
판정 배지가 큼직한 HTML 팩트체크 카드를 만들고 브라우저로 연다.

설계 원칙: 판정/데이터는 스킬이, 카드 디자인은 이 스크립트가 담당(데이터·표현 분리).
"""

import html
import json
import os
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
DATA_PATH = os.path.join(HERE, "data.json")
OUTPUT_PATH = os.path.join(PROJECT_ROOT, ".claude", "factcheck-report.html")

# 판정 → (색상, 이모지). 매칭 안 되면 '근거 불충분' 취급.
VERDICTS = {
    "사실": ("#1f9d55", "✓"),
    "대체로 사실": ("#3bb273", "✓"),
    "부분적 사실": ("#e0a52e", "◐"),
    "근거 불충분": ("#8a94a6", "?"),
    "대체로 거짓": ("#e0683e", "✗"),
    "거짓": ("#e0245e", "✗"),
}
CONF_LABEL = {"high": "높음", "medium": "보통", "low": "낮음"}


def esc(value):
    return html.escape(str(value), quote=True)


CSS = """
:root {
  --bg: #0b0d12; --ink: #f2f4f8; --muted: #98a1b3;
  --card: rgba(255,255,255,0.04); --border: rgba(255,255,255,0.09);
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: radial-gradient(1000px 560px at 50% -12%, rgba(120,130,160,0.18), transparent 62%), var(--bg);
  color: var(--ink); line-height: 1.6; -webkit-font-smoothing: antialiased; padding-bottom: 70px;
}
.wrap { max-width: 820px; margin: 0 auto; padding: 0 24px; }
.kicker { padding: 52px 0 0; font-size: 12px; font-weight: 700; letter-spacing: 2px;
  text-transform: uppercase; color: var(--muted); }

.verdict-card {
  margin-top: 20px; border-radius: 24px; padding: 34px 32px;
  border: 1px solid var(--border); position: relative; overflow: hidden;
}
.verdict-card .glow { position: absolute; inset: 0; opacity: .16; z-index: 0; }
.verdict-card > * { position: relative; z-index: 1; }
.claim-label { font-size: 12px; letter-spacing: 2px; text-transform: uppercase;
  color: var(--muted); margin-bottom: 10px; font-weight: 700; }
.claim { font-size: clamp(21px, 3.6vw, 30px); font-weight: 800; line-height: 1.25;
  letter-spacing: -0.5px; }
.badge-row { margin-top: 24px; display: flex; flex-wrap: wrap; align-items: center; gap: 14px; }
.badge {
  display: inline-flex; align-items: center; gap: 10px; padding: 12px 22px;
  border-radius: 999px; font-size: 22px; font-weight: 900; color: #fff;
  letter-spacing: -0.3px;
}
.badge .mark { font-size: 24px; }
.conf { font-size: 13px; color: var(--muted); font-weight: 600; }
.conf b { color: var(--ink); }

.rationale { margin-top: 22px; font-size: 16px; color: #e6e9f0;
  background: var(--card); border: 1px solid var(--border);
  border-radius: 14px; padding: 18px 20px; }

.cols { margin-top: 34px; display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 680px) { .cols { grid-template-columns: 1fr; } }
.col h3 { font-size: 13px; letter-spacing: 1px; text-transform: uppercase; margin-bottom: 12px;
  display: flex; align-items: center; gap: 8px; }
.col.pro h3 { color: #4fd08a; }
.col.con h3 { color: #f26d8f; }
.ev { background: var(--card); border: 1px solid var(--border); border-radius: 14px;
  padding: 15px 17px; margin-bottom: 10px; }
.ev.pro { border-left: 3px solid #1f9d55; }
.ev.con { border-left: 3px solid #e0245e; }
.ev .p { font-weight: 700; font-size: 14.5px; }
.ev .d { color: var(--muted); font-size: 13.5px; margin-top: 4px; }
.ev .s { font-size: 11.5px; color: #7f8aa0; margin-top: 7px; font-weight: 600; }
.empty { color: var(--muted); font-size: 14px; padding: 8px 2px; }

.sources { margin-top: 40px; padding-top: 20px; border-top: 1px solid var(--border); }
.sources h3 { font-size: 12.5px; letter-spacing: 1.5px; text-transform: uppercase;
  color: var(--muted); margin-bottom: 10px; }
.sources ul { list-style: none; display: grid; gap: 7px; }
.sources a { color: #cdd3df; text-decoration: none; font-size: 14px;
  border-bottom: 1px dashed var(--border); }
.foot { margin-top: 36px; text-align: center; color: var(--muted); font-size: 12.5px; }
"""


def render_evidence(items, kind):
    if not items:
        return '<div class="empty">확인된 근거 없음.</div>'
    out = []
    for e in items:
        out.append(f"""
        <div class="ev {kind}">
          <div class="p">{esc(e.get('point',''))}</div>
          <div class="d">{esc(e.get('detail',''))}</div>
          <div class="s">— {esc(e.get('source','출처 미상'))}</div>
        </div>""")
    return "".join(out)


def render_sources(sources):
    if not sources:
        return ""
    items = "".join(
        f'<li><a href="{esc(s.get("url","#"))}" target="_blank">{esc(s.get("name",""))} ↗</a></li>'
        for s in sources
    )
    return f'<div class="sources"><h3>출처</h3><ul>{items}</ul></div>'


def build_html(data):
    verdict = data.get("verdict", "근거 불충분")
    color, mark = VERDICTS.get(verdict, VERDICTS["근거 불충분"])
    conf = data.get("confidence", "low")
    conf_ko = CONF_LABEL.get(conf, conf)
    return f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>팩트체크 · {esc(data.get('claim',''))[:40]}</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800;900&display=swap" rel="stylesheet"/>
<style>{CSS}</style>
</head>
<body>
  <div class="wrap">
    <div class="kicker">Claude OS · 팩트체크</div>
    <div class="verdict-card">
      <div class="glow" style="background:{color}"></div>
      <div class="claim-label">검증 대상 주장</div>
      <div class="claim">“{esc(data.get('claim','—'))}”</div>
      <div class="badge-row">
        <span class="badge" style="background:{color}">
          <span class="mark">{mark}</span> {esc(verdict)}
        </span>
        <span class="conf">신뢰도 <b>{esc(conf_ko)}</b> · 기준일 {esc(data.get('updated_at','—'))}</span>
      </div>
      <div class="rationale">{esc(data.get('rationale',''))}</div>
    </div>

    <div class="cols">
      <div class="col pro">
        <h3>▲ 주장을 뒷받침하는 근거</h3>
        {render_evidence(data.get('supporting', []), 'pro')}
      </div>
      <div class="col con">
        <h3>▼ 주장을 반박하는 근거</h3>
        {render_evidence(data.get('refuting', []), 'con')}
      </div>
    </div>

    {render_sources(data.get('sources', []))}

    <div class="foot">web-researcher 에이전트를 찬·반 병렬 호출해 적대적으로 검증 · Claude OS /factcheck</div>
  </div>
</body>
</html>"""


def main():
    with open(DATA_PATH, encoding="utf-8") as fp:
        data = json.load(fp)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as fp:
        fp.write(build_html(data))

    print("✅ 팩트체크 판정 카드 생성 완료")
    print(f"   • 주장     : {data.get('claim','—')}")
    print(f"   • 판정     : {data.get('verdict','—')}  (신뢰도 {CONF_LABEL.get(data.get('confidence',''), data.get('confidence','—'))})")
    print(f"   • 근거     : 찬성 {len(data.get('supporting', []))} · 반박 {len(data.get('refuting', []))}")
    print(f"   • 리포트   : {OUTPUT_PATH}")
    try:
        webbrowser.open(f"file://{OUTPUT_PATH}")
        print("   • 브라우저 : 열림")
    except Exception as exc:  # noqa: BLE001
        print(f"   • 브라우저 : 자동 열기 실패({exc}) — 위 경로를 직접 열어 주세요")


if __name__ == "__main__":
    main()
