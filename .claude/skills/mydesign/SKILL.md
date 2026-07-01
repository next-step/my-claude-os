---
name: mydesign
description: Provides a clean, white, high-readability design system (CSS tokens, typography, components) for rendering content so it is easy on the eyes. Use whenever content needs to be styled or turned into a visually clean document, especially before generating HTML.
---

# /mydesign — 디자인 시스템 스킬

"깔끔한 하얀색 스타일 + 눈에 잘 보이는 가독성"을 보장하는 **재사용 디자인 토큰/CSS**를 제공합니다.
`/html` 스킬이 이 디자인을 가져다 최종 HTML을 렌더링합니다.

## 디자인 원칙
- **배경**: 순백(#FFFFFF) 기반, 카드/섹션은 아주 옅은 회색으로 구분.
- **가독성**: 본문 16~18px, 줄간격 1.7, 본문 폭 720px 제한.
- **대비**: 본문 텍스트 #1A1A1A, 강조는 단일 액센트 컬러(#2563EB).
- **여백**: 넉넉한 패딩, 시각적 잡음 최소화.

## 산출물
이 스킬이 호출되면 아래 CSS를 `output/design.css` 로 저장한다(이미 있으면 재사용).

```css
:root{
  --bg:#FFFFFF; --surface:#F7F8FA; --border:#E5E7EB;
  --text:#1A1A1A; --muted:#5B6470; --accent:#2563EB; --accent-soft:#EFF4FF;
  --radius:14px; --maxw:760px;
  --font: -apple-system,"Segoe UI",Roboto,"Noto Sans KR",sans-serif;
}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font-family:var(--font);
  font-size:17px;line-height:1.75;-webkit-font-smoothing:antialiased}
.wrap{max-width:var(--maxw);margin:0 auto;padding:56px 24px 96px}
h1{font-size:2rem;line-height:1.25;margin:0 0 .3em;letter-spacing:-.02em}
h2{font-size:1.4rem;margin:2.2em 0 .6em;padding-bottom:.3em;border-bottom:2px solid var(--accent-soft)}
h3{font-size:1.1rem;margin:1.6em 0 .4em;color:var(--accent)}
p,li{color:var(--text)}
.muted{color:var(--muted)}
.card{background:var(--surface);border:1px solid var(--border);
  border-radius:var(--radius);padding:20px 24px;margin:18px 0}
.badge{display:inline-block;background:var(--accent-soft);color:var(--accent);
  font-size:.8rem;font-weight:600;padding:3px 10px;border-radius:999px;margin:2px}
code,pre{font-family:"SF Mono",Consolas,monospace;font-size:.92em}
pre{background:#0F172A;color:#E2E8F0;padding:18px;border-radius:10px;overflow:auto;line-height:1.5}
:not(pre)>code{background:var(--accent-soft);color:var(--accent);padding:1px 6px;border-radius:6px}
blockquote{margin:16px 0;padding:8px 18px;border-left:4px solid var(--accent);
  background:var(--accent-soft);color:#1e3a8a;border-radius:0 8px 8px 0}
table{width:100%;border-collapse:collapse;margin:16px 0}
th,td{border:1px solid var(--border);padding:10px 12px;text-align:left}
th{background:var(--surface)}
a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}
.toc{font-size:.95rem}.toc a{color:var(--muted)}
```

## 사용 규약
- 모든 HTML 산출물은 이 CSS를 `<style>` 인라인 또는 `<link>`로 포함해야 한다.
- 색을 추가할 일이 생기면 위 토큰(`--accent` 등)을 변형해 일관성 유지.
- 산출 후 `output/design.css` 경로를 보고한다.
