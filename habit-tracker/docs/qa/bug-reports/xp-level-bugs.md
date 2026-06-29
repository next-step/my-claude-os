# 버그 리포트: XP & 레벨 시스템 + UI 개선

> 작성일: 2026-06-29
> 분석 대상: feat(xp) b78987e, feat(ui) 78e4b93
> 분석 방법: 코드 정적 분석 (실행 없이)

---

## P1 — 즉시 수정 필요

### BUG-01: 만렙(LEGEND) 분기 조건 항상 false — 만렙 UI 미표시

**심각도:** P1
**영향 범위:** totalXp >= 15000인 사용자 전원
**대상 파일:** `templates/index.html`

**원인 분석:**

```html
<!-- templates/index.html line 35 -->
<div th:unless="${userLevel.name == 'LEGEND'}"

<!-- templates/index.html line 65 -->
<div th:if="${userLevel.name == 'LEGEND'}"
```

Thymeleaf에서 `${userLevel.name}`은 `Level.getName()`을 호출한다. `getName()`은 아래와 같이 정의되어 있다.

```java
// Level.java line 35
public String getName() { return name; }
// name 필드는 "전설", "자연인" 등 한국어 이름
```

따라서 `${userLevel.name}`의 반환값은 "전설"이고, "LEGEND"와 비교하면 항상 false다.

**재현 방법:**
1. DB에서 `update user_stats set total_xp = 15000 where id=1` 세팅
2. 홈 화면 접속
3. 만렙 골드 shimmer 카드가 표시되지 않고 일반 XP 바가 표시됨
4. 일반 XP 바에서 "xpInCurrentLevel / 0 XP" 이상한 수치 표시 (xpToNextLevel=0이므로)

**증상:**
- 만렙 shimmer 골드 카드 미표시
- "★ 최고 레벨 달성 ★" 텍스트 미표시
- 일반 XP 바에서 분모가 0 XP 표시 ("N / 0 XP")
- 레벨업 배너도 레벨7에서 표시되지 않아야 하는데, 이 버그로 인해 다른 동작 가능성

**수정 방법 (3가지 중 선택):**

옵션 A - enum 직접 비교 (권장):
```html
<div th:unless="${userLevel == T(com.habit.tracker.domain.Level).LEGEND}">
<div th:if="${userLevel == T(com.habit.tracker.domain.Level).LEGEND}">
```

옵션 B - 레벨 번호 비교:
```html
<div th:unless="${userLevel.number == 7}">
<div th:if="${userLevel.number == 7}">
```

옵션 C - Java enum name() 메서드 명시적 호출:
```html
<!-- Thymeleaf에서 메서드 호출: 괄호 사용 -->
<div th:unless="${userLevel.name() == 'LEGEND'}">
```
단, 옵션 C는 `name`이 필드와 메서드로 중복되어 혼란 유발 가능성 있으므로 비권장.

**→ 백엔드/프론트:** `templates/index.html` line 35, 65 수정 필요

---

## P2 — 우선 수정 권장

### BUG-02: XP 수치 텍스트 분모 의미 불일치

**심각도:** P2
**영향 범위:** Lv.1~6 전체 사용자 (홈 화면 XP 수치 오표시)
**대상 파일:** `templates/index.html`, `UserStatsService.java`

**원인 분석:**

디자인 명세(`docs/design/specs/xp-level.md`)의 예시:
- `"80 / 200 XP"` (totalXp=80일 때, 분모=레벨 구간 크기 200)
- `"450 / 900 XP"` (분모=TREE 레벨 구간 크기 900)

`getXpToNextLevel()`의 반환값:
```java
// UserStatsService.java line 77
return current.nextLevel().getMinXp() - totalXp;
// totalXp=80 → 200 - 80 = 120 (남은 XP)
```

실제 표시:
```html
<!-- templates/index.html line 55~57 -->
<span th:text="${xpInCurrentLevel}"></span> / <span th:text="${xpToNextLevel}"></span> XP
```
totalXp=80이면 "80 / 120 XP" 표시 (디자인 의도 "80 / 200 XP"와 다름)

**사용자 영향:**
- 프로그레스 바(40%)와 수치 텍스트("80/120 XP") 간 직관적 연결 불가
- "80/200 XP"이면 40% 직관 가능, "80/120 XP"이면 40%와 연결 어려움
- 분모가 계속 바뀌어(남은 XP 감소) 사용자가 목표 XP를 파악하기 어려움

**수정 방법:**

`UserStatsService`에 레벨 구간 크기 반환 메서드 추가:
```java
@Transactional(readOnly = true)
public int getXpLevelRange() {
    Level current = Level.fromXp(getOrCreate().getTotalXp());
    if (current == Level.LEGEND) return 0;
    return current.nextLevel().getMinXp() - current.getMinXp();
}
```

`HomeController`에서 모델 속성 변경:
```java
model.addAttribute("xpLevelRange", userStatsService.getXpLevelRange());
```

`index.html` 수치 텍스트 변경:
```html
<span th:text="${xpInCurrentLevel}"></span> / <span th:text="${xpLevelRange}"></span> XP
```

