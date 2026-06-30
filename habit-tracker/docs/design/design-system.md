# Design System — Habit Tracker

토스/당근 감성의 미니멀하고 따뜻한 디자인 시스템.
모든 에이전트는 이 파일을 기준으로 일관된 UI를 만든다.

---

## 색상 (Color)

```css
:root {
  /* 브랜드 */
  --color-primary:        #3B82F6;   /* 집중·액션 블루 */
  --color-primary-light:  #EFF6FF;   /* 블루 배경 틴트 */

  /* 상태 */
  --color-success:        #22C55E;   /* 완료·달성 */
  --color-success-light:  #F0FDF4;   /* 완료 배경 틴트 */
  --color-warning:        #F59E0B;   /* 주의·미완료 */
  --color-warning-light:  #FFFBEB;
  --color-danger:         #EF4444;   /* 오류·삭제 */
  --color-danger-light:   #FEF2F2;

  /* 중립 */
  --color-surface:        #FFFFFF;   /* 카드/컨테이너 배경 */
  --color-bg:             #F8FAFC;   /* 페이지 배경 */
  --color-border:         #E5E7EB;   /* 구분선 */

  /* 텍스트 */
  --color-text-1:         #111827;   /* 주요 텍스트 */
  --color-text-2:         #6B7280;   /* 보조 텍스트 */
  --color-text-3:         #D1D5DB;   /* 비활성/플레이스홀더 */

  /* XP & 레벨 시스템 */
  --color-xp-green-start: #4ADE80;   /* 프로그레스 바 그라데이션 시작 (라임) */
  --color-xp-green-end:   #16A34A;   /* 프로그레스 바 그라데이션 끝 (딥 그린) */
  --color-legend-gold:    #F59E0B;   /* 전설 레벨 골드 (--color-warning 재사용) */
  --color-legend-gold-bg: #FEF3C7;   /* 전설 카드 배경 틴트 */
  --color-legend-text:    #B45309;   /* "최고 레벨 달성" 텍스트 (골드 다크) */
  --color-banner-start:   #F59E0B;   /* 레벨업 배너 그라데이션 시작 (골드) */
  --color-banner-end:     #FB923C;   /* 레벨업 배너 그라데이션 끝 (오렌지) */
}
```

**사용 규칙**:
- CSS 변수로만 사용, 하드코딩 색상값 금지
- Bootstrap 유틸리티 색상과 병행 사용 시 변수 우선

---

## 타이포그래피 (Typography)

| 용도 | 크기 | 굵기 | 예시 |
|------|------|------|------|
| 페이지 제목 | 22–24px | 700 | "오늘의 루틴" |
| 섹션 제목 | 18–20px | 700 | "최근 7일" |
| 카드 제목 | 15–16px | 600 | 루틴 이름 |
| 본문 | 15px | 400 | 설명 텍스트 |
| 보조 | 13px | 400 | 날짜, 메타 정보 |
| 레이블 | 13px | 500 | 배지, 버튼 |

**폰트**: 시스템 기본 (Pretendard 또는 Apple SD Gothic Neo → 브라우저 기본)

---

## 간격 시스템 (Spacing — 8px Grid)

```
4px  (--space-xs)  : 아이콘-텍스트 사이
8px  (--space-sm)  : 인라인 요소 간격
16px (--space-md)  : 카드 패딩, 섹션 내부
24px (--space-lg)  : 섹션 간격, 카드 외부 간격
32px (--space-xl)  : 페이지 상단 여백
```

Bootstrap 5 간격 유틸리티 (`py-3` = 16px, `py-4` = 24px 등)와 매핑됨.

---

## 컴포넌트

### 루틴 카드 (Routine Card)

```
상태: 기본
┌─────────────────────────────────┐
│ [◯] 루틴 이름          [미완료] │  ← border-left: 4px --color-primary
│     설명 텍스트                  │
└─────────────────────────────────┘

상태: 완료
┌─────────────────────────────────┐
│ [✓] ~~루틴 이름~~       [완료]  │  ← border-left: 4px --color-success
│     설명 텍스트 (opacity 0.85)  │     text-decoration: line-through
└─────────────────────────────────┘
```

CSS 클래스:
- `.routine-card` — 기본
- `.routine-card.completed` — 완료 상태

### 진행률 표시 (Progress Bar)

```
[███████████░░░░░░░] 65%
```

