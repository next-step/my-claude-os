#!/usr/bin/env python3
"""
briefing 리포트 렌더러.

`data.json`(web-researcher 에이전트들의 결과가 각도별로 병합된 파일)을 읽어
차분한 에디토리얼 무드의 모던 HTML 브리핑 리포트를 만들고 브라우저로 연다.

설계 원칙: 데이터(data.json)와 표현(이 스크립트의 템플릿)을 분리한다.
에이전트는 데이터만 갱신하고, 디자인은 여기서만 바뀐다.
"""

import html
import json
import os
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
DATA_PATH = os.path.join(HERE, "data.json")
OUTPUT_PATH = os.path.join(PROJECT_ROOT, ".claude", "briefing-report.html")

STANCE = {
    "supporting": ("▲", "근거", "#1f9d55"),
    "refuting": ("▼", "반론", "#e0245e"),
    "neutral": ("■", "배경", "#5b8def"),
}


def esc(value):
    return html.escape(str(value), quote=True)


CSS = """
:root {
  --bg: #0c0f14;
  --bg-2: #12161f;
  --amber: #ffb545;
  --teal: #34d3c0;
  --ink: #f3f4f7;
  --muted: #9aa3b2;
  --card: rgba(255,255,255,0.04);
  --border: rgba(255,255,255,0.09);
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background:
    radial-gradient(1000px 520px at 8% -10%, rgba(255,181,69,0.16), transparent 60%),
    radial-gradient(900px 560px at 96% 4%, rgba(52,211,192,0.14), transparent 58%),
    var(--bg);
  color: var(--ink); line-height: 1.6; -webkit-font-smoothing: antialiased;
  padding-bottom: 80px;
}
.wrap { max-width: 900px; margin: 0 auto; padding: 0 24px; }

.hero { padding: 60px 0 30px; }
.kicker {
  display: inline-flex; align-items: center; gap: 8px;
  font-size: 12px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase;
  color: var(--amber); margin-bottom: 16px;
}
.kicker::before { content:""; width: 7px; height: 7px; border-radius: 50%;
  background: var(--teal); box-shadow: 0 0 12px var(--teal); }
.title {
  font-weight: 800; font-size: clamp(30px, 5.2vw, 52px); line-height: 1.08;
  letter-spacing: -1px;
  background: linear-gradient(92deg, #fff 20%, var(--amber) 100%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.meta-row { margin-top: 20px; display: flex; flex-wrap: wrap; gap: 10px; }
.chip { background: var(--card); border: 1px solid var(--border); border-radius: 999px;
  padding: 8px 15px; font-size: 12.5px; font-weight: 600; color: var(--ink); }
.chip b { color: var(--teal); }

.lead {
  margin-top: 30px; padding: 24px 26px; border-radius: 18px;
  background: var(--card); border: 1px solid var(--border);
  border-left: 3px solid var(--amber);
  font-size: 17px; color: #e8eaf0;
}
.lead h3 { font-size: 12px; letter-spacing: 2px; text-transform: uppercase;
  color: var(--muted); margin-bottom: 10px; font-weight: 700; }

.section { margin-top: 40px; }
.section > h2 {
  font-size: 21px; font-weight: 800; letter-spacing: -0.3px;
  display: flex; align-items: baseline; gap: 12px; margin-bottom: 6px;
}
.section > h2 .idx { color: var(--amber); font-size: 15px; font-weight: 700; }
.section > .sum { color: var(--muted); font-size: 15px; margin-bottom: 18px; }

.finding {
  background: var(--card); border: 1px solid var(--border); border-radius: 16px;
  padding: 18px 20px; margin-bottom: 12px;
}
.finding .top { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.tag { font-size: 11px; font-weight: 800; letter-spacing: .5px; padding: 3px 9px;
  border-radius: 999px; color: #06110f; }
.finding .point { font-weight: 700; font-size: 16px; }
.finding .detail { color: var(--muted); font-size: 14.5px; margin-top: 2px; }
.finding .src { font-size: 12px; color: var(--teal); margin-top: 8px; font-weight: 600; }

.sources { margin-top: 48px; padding-top: 22px; border-top: 1px solid var(--border); }
.sources h3 { font-size: 13px; letter-spacing: 1.5px; text-transform: uppercase;
  color: var(--muted); margin-bottom: 12px; }
.sources ul { list-style: none; display: grid; gap: 8px; }
.sources a { color: #cdd3df; text-decoration: none; font-size: 14px;
  border-bottom: 1px dashed var(--border); }
.sources a:hover { color: var(--amber); }

.foot { margin-top: 40px; text-align: center; color: var(--muted); font-size: 12.5px; }
"""