**→ 백엔드:** UserStatsService, HomeController 수정
**→ 프론트:** templates/index.html line 56

---

### BUG-03: 과거 날짜 전체 완료 시 오늘 보너스 XP 오지급

**심각도:** P2
**영향 범위:** /history 화면에서 직접 POST 요청 가능한 경우 (UI는 차단, 서버 미검증)
**대상 파일:** `RoutineService.java`, `UserStatsService.java`

**원인 분석:**

`RoutineService.toggleCheck()`의 보너스 조건:
```java
// RoutineService.java line 112~119
List<TodayRoutineDto> todayRoutines = getRoutinesForDate(date);  // date 파라미터 사용
boolean allCompleted = !todayRoutines.isEmpty()
        && todayRoutines.stream().allMatch(TodayRoutineDto::isCompleted);

if (allCompleted && !userStatsService.isTodayBonusAwarded()) {  // 오늘 기준 체크
    userStatsService.awardXp(50);
    userStatsService.markTodayBonusAwarded();  // lastBonusDate = LocalDate.now()
}
```

`isTodayBonusAwarded()`는 `LocalDate.now()`와 비교:
```java
// UserStatsService.java line 101~103
public boolean isTodayBonusAwarded() {
    return LocalDate.now().equals(lastBonus);
}
```

**문제 시나리오:**
1. 어제 날짜의 루틴을 전부 POST /check로 완료 처리
2. `getRoutinesForDate(어제)` → 모두 완료 → allCompleted=true
3. `isTodayBonusAwarded()` → lastBonusDate != 오늘 → false
4. +50 XP 지급 + `markTodayBonusAwarded()` → lastBonusDate = 오늘
5. 이후 오늘 루틴을 실제로 전부 완료해도 → `isTodayBonusAwarded()` = true → 오늘 보너스 못 받음

**추가 문제:**
- 어제 날짜 기준 보너스인데 lastBonusDate가 오늘로 기록됨 (날짜 의미 불일치)

**현재 상태:** UI 레이어에서 과거 날짜 체크 버튼을 `disabled` 처리하여 일반 사용자는 재현 불가. 그러나 서버 레이어 보호 없음.

**수정 방법 (2가지):**

옵션 A - 서버에서 미래/과거 날짜 보너스 제외:
```java
// toggleCheck에서 date가 오늘인 경우에만 보너스 체크
if (date.equals(LocalDate.now()) && allCompleted && !userStatsService.isTodayBonusAwarded()) {
```

옵션 B - lastBonusDate를 해당 날짜(date)로 저장 + 날짜별 보너스 지급:
전체 설계 변경 필요, 범위 큼.

**→ 백엔드:** RoutineService.java line 116 수정 권장

---

## P3 — 다음 스프린트 수정

### BUG-04: 통계 화면 빈 상태 표시 불가

**심각도:** P3
**영향 범위:** 신규 사용자 (루틴 0개 상태) 통계 접속 시
**대상 파일:** `stats/index.html`, `RoutineService.java`

**원인 분석:**

`stats/index.html`의 빈 상태 조건:
```html
<div th:if="${rates7 == null or rates7.isEmpty()}" class="text-center py-5">
```

`getDailyCompletionRates(7)` 반환값:
```java
// RoutineService.java line 138~153
Map<LocalDate, Integer> result = new LinkedHashMap<>();
for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
    List<TodayRoutineDto> routines = getRoutinesForDate(d);
    if (routines.isEmpty()) {
        result.put(d, 0);  // 루틴이 없어도 0%로 항목 추가
    } else { ... }
}
return result;  // 항상 7개 항목 반환
```

`rates7`은 루틴이 0개여도 7개의 `{날짜: 0}` 항목을 반환하므로 절대 `isEmpty()`가 아니다.

**사용자 영향:**
- 루틴이 없는 신규 사용자가 통계 화면에 접속하면 빈 상태 화면 대신 0% 막대 7개 표시
- "아직 기록이 없어요" 메시지를 볼 수 없음
- "오늘 루틴 확인하기" CTA도 표시 안 됨 → 온보딩 흐름 차단

**수정 방법:**

빈 상태 조건을 "활성 루틴이 0개인가"로 변경:
- `HomeController.stats()`에서 활성 루틴 수 모델 추가
- 또는 rates7의 모든 값이 0인 경우를 다르게 처리

단기 수정: `RoutineService.findAllActive().isEmpty()`를 모델에 추가해서 템플릿에서 분기.

**→ 백엔드:** HomeController.stats() 모델 속성 추가
**→ 프론트:** stats/index.html 빈 상태 조건 변경

---

### BUG-05: history 화면에서 미래 날짜로 무한 이동 가능

**심각도:** P3
**영향 범위:** history 화면 사용자
**대상 파일:** `templates/history/index.html`, `HomeController.java`

**원인 분석:**

`isToday` 변수 정의:
```java
// HomeController.java line 83
model.addAttribute("isToday", target.equals(LocalDate.now()));
```

