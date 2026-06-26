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
| 빈 상태 | `bi-inbox` |
| 삭제 | `bi-trash` |

---

## 반응형 브레이크포인트

```
모바일:  < 576px  → 1열, 풀 너비 카드, 터치 친화적
태블릿:  576–768px → 여백 증가
데스크탑: > 768px  → max-width 720px container
```

**터치 타겟**: 모든 탭/클릭 가능 요소는 최소 44×44px 확보.
