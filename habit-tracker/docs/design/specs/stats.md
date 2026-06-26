# 통계 화면 명세

> 작성일: 2026-06-27 (검토 기반 신규 작성)
> 연관 정책: docs/planning/POLICY.md §4-2 (빈 상태)

---

## 목적

지난 7일과 30일 동안 얼마나 꾸준히 루틴을 실천했는지 한눈에 파악하여, "나는 잘 하고 있구나" 또는 "다시 시작하자"는 동기를 얻게 한다.

---

## 현재 구현 검토 — 발견된 문제

### 문제 1: 인라인 하드코딩 색상 — 디자인 시스템 위반 (심각)

**현재 코드 (stats/index.html:22):**
```html
th:style="'height: ' + ${entry.value} + '%; min-height: 4px; background-color: ' +
          (${entry.value == 100} ? '#198754' : (${entry.value >= 50} ? '#0d6efd' : '#dee2e6')) + ';'"
```

- `#198754`: Bootstrap success 원색. CSS 변수 `--color-success` (#22C55E)와 다름 → 불일치
- `#0d6efd`: Bootstrap primary 원색. CSS 변수 `--color-primary` (#3B82F6)와 다름 → 불일치
- `#dee2e6`: Bootstrap gray-300. CSS 변수 `--color-border` (#E5E7EB)와 다름 → 불일치

이 3개 색상은 디자인 시스템과 실제로 다른 색이다.
홈 화면의 완료율 표현과도 색상이 달라 앱 전체 색상 일관성이 깨진다.

**개선:** height는 인라인 style로 유지 (불가피), 색상은 CSS 클래스로 분리

### 문제 2: 0.65rem 폰트 크기 — 접근성 위반

**현재:** `style="font-size: 0.65rem;"` → 16px 기준 약 10.4px

WCAG 2.1 기준 최소 가독성 폰트 크기 권고: 12px (0.75rem).
10px대 텍스트는 고령 사용자, 낮은 해상도 환경에서 판독 불가능 수준.
7일 바 차트의 날짜 레이블과 퍼센트 값이 모두 이 크기 → 핵심 정보가 안 보인다.

**개선:** 최소 **11px (0.69rem)**, 여유 있으면 12px (0.75rem)

### 문제 3: 빈 상태 처리 없음 — POLICY §4-2 위반

루틴이 하나도 없거나, 앱을 방금 설치해서 기록이 없을 때 어떤 UI가 보이는지 정의되지 않았다.
현재 코드에서 `th:each="entry : ${rates7}"`가 빈 컬렉션일 경우 빈 div만 남는다.

### 문제 4: 7일 바 차트 aria 접근성 없음

바 차트 각 막대에 `role="img"` 또는 `aria-label`이 없어 스크린 리더에서 의미 없는 요소다.

### 문제 5: 페이지 맥락 없음

통계 화면에 제목만 있고 현재 보여주는 데이터 기간 요약이 없다.
예: "최근 7일 평균 완료율: 72%" 같은 한 줄 요약이 있으면 데이터 스캔 없이도 내 현황을 파악할 수 있다.

---

## 사용자 플로우

```
통계 진입
  → [요약 카드] 최근 7일 평균 완료율 한눈에 파악
  → [7일 바 차트] 날짜별 성과 추이 확인
  → [30일 목록] 특정 날짜 상세 완료율 확인
```

---

## 레이아웃

### 화면 1 — 전체 구조

```
┌──────────────────────────────────────────┐
│  [네비게이션 바]                          │
├──────────────────────────────────────────┤
│                                          │
│  통계                                    │  ← h4 fw-bold mb-1
│  최근 7일 평균 완료율: 72%               │  ← 요약 한 줄 (소형, text-muted)
│                                          │
│  ┌─ 최근 7일 완료율 ──────────────────┐  │
│  │                                    │  │
│  │  72%                               │  │  ← 7일 평균 뱃지 (선택적)
│  │  [막대][막대][막대][막대][막대][막대][막대] │
│  │  6/21  6/22  6/23  6/24  6/25  6/26  6/27 │  ← 최소 11px
│  └────────────────────────────────────┘  │
│                                          │
│  ┌─ 최근 30일 상세 ────────────────────┐  │
│  │  6월 27일 (토)  [████░░░░]  65%    │  │
│  │  6월 26일 (금)  [██████░░]  80%    │  │
│  │  ...                               │  │
│  └────────────────────────────────────┘  │
│                                          │
└──────────────────────────────────────────┘
```

---

### 화면 2 — 7일 바 차트 (개선 후)

**핵심 변경: 색상 클래스 분리**

```html
<!-- 개선 전 -->
<div th:style="'height: ' + ${entry.value} + '%; min-height: 4px; background-color: #198754'"></div>

<!-- 개선 후 -->
<div class="chart-bar flex-fill d-flex flex-column align-items-center">
  <!-- 퍼센트 레이블 -->
  <small class="chart-label mb-1"
         th:text="${entry.value} + '%'"></small>
  <!-- 막대 -->
  <div class="w-100 rounded-top chart-bar-fill"
       th:classappend="${entry.value == 100} ? 'bg-success' :
                       (${entry.value >= 50} ? 'bg-primary' : 'bg-secondary')"
       th:style="'height: ' + ${entry.value} + '%; min-height: 4px;'"
       role="img"
       th:attr="aria-label=${#temporals.format(entry.key, 'M월 d일')} + ' 완료율 ' + ${entry.value} + '%'">
  </div>
  <!-- 날짜 레이블 -->
  <small class="chart-label mt-1"
         th:text="${#temporals.format(entry.key, 'M/d')}"></small>
</div>
```

**CSS 추가 (style.css):**
```css
/* 7일 바 차트 레이블 */
.chart-label {
  font-size: 11px;          /* 접근성: 0.65rem → 11px */
  color: var(--color-text-2);
  line-height: 1;
}
```

---

### 화면 3 — 30일 목록 색상 수정

```html
<!-- 개선 전 -->
<div class="progress-bar"
     th:classappend="${entry.value == 100} ? 'bg-success' : (${entry.value >= 50} ? 'bg-primary' : 'bg-secondary')"
     th:style="'width: ' + ${entry.value} + '%'"></div>
```

30일 목록의 진행 바는 이미 CSS 클래스를 사용하고 있어서 하드코딩 문제 없음 (유지).
단, `bg-success / bg-primary / bg-secondary`가 Bootstrap 변수를 쓰는데,
디자인 시스템의 `--color-success / --color-primary`와 Bootstrap 변수가 일치하지 않는다.

**근본 해결책:** style.css에 Bootstrap 변수 오버라이드
```css
:root {
  --bs-success: #22C55E;  /* --color-success와 동일하게 */
  --bs-primary: #3B82F6;  /* --color-primary와 동일하게 */
}
```
이 한 줄 추가로 Bootstrap `bg-success`, `text-success` 등이 모두 디자인 시스템 색상을 따른다.

---

### 화면 4 — 빈 상태 (기록 없음)

```
┌────────────────────────────────────────────┐
│                                            │
│              [bi-bar-chart]                │  ← 아이콘 3rem, --color-text-3
│                                            │
│       아직 기록이 없어요                   │  ← 15px, --color-text-2
│  루틴을 체크하면 여기서 성과를 볼 수 있어요  │  ← 14px, --color-text-2
│                                            │
│           [오늘 루틴 확인하기 →]           │  ← btn btn-primary → /
│                                            │
└────────────────────────────────────────────┘
```

- 빈 상태 조건: `rates7`이 비어있거나 모든 값이 null/0인 경우
- 아이콘: `bi-bar-chart` (통계 페이지 맥락과 일치)
- CTA: "오늘 루틴 확인하기 →" → `/` (홈 화면으로)

---

## 컴포넌트 상태

### 7일 바 차트 막대 색상

| completionRate | CSS 클래스 | 색상 |
|---------------|-----------|------|
| 100% | bg-success | --color-success (#22C55E) |
| 50–99% | bg-primary | --color-primary (#3B82F6) |
| 0–49% | bg-secondary | Bootstrap secondary (#6B7280 계열) |
| 0% (min-height 4px) | bg-secondary | 최소 높이 유지로 날짜 레이블 정렬 |

### 30일 목록 진행 바 색상

7일 차트와 동일 기준 (현행 클래스 방식 유지, Bootstrap 변수 오버라이드로 색상 통일).

---

## 텍스트 가이드

### 페이지 헤더

- 제목: `"통계"`
- 부제: `"최근 7일 평균 완료율: {avg}%"` (7일 rates의 평균, 소수점 없이 반올림)

### 카드 헤더

- 7일 섹션: `"최근 7일 완료율"` (현행 유지)
- 30일 섹션: `"최근 30일 상세"` (현행 유지)

### 빈 상태

- 제목: `"아직 기록이 없어요"`
- 부제: `"루틴을 체크하면 여기서 성과를 볼 수 있어요"`
- CTA: `"오늘 루틴 확인하기 →"`

---

## 반응형 규칙

### 모바일 (< 576px)

- 7일 바 차트: 현행 `d-flex gap-2 align-items-end` 유지. 7개 막대가 좁아지면 날짜 레이블이 숫자(`6/27`)로 충분히 짧으므로 문제 없음
- 30일 목록: 날짜 텍스트 `min-width: 70px` 유지, 퍼센트 숫자 `min-width: 40px` 유지

### 데스크탑 (> 768px)

- 7일 차트 카드와 30일 목록 카드를 2열로 나란히 배치하는 방안 검토 가능 (현재는 단일 컬럼 유지)

---

## 디자인 시스템 업데이트 필요 사항

**style.css에 추가:**
```css
/* Bootstrap 변수 오버라이드 — 디자인 시스템 색상과 동기화 */
:root {
  --bs-success: #22C55E;
  --bs-success-rgb: 34, 197, 94;
  --bs-primary: #3B82F6;
  --bs-primary-rgb: 59, 130, 246;
}

/* 7일 바 차트 레이블 */
.chart-label {
  font-size: 11px;
  color: var(--color-text-2);
  line-height: 1;
}
```

이 변경이 적용되면 앱 전체에서 `bg-success`, `text-success`, `border-success` 등
Bootstrap 유틸리티가 디자인 시스템 색상을 따르게 된다.

---

## UX 체크리스트 (QA 테스트 케이스로 변환)

### 색상 일관성

- [ ] 7일 바 차트의 100% 막대 색상이 홈 화면 진행 바 100% 색상과 동일한가?
- [ ] 7일 바 차트에 하드코딩 색상값(#198754, #0d6efd, #dee2e6)이 없는가?
- [ ] 50% 이상 막대가 `bg-primary` 클래스를 사용하는가?

### 접근성

- [ ] 7일 바 차트 레이블 폰트가 11px 이상인가?
- [ ] 각 막대에 `role="img"`와 `aria-label`이 있는가? (예: "6월 27일 완료율 72%")

### 빈 상태

- [ ] 기록이 없을 때 빈 상태 메시지("아직 기록이 없어요")가 표시되는가?
- [ ] 빈 상태에 "오늘 루틴 확인하기 →" CTA가 있는가?

### 데이터 정확성

- [ ] 7일 차트 날짜가 오늘 포함 최근 7일인가?
- [ ] 30일 목록이 최신 날짜부터 역순으로 표시되는가?
