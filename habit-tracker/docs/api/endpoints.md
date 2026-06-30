# API 계약서 — Habit Tracker

백엔드 에이전트가 관리하는 Thymeleaf 모델 속성 및 URL 계약.
프론트 에이전트는 이 파일의 속성명을 그대로 사용한다.

---

## 페이지별 URL 및 모델 속성

---

### GET /
**화면**: 오늘의 루틴 (홈)

**Model Attributes:**
| 키 | 타입 | 설명 |
|----|------|------|
| `todayRoutines` | `List<RoutineDto>` | 오늘 요일에 해당하는 활성 루틴 목록 |
| `today` | `LocalDate` | 오늘 날짜 |
| `completionRate` | `int` | 오늘 완료율 (0–100) |
| `userLevel` | `Level` | 현재 레벨 Enum |
| `currentXp` | `int` | 누적 총 XP |
| `xpInCurrentLevel` | `int` | 현재 레벨 시작 XP 기준 진행 XP |
| `xpToNextLevel` | `int` | 다음 레벨까지 남은 XP (만렙 LEGEND 이면 0) |
| `xpLevelRange` | `int` | 현재 레벨 구간 크기 (다음 레벨 minXp - 현재 레벨 minXp, 만렙이면 0) |
| `xpProgressPercent` | `int` | 현재 레벨 구간 내 진행률 0–100 (만렙이면 100) |
| `leveledUp` | `boolean` | 직전 체크로 레벨업 발생 여부 (플래시, 기본값 false) |
| `newLevel` | `Level` (nullable) | 레벨업 시 새 레벨 Enum (플래시) |
| `prevLevel` | `Level` (nullable) | 레벨업 시 이전 레벨 Enum (플래시) |
| `newLevelName` | `String` (nullable) | 새 레벨 한글 이름 (플래시) |
| `newLevelEmoji` | `String` (nullable) | 새 레벨 이모지 (플래시) |
| `prevLevelName` | `String` (nullable) | 이전 레벨 한글 이름 (플래시) |
| `prevLevelEmoji` | `String` (nullable) | 이전 레벨 이모지 (플래시) |

**Level Enum 필드 (Thymeleaf 접근):**
```
${userLevel.number}      Integer  레벨 번호 (1–7)
${userLevel.name}        String   열거형 상수명 (SPROUT, SAPLING … LEGEND)  ← enum .name()
${userLevel.getName()}   String   레벨 한글 이름 (새싹, 묘목 … 전설)
${userLevel.emoji}       String   이모지 (🌱 … ⚡)
${userLevel.minXp}       int      해당 레벨 최소 누적 XP
```

**Thymeleaf 분기 포인트:**
```html
<!-- 일반 레벨 XP 바 (Lv.1–6) -->
<div th:unless="${userLevel.name == 'LEGEND'}">
  <span th:text="${userLevel.emoji}"></span>
  <span th:text="${userLevel.getName()}"></span> Lv.<span th:text="${userLevel.number}"></span>
  <div class="xp-progress-fill" th:attr="data-percent=${xpProgressPercent}"></div>
  <span th:text="${xpInCurrentLevel}"></span> / <span th:text="${xpToNextLevel}"></span> XP
</div>

<!-- 만렙 카드 (Lv.7 LEGEND) -->
<div th:if="${userLevel.name == 'LEGEND'}" class="xp-bar-legend">
  ★ 최고 레벨 달성 ★
</div>

<!-- 레벨업 배너 (플래시 속성) -->
<div th:if="${leveledUp}" class="level-up-banner">
  <span th:text="${prevLevelEmoji}"></span> →
  <span th:text="${newLevelEmoji}"></span>
  <span th:text="|${newLevelName}에 도달했어요!|"></span>
  <button class="banner-close">✕</button>
</div>
```

**RoutineDto 필드:**
```
id          Long
name        String
description String (nullable)
completed   boolean
daysOfWeek  String  (예: "MON,WED,FRI")
```

---

### POST /check/{routineId}
**설명**: 루틴 체크 토글 (완료 ↔ 미완료)

**Path Variable:** `routineId` (Long)

**Form Parameters:**
| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `returnDate` | `String` (yyyy-MM-dd) | 히스토리에서 호출 시 돌아갈 날짜 |

**응답:**
- `returnDate` 없음 → `redirect:/`
- `returnDate` 있음 → `redirect:/history?date={returnDate}`

---

### GET /stats
**화면**: 통계

**Model Attributes:**
| 키 | 타입 | 설명 |
|----|------|------|
| `rates7` | `LinkedHashMap<LocalDate, Integer>` | 최근 7일 날짜별 완료율 |
| `rates30` | `LinkedHashMap<LocalDate, Integer>` | 최근 30일 날짜별 완료율 |
| `hasActiveRoutines` | `boolean` | 활성 루틴이 1개 이상 존재하는지 여부 (빈 상태 분기용) |

---

### GET /history?date={date}
**화면**: 과거 기록 조회

**Query Parameters:**
| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| `date` | `String` (yyyy-MM-dd) | 오늘 | 조회할 날짜 |

**Model Attributes:**
| 키 | 타입 | 설명 |
|----|------|------|
| `routines` | `List<RoutineDto>` | 해당 날짜 루틴 목록 (완료 여부 포함) |
| `target` | `LocalDate` | 조회 중인 날짜 |
| `prev` | `LocalDate` | 이전 날짜 |
| `next` | `LocalDate` | 다음 날짜 |
| `isToday` | `boolean` | 오늘 여부 (미래 날짜 체크 방지용) |
| `isFuture` | `boolean` | 미래 날짜 여부 (다음 버튼 비활성화용) |

**제약**: 오늘보다 미래 날짜에서는 체크 버튼 비활성화, 다음 버튼도 비활성화

---

### GET /routines
**화면**: 루틴 목록 + 추가 폼

**Model Attributes:**
| 키 | 타입 | 설명 |
|----|------|------|
| `routines` | `List<Routine>` | 활성 루틴 전체 목록 |
| `form` | `RoutineForm` | 빈 폼 (유효성 오류 재표시용) |

---

### POST /routines
**설명**: 새 루틴 생성

**Form Parameters (RoutineForm):**
| 필드 | 유효성 | 설명 |
|------|-------|------|
| `name` | @NotBlank, @Size(max=100) | 루틴 이름 |
| `description` | @Size(max=500) | 설명 (선택) |
| `daysOfWeek` | @NotEmpty | 선택된 요일 배열 (예: ["MON","WED","FRI"]) |

**응답:**
- 성공 → `redirect:/routines`
- 유효성 오류 → `routines/list` (폼 재표시, bindingResult 포함)

---

### POST /routines/{id}/delete
**설명**: 루틴 비활성화 (soft delete)

**Path Variable:** `id` (Long)

**응답:** `redirect:/routines`

---

## 공통 규칙

1. **URL 형식**: 소문자, 하이픈 사용 (예: `/habit-list` 아닌 `/routines`)
2. **리다이렉트 후 GET**: POST 후 반드시 redirect (PRG 패턴)
3. **날짜 형식**: `yyyy-MM-dd` 문자열로 주고받음, 모델에서 `LocalDate`로 변환
4. **빈 목록**: 빈 배열 `[]`로 반환, null 금지
5. **soft delete**: 루틴은 삭제하지 않고 `active=false`로 비활성화
