#!/usr/bin/env python3
"""
worldcup 리포트 렌더러.

`data.json`(경기정보 에이전트 + 뉴스 에이전트의 결과가 병합된 파일)을 읽어
FIFA 월드컵 26 공식 홈페이지 감성의 모던 HTML 리포트를 생성하고 브라우저로 연다.

설계 원칙: 데이터(data.json)와 표현(이 스크립트의 템플릿)을 분리한다.
에이전트는 데이터만 갱신하고, 디자인은 여기서만 바뀐다.
"""

import html
import json
import os
import sys
import webbrowser

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
DATA_PATH = os.path.join(HERE, "data.json")
OUTPUT_PATH = os.path.join(PROJECT_ROOT, ".claude", "worldcup-report.html")


def esc(value):
    return html.escape(str(value), quote=True)


# ──────────────────────────────────────────────────────────────────────────
# CSS — FIFA World Cup 26 무드: 비비드한 마젠타→퍼플→시안 그라데이션 + 글래스모피즘
# ──────────────────────────────────────────────────────────────────────────
CSS = """
:root {
  --bg: #07071a;
  --bg-2: #0e0b2e;
  --magenta: #ff2d78;
  --purple: #7b2ff7;
  --cyan: #00d4ff;
  --lime: #c6ff00;
  --ink: #f4f3ff;
  --muted: #a9a6cf;
  --card: rgba(255, 255, 255, 0.045);
  --border: rgba(255, 255, 255, 0.10);
}
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background:
    radial-gradient(1100px 600px at 12% -8%, rgba(123,47,247,0.45), transparent 60%),
    radial-gradient(1000px 600px at 92% 0%, rgba(255,45,120,0.40), transparent 55%),
    radial-gradient(900px 700px at 60% 110%, rgba(0,212,255,0.25), transparent 60%),
    var(--bg);
  color: var(--ink);
  line-height: 1.55;
  -webkit-font-smoothing: antialiased;
  padding-bottom: 80px;
}
.wrap { max-width: 1120px; margin: 0 auto; padding: 0 24px; }
a { color: inherit; }

/* HERO */
.hero { padding: 64px 0 40px; position: relative; }
.hosts { display: flex; gap: 8px; font-size: 30px; letter-spacing: 4px; margin-bottom: 18px; }
.kicker {
  display: inline-flex; align-items: center; gap: 8px;
  font-size: 13px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase;
  color: var(--cyan); margin-bottom: 14px;
}
.kicker::before { content: ""; width: 8px; height: 8px; border-radius: 50%; background: var(--lime); box-shadow: 0 0 14px var(--lime); animation: pulse 1.6s infinite; }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: .3; } }
.title {
  font-family: 'Archivo', 'Inter', sans-serif;
  font-weight: 900; font-size: clamp(40px, 7vw, 84px); line-height: 0.95;
  letter-spacing: -2px; text-transform: uppercase;
  background: linear-gradient(92deg, #fff 10%, var(--magenta) 55%, var(--cyan) 100%);
  -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
}
.subtitle { margin-top: 18px; font-size: 18px; color: var(--muted); max-width: 640px; }
.meta-row { margin-top: 26px; display: flex; flex-wrap: wrap; gap: 10px; }
.chip {
  background: var(--card); border: 1px solid var(--border); border-radius: 999px;
  padding: 9px 16px; font-size: 13px; font-weight: 600; color: var(--ink);
  backdrop-filter: blur(8px);
}
.chip b { color: var(--cyan); }

/* STATS */
.stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-top: 44px; }
.stat {
  background: var(--card); border: 1px solid var(--border); border-radius: 20px;
  padding: 22px; backdrop-filter: blur(10px); position: relative; overflow: hidden;
}
.stat::after { content: ""; position: absolute; inset: 0 0 auto 0; height: 3px;
  background: linear-gradient(90deg, var(--magenta), var(--cyan)); }
.stat .v { font-family: 'Archivo', sans-serif; font-weight: 900; font-size: 32px; letter-spacing: -1px; }
.stat .l { font-size: 13px; color: var(--muted); margin-top: 6px; font-weight: 600; }
.stat .s { font-size: 12px; color: var(--cyan); margin-top: 8px; }

/* SECTION */
section { margin-top: 64px; }
.sec-head { display: flex; align-items: baseline; gap: 14px; margin-bottom: 24px; }
.sec-head h2 {
  font-family: 'Archivo', sans-serif; font-weight: 800; font-size: 26px;
  text-transform: uppercase; letter-spacing: -0.5px;
}
.sec-head .bar { flex: 1; height: 1px; background: linear-gradient(90deg, var(--border), transparent); }

/* NEWS */
.news { display: grid; grid-template-columns: repeat(2, 1fr); gap: 18px; }
.news-card {
  background: var(--card); border: 1px solid var(--border); border-radius: 20px;
  padding: 24px; backdrop-filter: blur(10px); transition: transform .18s, border-color .18s;
}
.news-card:hover { transform: translateY(-4px); border-color: rgba(255,45,120,0.5); }
.news-tag {
  display: inline-block; font-size: 11px; font-weight: 800; letter-spacing: 1px;
  text-transform: uppercase; padding: 5px 11px; border-radius: 999px;
  background: linear-gradient(90deg, var(--magenta), var(--purple)); color: #fff; margin-bottom: 14px;
}
.news-card h3 { font-size: 19px; font-weight: 800; line-height: 1.3; margin-bottom: 10px; }
.news-card p { font-size: 14.5px; color: var(--muted); }
.news-src { margin-top: 14px; font-size: 12px; color: var(--cyan); font-weight: 600; }

/* QUALIFIED */
.groups { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.gcard { background: var(--card); border: 1px solid var(--border); border-radius: 18px; padding: 18px; backdrop-filter: blur(10px); }
.gcard .gname {
  font-family: 'Archivo', sans-serif; font-weight: 900; font-size: 14px;
  color: var(--lime); letter-spacing: 2px; margin-bottom: 14px;
}
.team { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-top: 1px solid var(--border); }
.team:first-of-type { border-top: none; }
.team .flag { font-size: 22px; }
.team .tn { font-weight: 700; font-size: 14.5px; }
.team .rk {
  margin-left: auto; font-size: 11px; font-weight: 800; width: 22px; height: 22px;
  display: grid; place-items: center; border-radius: 50%;
}
.rk.r1 { background: linear-gradient(135deg, #ffd700, #ff9500); color: #2a1a00; }
.rk.r2 { background: rgba(255,255,255,0.18); color: #fff; }
.rk.r3 { background: rgba(0,212,255,0.22); color: var(--cyan); }
.team .note { flex-basis: 100%; font-size: 11.5px; color: var(--muted); padding-left: 32px; margin-top: -4px; }

/* SPOTLIGHT TABLE */
.spotlight { background: var(--card); border: 1px solid var(--border); border-radius: 22px; padding: 28px; backdrop-filter: blur(10px); }
.spotlight .stitle { font-family: 'Archivo', sans-serif; font-weight: 800; font-size: 22px; margin-bottom: 10px; }
.spotlight .ssum { color: var(--muted); font-size: 15px; margin-bottom: 22px; }
table { width: 100%; border-collapse: collapse; }
th, td { text-align: center; padding: 12px 8px; font-size: 14px; }
th { color: var(--muted); font-size: 12px; text-transform: uppercase; letter-spacing: 1px; font-weight: 700; border-bottom: 1px solid var(--border); }
td.team-cell { text-align: left; font-weight: 700; }
tbody tr { border-bottom: 1px solid var(--border); }
tbody tr.kr { background: linear-gradient(90deg, rgba(255,45,120,0.14), transparent); }
td .pts { font-family: 'Archivo', sans-serif; font-weight: 900; font-size: 16px; }
.badge { font-size: 11px; font-weight: 800; padding: 4px 10px; border-radius: 999px; }
.badge.go { background: rgba(198,255,0,0.18); color: var(--lime); }
.badge.out { background: rgba(255,255,255,0.08); color: var(--muted); }

/* GOLDEN BOOT */
.boot { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.scorer { background: var(--card); border: 1px solid var(--border); border-radius: 20px; padding: 24px; backdrop-filter: blur(10px); position: relative; }
.scorer .rank { font-family: 'Archivo', sans-serif; font-weight: 900; font-size: 14px; color: var(--muted); }
.scorer .pl { font-size: 20px; font-weight: 800; margin-top: 6px; display: flex; align-items: center; gap: 10px; }
.scorer .pl .flag { font-size: 26px; }
.scorer .goals { font-family: 'Archivo', sans-serif; font-weight: 900; font-size: 54px;
  background: linear-gradient(120deg, var(--lime), var(--cyan)); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; line-height: 1; margin: 14px 0 4px; }
.scorer .goals span { font-size: 16px; color: var(--muted); -webkit-text-fill-color: var(--muted); }
.scorer .nt { font-size: 13px; color: var(--muted); }
.scorer.lead { border-color: rgba(198,255,0,0.45); }

/* FOOTER */
footer { margin-top: 72px; padding-top: 28px; border-top: 1px solid var(--border); color: var(--muted); font-size: 13px; }
footer .srcs { display: flex; flex-wrap: wrap; gap: 8px 18px; margin-top: 12px; }
footer a { color: var(--cyan); text-decoration: none; }
footer a:hover { text-decoration: underline; }

@media (max-width: 860px) {
  .stats, .news, .groups, .boot { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 560px) {
  .stats, .news, .groups, .boot { grid-template-columns: 1fr; }
}
"""