def render_finding(f):
    stance = f.get("stance", "neutral")
    icon, label, color = STANCE.get(stance, STANCE["neutral"])
    return f"""
      <div class="finding">
        <div class="top">
          <span class="tag" style="background:{color}">{icon} {esc(label)}</span>
          <span class="point">{esc(f.get('point',''))}</span>
        </div>
        <div class="detail">{esc(f.get('detail',''))}</div>
        <div class="src">— {esc(f.get('source','출처 미상'))}</div>
      </div>"""


def render_section(sec, i):
    findings = "".join(render_finding(f) for f in sec.get("findings", []))
    return f"""
    <div class="section">
      <h2><span class="idx">{i:02d}</span> {esc(sec.get('angle',''))}</h2>
      <div class="sum">{esc(sec.get('summary',''))}</div>
      {findings}
    </div>"""


def render_sources(sources):
    if not sources:
        return ""
    items = "".join(
        f'<li><a href="{esc(s.get("url","#"))}" target="_blank">{esc(s.get("name",""))} ↗</a></li>'
        for s in sources
    )
    return f'<div class="sources"><h3>출처</h3><ul>{items}</ul></div>'


def build_html(data):
    sections = data.get("sections", [])
    total_findings = sum(len(s.get("findings", [])) for s in sections)
    sec_html = "".join(render_section(s, i + 1) for i, s in enumerate(sections))
    return f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>브리핑 · {esc(data.get('topic',''))}</title>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&display=swap" rel="stylesheet"/>
<style>{CSS}</style>
</head>
<body>
  <div class="wrap">
    <div class="hero">
      <div class="kicker">Claude OS · 주제 브리핑</div>
      <div class="title">{esc(data.get('topic','브리핑'))}</div>
      <div class="meta-row">
        <span class="chip">📅 기준일 <b>{esc(data.get('updated_at','—'))}</b></span>
        <span class="chip">🧭 조사 각도 <b>{len(sections)}</b></span>
        <span class="chip">🔎 핵심 근거 <b>{total_findings}</b></span>
      </div>
    </div>

    <div class="lead">
      <h3>총평</h3>
      {esc(data.get('lead',''))}
    </div>

    {sec_html}

    {render_sources(data.get('sources', []))}

    <div class="foot">web-researcher 에이전트를 병렬 호출해 수집 · Claude OS /briefing</div>
  </div>
</body>
</html>"""


def main():
    with open(DATA_PATH, encoding="utf-8") as fp:
        data = json.load(fp)
    html_out = build_html(data)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as fp:
        fp.write(html_out)

    sections = data.get("sections", [])
    total = sum(len(s.get("findings", [])) for s in sections)
    print("✅ 브리핑 리포트 생성 완료")
    print(f"   • 주제       : {data.get('topic','—')}")
    print(f"   • 기준일     : {data.get('updated_at','—')}")
    print(f"   • 조사 각도  : {len(sections)}개")
    print(f"   • 핵심 근거  : {total}개")
    print(f"   • 리포트     : {OUTPUT_PATH}")
    try:
        webbrowser.open(f"file://{OUTPUT_PATH}")
        print("   • 브라우저   : 열림")
    except Exception as exc:  # noqa: BLE001
        print(f"   • 브라우저   : 자동 열기 실패({exc}) — 위 경로를 직접 열어 주세요")


if __name__ == "__main__":
    main()
