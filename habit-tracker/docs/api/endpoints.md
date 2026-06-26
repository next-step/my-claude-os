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

**제약**: 오늘보다 미래 날짜에서는 체크 버튼 비활성화

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