def render_stats(stats):
    cells = []
    for s in stats:
        cells.append(
            f'<div class="stat"><div class="v">{esc(s.get("value",""))}</div>'
            f'<div class="l">{esc(s.get("label",""))}</div>'
            f'<div class="s">{esc(s.get("sub",""))}</div></div>'
        )
    return f'<div class="stats">{"".join(cells)}</div>'


def render_news(headlines):
    cards = []
    for h in headlines:
        cards.append(
            f'<div class="news-card"><span class="news-tag">{esc(h.get("tag",""))}</span>'
            f'<h3>{esc(h.get("title",""))}</h3><p>{esc(h.get("body",""))}</p>'
            f'<div class="news-src">{esc(h.get("source",""))}</div></div>'
        )
    return f'<section><div class="sec-head"><h2>오늘의 주요 소식</h2><span class="bar"></span></div><div class="news">{"".join(cards)}</div></section>'


def render_qualified(qualified):
    cards = []
    for g in qualified:
        rows = [f'<div class="gname">GROUP {esc(g.get("group",""))}</div>']
        for t in g.get("teams", []):
            rk = int(t.get("rank", 0) or 0)
            note = f'<div class="note">{esc(t["note"])}</div>' if t.get("note") else ""
            rows.append(
                f'<div class="team"><span class="flag">{esc(t.get("flag",""))}</span>'
                f'<span class="tn">{esc(t.get("name",""))}</span>'
                f'<span class="rk r{rk}">{rk}</span>{note}</div>'
            )
        cards.append(f'<div class="gcard">{"".join(rows)}</div>')
    return f'<section><div class="sec-head"><h2>32강 진출 현황</h2><span class="bar"></span></div><div class="groups">{"".join(cards)}</div></section>'