- 100%: `--color-success`
- 50% 이상: `--color-primary`
- 50% 미만: Bootstrap `bg-secondary` (회색)

### 빈 상태 (Empty State)

```
        [아이콘 3rem]

    이 날 예정된 루틴이 없습니다.
      [루틴 추가하기 →]
```

항상 존재해야 함. 빈 목록에서 그냥 아무것도 없으면 안 된다.

### 배지 (Badge)

| 용도 | 배경 | 글자 |
|------|------|------|
| 완료 | `bg-success-subtle` | `text-success` |
| 미완료 | `bg-secondary-subtle` | `text-secondary` |
| 오늘 | `bg-primary-subtle` | `text-primary` |

---

## 모서리 (Border Radius)

| 요소 | 값 |
|------|-----|
| 카드 | 12–16px (`rounded-3`) |
| 버튼 | 8–12px (`rounded-2`) |
| 배지 | 999px (pill) |
| 입력 | 8px |

---

## 그림자 (Shadow)

```css
/* 카드 기본 */
box-shadow: 0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.04);

/* 카드 호버 */
box-shadow: 0 4px 12px rgba(0,0,0,0.10);
```

Bootstrap `shadow-sm` 사용 후 커스텀 호버 추가.

---

### XP 바 (xp-bar)

레벨 정보와 XP 진행률을 한 컴포넌트에 담는 홈 화면 전용 카드.

```
일반 상태 (Lv.1–6):
┌─────────────────────────────────────────┐
│ [이모지 2rem]  [레벨명] Lv.[N]           │  ← .xp-bar-header
│                                         │
│ [████████████░░░░░░░░░░░░░░░░░░░░░░░░] │  ← .xp-progress-track + .xp-progress-fill
│                                [N / M XP]│  ← .xp-text
└─────────────────────────────────────────┘
  border-left: 4px solid [레벨별 색상]

만렙 상태 (Lv.7 전설):
╔═════════════════════════════════════════╗
║ [⚡ 2.5rem]  전설 Lv.7                  ║  ← .xp-bar-header
║          ★ 최고 레벨 달성 ★              ║  ← .xp-legend-text (shimmer 애니)
╚═════════════════════════════════════════╝
  border: 2px solid var(--color-legend-gold)
  background: linear-gradient(135deg, #FFFBEB, var(--color-legend-gold-bg))
```

CSS 클래스:
- `.xp-bar-container` — 전체 카드 래퍼
- `.xp-bar-header` — 이모지 + 레벨명 + Lv.N 행
- `.xp-level-emoji` — 이모지 (font-size: 2rem)
- `.xp-progress-track` — 바 배경 트랙 (height: 8px, border-radius: 999px)
- `.xp-progress-fill` — fill 영역, `background: linear-gradient(90deg, var(--color-xp-green-start), var(--color-xp-green-end))`, `transition: width 0.4s ease-out`
- `.xp-text` — "N / M XP" 보조 텍스트 (13px, --color-text-2, 우측 정렬)
- `.xp-bar-legend` — 만렙 전용 shimmer 카드 (일반 상태 대체)
- `.xp-legend-text` — "★ 최고 레벨 달성 ★" (font-weight: 700, color: var(--color-legend-text))

레벨별 좌측 보더 색상 매핑:
| 레벨 | 색상 값 |
|------|--------|
| 1 🌱 | `#86EFAC` |
| 2 🌿 | `#4ADE80` |
| 3 🌳 | `#22C55E` |
| 4 🌲 | `#16A34A` |
| 5 👨‍🌾 | `#15803D` |
| 6 🏕️ | `#14532D` |
| 7 ⚡ | `var(--color-legend-gold)` (전체 테두리로 대체) |

---

### 레벨업 배너 (level-up-banner)

레벨업 발생 시 홈 화면 최상단에 조건부 표시되는 축하 배너.

```
┌─────────────────────────────────────────────────────────┐
│  [이전이모지] → [새이모지]  [새레벨명]에 도달했어요!   [✕] │
└─────────────────────────────────────────────────────────┘
background: linear-gradient(90deg, var(--color-banner-start), var(--color-banner-end))
color: #FFFFFF
```

CSS 클래스:
- `.level-up-banner` — 배너 전체, `transform: translateY(-100%)` 초기값
- `.level-up-banner.visible` — `transform: translateY(0)`, `transition: transform 0.3s ease-out`
- `.level-up-banner.fading` — `opacity: 0`, `transition: opacity 0.5s ease-in`
- `.banner-close` — 닫기 버튼, 최소 44×44px 터치 타겟

