---
name: html
description: Render the analyzed/detailed paper content into a clean standalone HTML file using the mydesign design system. Use when the user wants the analysis turned into a viewable HTML page or report.
---

# /html — HTML 렌더링 스킬

분석 결과(`output/01_analysis.md`, `output/03_detail.md` 등)를 **`/mydesign`의 디자인**으로 단일 HTML 파일로 만듭니다.

## 입력
- `output/01_analysis.md`(필수), 있으면 `output/03_detail.md`, `output/04_code.md`도 포함.
- `output/design.css`(없으면 먼저 `/mydesign`을 실행해 생성).

## 절차
1. `output/design.css`가 없으면 `mydesign` 스킬을 먼저 수행한다.
2. 입력 마크다운들을 읽어 의미 단위(섹션)로 HTML로 변환한다.
3. 아래 골격에 맞춰 **자급식(self-contained)** `output/report.html`을 생성한다. CSS는 `<style>`로 인라인.
4. 목차(TOC), 상단 메타 배지, 섹션 카드, 코드 하이라이트 블록을 포함.

## HTML 골격
```html
<!doctype html><html lang="ko"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><논문 제목></title>
<style>/* output/design.css 내용 인라인 */</style>
</head><body><div class="wrap">
  <h1><논문 제목></h1>
  <p class="muted"><저자 · 연도 · 링크></p>
  <div><span class="badge">키워드1</span><span class="badge">키워드2</span></div>
  <nav class="toc card">… 목차 …</nav>
  <!-- 분석/상세/코드 섹션을 카드와 h2/h3로 -->
</div></body></html>
```

## 품질 기준
- 외부 의존성 없이 브라우저에서 바로 열려야 한다(인라인 CSS).
- 순백 배경 + 높은 대비 + 720px 본문폭(=mydesign 규약) 준수.
- 수식은 가능하면 KaTeX CDN(`<script>`)으로, 불가하면 코드블록으로 보존.
- 완료 후 `output/report.html` 경로를 보고하고, 사용자가 열도록 안내.