def render_spotlight(sp):
    if not sp:
        return ""
    rows = []
    for r in sp.get("table", []):
        is_kr = "🇰🇷" in str(r.get("flag", "")) or "한국" in str(r.get("team", "")) or "대한민국" in str(r.get("team", ""))
        status = str(r.get("status", ""))
        badge_cls = "go" if status in ("진출", "확정") else "out"
        rows.append(
            f'<tr class="{"kr" if is_kr else ""}">'
            f'<td class="team-cell">{esc(r.get("flag",""))} {esc(r.get("team",""))}</td>'
            f'<td>{esc(r.get("p",""))}</td><td>{esc(r.get("w",""))}</td><td>{esc(r.get("d",""))}</td>'
            f'<td>{esc(r.get("l",""))}</td><td>{esc(r.get("gf",""))}</td><td>{esc(r.get("ga",""))}</td>'
            f'<td><span class="pts">{esc(r.get("pts",""))}</span></td>'
            f'<td><span class="badge {badge_cls}">{esc(status)}</span></td></tr>'
        )
    return (
        '<section><div class="sec-head"><h2>스포트라이트</h2><span class="bar"></span></div>'
        f'<div class="spotlight"><div class="stitle">{esc(sp.get("title",""))}</div>'
        f'<div class="ssum">{esc(sp.get("summary",""))}</div>'
        '<table><thead><tr><th style="text-align:left">팀</th><th>경기</th><th>승</th><th>무</th><th>패</th>'
        '<th>득</th><th>실</th><th>승점</th><th>결과</th></tr></thead>'
        f'<tbody>{"".join(rows)}</tbody></table></div></section>'
    )