과거 날짜와 미래 날짜 모두 `isToday = false`. "다음" 버튼 비활성화 조건:
```html
<!-- history/index.html line 28~30 -->
th:classappend="${isToday} ? ' disabled' : ''"
th:attr="aria-disabled=${isToday}..."
```

미래 날짜에서는 `isToday = false` → "다음" 버튼 활성화 → 더 먼 미래로 이동 가능.

**재현 방법:**
1. URL 직접 입력: `/history?date=2030-01-01`
2. "다음" `[>]` 버튼 클릭 → 2030-01-02로 이동 → 무한 반복 가능

**사용자 영향:**
- PRD AC: "미래 날짜 체크는 UI 차단됨" → 체크 자체는 disabled이나 미래 날짜 탐색은 가능
- 루틴이 없는 미래 날짜 빈 화면이 계속 표시됨
- UX 혼란 (아무것도 없는 날짜를 계속 볼 수 있음)

**수정 방법:**

```java
// HomeController.java history()에 isFuture 추가
model.addAttribute("isFuture", target.isAfter(LocalDate.now()));
```

```html
<!-- history/index.html "다음" 버튼 비활성화 조건 변경 -->
th:classappend="${isToday or isFuture} ? ' disabled' : ''"
```

또는 서버에서 미래 날짜 요청 시 오늘로 리다이렉트.

**→ 백엔드:** HomeController.history() 수정
**→ 프론트:** history/index.html 조건 수정

---

### BUG-06: 레벨업 배너 CSS 초기 transform 스펙 불일치

**심각도:** P3 (기능 영향 없음, 스펙 불일치)
**영향 범위:** 레벨업 발생 시 배너 슬라이드 인 동작
**대상 파일:** `static/css/style.css`

**원인 분석:**

CSS 구현:
```css
/* style.css line 237 */
.level-up-banner {
    transform: translateY(-120%);  /* 구현값 */
```

디자인 스펙:
```css
/* xp-level.md line 238 */
.level-up-banner {
  transform: translateY(-100%);  /* 스펙값 */
```

**영향:**
- -120%는 배너 높이의 120%만큼 위로 숨기므로, 화면 상단 여백이 있어도 완전히 가려짐
- 실제 슬라이드 인 거리가 -100%보다 약간 더 길어 애니메이션 거리가 길다
- 기능적으로는 정상 동작하나 스펙과 불일치

**수정:** style.css line 237을 `transform: translateY(-100%)`로 변경

**→ 프론트:** static/css/style.css line 237

---

## P4 — 기술 부채 (마감 없음)

### BUG-07: HomeController에서 currentXp 모델 속성 누락

**심각도:** P4
**영향 범위:** PRD 계약과 구현 불일치 (현재 템플릿에서 미사용)
**대상 파일:** `HomeController.java`

**원인 분석:**

PRD `prd-xp-level.md` GET / 신규 모델 속성 계약:
```
| `currentXp` | `int` | 누적 총 XP |
```

`HomeController.index()`에 `currentXp` 추가 없음:
```java
model.addAttribute("userLevel", ...);
model.addAttribute("xpInCurrentLevel", ...);
model.addAttribute("xpToNextLevel", ...);
model.addAttribute("xpProgressPercent", ...);
// currentXp 누락
```

현재 템플릿은 `currentXp`를 사용하지 않으나, 향후 템플릿 수정 시 혼란 유발 가능.

**수정:**
```java
model.addAttribute("currentXp", userStatsService.getTotalXp());
```

---

### BUG-08: XP 바 aria-label 분모 의미 불명확

**심각도:** P4
**영향 범위:** 스크린 리더 사용자
**대상 파일:** `templates/index.html`

**원인 분석:**

```html
<!-- templates/index.html line 52 -->
aria-label=|${xpInCurrentLevel} XP / ${xpToNextLevel} XP|
```

totalXp=80일 때: "80 XP / 120 XP" → 스크린 리더가 읽으면 의미 불명확
(BUG-02가 수정되면 분모가 200이 되어 "80 XP / 200 XP")

**개선안:**
```html
aria-label=|XP 진행: ${xpInCurrentLevel} / ${xpLevelRange} (${xpProgressPercent}%)|
```

---

## 요약표

| ID | 심각도 | 제목 | 대상 | 상태 |
|----|-------|------|------|------|
| BUG-01 | P1 | 만렙 LEGEND 분기 조건 항상 false | templates/index.html | 미수정 |
| BUG-02 | P2 | XP 수치 분모 의미 불일치 | UserStatsService, index.html | 미수정 |
| BUG-03 | P2 | 과거 날짜 완료 시 오늘 보너스 오지급 | RoutineService | 미수정 |
| BUG-04 | P3 | 통계 빈 상태 표시 불가 | stats/index.html, RoutineService | 미수정 |
| BUG-05 | P3 | history 미래 날짜 무한 이동 | HomeController, history/index.html | 미수정 |
| BUG-06 | P3 | 배너 CSS transform 스펙 불일치 | style.css | 미수정 |
| BUG-07 | P4 | currentXp 모델 속성 누락 | HomeController | 미수정 |
| BUG-08 | P4 | aria-label 분모 의미 불명확 | templates/index.html | 미수정 |