동작 규칙:
- Thymeleaf `th:if="${leveledUp}"` 로 DOM 자체를 조건부 렌더
- 5초(4.5s 후 fade 시작 + 0.5s fade) 후 자동 DOM 제거
- 닫기 클릭 시 타이머 취소 후 즉시 제거

---

## Bootstrap 변수 오버라이드

Bootstrap 기본 색상(`bg-success`, `text-primary` 등)이 디자인 시스템 색상과 일치하도록 오버라이드한다.
이 선언을 `style.css` `:root`에 추가하면 Bootstrap 유틸리티 클래스와 디자인 시스템 색상이 자동 동기화된다.

```css
:root {
  --bs-success:     #22C55E;   /* --color-success와 동일 */
  --bs-success-rgb: 34, 197, 94;
  --bs-primary:     #3B82F6;   /* --color-primary와 동일 */
  --bs-primary-rgb: 59, 130, 246;
}
```

**적용 배경:**
Bootstrap 기본 `--bs-success`(#198754)와 디자인 시스템 `--color-success`(#22C55E)가 달라서
통계 화면 등에서 하드코딩 색상값이 사용되는 문제가 발생했다.
이 오버라이드 한 줄로 `bg-success`, `text-success`, `border-success` 등 전체가 통일된다.

---

### 진행 카운트 텍스트 (completion-count)

홈 화면 진행 바 옆에 표시되는 "N/M 완료" 텍스트.

```
[████████░░░░]  3/5 완료
```

CSS 클래스: `.completion-count`
```css
.completion-count {
  font-size: 13px;
  color: var(--color-text-2);
  white-space: nowrap;    /* 좁은 화면에서 줄바꿈 방지 */
  flex-shrink: 0;
}
```

---

### 날짜 네비게이션 버튼 (nav-date-btn)

기록 화면의 이전/다음 날짜 이동 버튼. 44px 터치 타겟 확보.

```
[  <  ]   2026년 6월 27일 (토)   [  >  ]
```

CSS 클래스: `.nav-date-btn`
```css
.nav-date-btn {
  min-width: 44px;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--space-md);
}
```

비활성(미래 날짜 이동 불가) 시 `aria-disabled="true"` + `tabindex="-1"` 추가.

---

### 바 차트 레이블 (chart-label)

통계 화면의 7일 바 차트 날짜/퍼센트 레이블.

CSS 클래스: `.chart-label`
```css
.chart-label {
  font-size: 11px;           /* 접근성: 최소 11px (WCAG 권고 기준) */
  color: var(--color-text-2);
  line-height: 1;
}
```

**설계 이유:** 0.65rem(약 10.4px)은 WCAG 가독성 기준 미달.
7개 막대가 좁은 공간에 있더라도 최소 11px을 유지한다.

---

## 아이콘

Bootstrap Icons (CDN 로드됨) 사용. 사이즈: `fs-4` (1.5rem) 또는 `fs-5` (1.25rem).

| 용도 | 아이콘 클래스 |
|------|-------------|
| 체크 미완료 | `bi-circle` |
| 체크 완료 | `bi-check-circle-fill` |
| 이전 날짜 | `bi-chevron-left` |
| 다음 날짜 | `bi-chevron-right` |
| 통계 | `bi-bar-chart` |
| 루틴 | `bi-list-check` |
| 빈 상태 — 루틴 없음 | `bi-calendar-plus` |
| 빈 상태 — 기록 없음 | `bi-calendar-x` |
| 빈 상태 — 통계 없음 | `bi-bar-chart` |
| 빈 상태 — `bi-inbox` 사용 금지 | 이메일 맥락 아이콘, Habit Tracker와 맥락 불일치 |
| 삭제(그만하기) | `bi-trash` (아이콘만 사용 시) |
| 루틴 관리 아이템 삭제 | 아이콘 사용 금지, 텍스트 버튼 "그만하기" 사용 |

---

## 반응형 브레이크포인트

```
모바일:  < 576px  → 1열, 풀 너비 카드, 터치 친화적
태블릿:  576–768px → 여백 증가
데스크탑: > 768px  → max-width 720px container
```

**터치 타겟**: 모든 탭/클릭 가능 요소는 최소 44×44px 확보.