def render_boot(scorers):
    cards = []
    for i, s in enumerate(scorers):
        lead = " lead" if i == 0 else ""
        cards.append(
            f'<div class="scorer{lead}"><div class="rank">#{i+1}</div>'
            f'<div class="pl"><span class="flag">{esc(s.get("flag",""))}</span>{esc(s.get("name",""))}</div>'
            f'<div class="goals">{esc(s.get("goals",""))}<span> 골</span></div>'
            f'<div class="nt">{esc(s.get("team",""))} · {esc(s.get("note",""))}</div></div>'
        )
    return f'<section><div class="sec-head"><h2>골든부트 레이스</h2><span class="bar"></span></div><div class="boot">{"".join(cards)}</div></section>'


def build_html(data):
    t = data.get("tournament", {})
    hosts = " ".join(h.get("flag", "") for h in t.get("hosts", []))
    host_names = " · ".join(h.get("name", "") for h in t.get("hosts", []))

    srcs = " ".join(
        f'<a href="{esc(s.get("url","#"))}" target="_blank">{esc(s.get("name",""))}</a>'
        for s in data.get("sources", [])
    )

    return f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{esc(t.get("name","World Cup"))} · 현황 리포트</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Archivo:wght@800;900&family=Inter:wght@400;600;700;800&display=swap" rel="stylesheet">
<style>{CSS}</style>
</head>
<body>
<div class="wrap">
  <header class="hero">
    <div class="hosts">{hosts}</div>
    <div class="kicker">LIVE TRACKER · {esc(t.get("phase",""))}</div>
    <h1 class="title">{esc(t.get("name","World Cup"))}</h1>
    <p class="subtitle">{esc(t.get("edition",""))} · {esc(host_names)} 공동 개최. 전체 현황과 주요 소식을 한눈에.</p>
    <div class="meta-row">
      <span class="chip">📅 <b>{esc(t.get("dates",""))}</b></span>
      <span class="chip">⏭️ 다음 라운드 <b>{esc(t.get("next_phase",""))}</b></span>
      <span class="chip">🔄 업데이트 <b>{esc(data.get("updated_at",""))}</b></span>
    </div>
  </header>

  {render_stats(data.get("stats", []))}
  {render_news(data.get("headlines", []))}
  {render_qualified(data.get("qualified", []))}
  {render_spotlight(data.get("spotlight"))}
  {render_boot(data.get("golden_boot", []))}

  <footer>
    <div>본 리포트는 <b>경기정보 수집 에이전트</b> + <b>뉴스 조사 에이전트</b>가 병렬 수집한 데이터를 기반으로 자동 생성되었습니다.</div>
    <div class="srcs">출처: {srcs}</div>
  </footer>
</div>
</body>
</html>"""


def main():
    if not os.path.exists(DATA_PATH):
        print(f"[ERROR] 데이터 파일이 없습니다: {DATA_PATH}", file=sys.stderr)
        sys.exit(1)

    with open(DATA_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    html_out = build_html(data)
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        f.write(html_out)

    n_news = len(data.get("headlines", []))
    n_qual = sum(len(g.get("teams", [])) for g in data.get("qualified", []))
    print("✅ 월드컵 리포트 생성 완료")
    print(f"   • 업데이트 기준일 : {data.get('updated_at','?')}")
    print(f"   • 주요 소식       : {n_news}건")
    print(f"   • 32강 진출팀     : {n_qual}팀")
    print(f"   • 리포트 경로     : {OUTPUT_PATH}")

    opened = webbrowser.open("file://" + OUTPUT_PATH)
    print(f"   • 브라우저 열기   : {'성공' if opened else '실패 — 위 경로를 직접 여세요'}")


if __name__ == "__main__":
    main()
