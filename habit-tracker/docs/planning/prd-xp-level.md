# XP & 레벨 시스템 PRD

> 상태: Confirmed
> 작성일: 2026-06-26
> 관련 정책: POLICY.md #3-1

---

## 왜 만드나 (Problem)

Phase 1 앱에서 루틴 체크는 그 날 하루가 끝이다.
완료 외에 쌓이는 것이 없기 때문에 사용자가 내일 앱을 열 이유가 없다.
XP와 레벨은 "오늘의 체크"를 "장기 성장의 한 걸음"으로 재정의하는 가장 강력한 장치다.

---

## 게이미피케이션 각도

루틴 완료를 경험치 적립으로 재정의한다.
홈 화면 상단의 XP 프로그레스 바가 채워지는 시각적 만족감과,
레벨업 순간의 축하 연출이 결합되어 사용자는 "오늘 몇 XP 더 벌어야 레벨업인데"라는
자발적 동기를 갖게 된다.

핵심 감정 목표: 뿌듯함(루틴 완료 → XP 획득), 설렘(레벨업 임박), 성취감(레벨업 순간)

---

## MVP 범위 (Must Have)

- [ ] XP 적립: 루틴 체크 완료 시 +10 XP (최초 완료 1회 한정)
- [ ] XP 적립: 하루 루틴 전체 완료 시 +50 XP 보너스 (날짜당 1회 한정)
- [ ] 레벨 계산: 누적 XP 기반 7단계 레벨 자동 갱신 (POLICY.md §3-1 레벨 구간 기준)
- [ ] 홈 화면 상단: 현재 레벨명 + XP 프로그레스 바 표시
- [ ] 레벨업 감지 및 플래시 메시지 출력

---

## 후순위 (v2 이후)

- [ ] 스트릭 연동 XP 보너스 (3일 연속 +30, 7일 연속 +100, 30일 연속 +500) — 스트릭 시스템 PRD와 연동
- [ ] 루틴 체크 시 "+10 XP" 플로팅 숫자 애니메이션
- [ ] 레벨업 전체화면 팡파레 연출 (confetti)
- [ ] XP 획득 내역 로그 페이지 (/xp-history)
- [ ] 레벨별 특전 해금 알림

---

## 사용자 스토리 + 수용 기준

### Story 1: XP 적립 — 루틴 완료
사용자가 루틴을 체크하면 +10 XP가 적립된다.

AC:
- [ ] 루틴 체크 완료(false → true) 시 UserStats.totalXp += 10
- [ ] 같은 루틴을 같은 날 체크 해제 후 재체크해도 XP는 추가 지급되지 않는다
- [ ] 체크 해제(true → false) 시 XP는 차감되지 않는다
- [ ] 엣지: 오늘이 아닌 과거 날짜 체크 시에도 XP는 동일하게 적립된다 (미래 날짜 체크는 UI 차단됨)

### Story 2: XP 적립 — 하루 전체 완료 보너스
사용자가 오늘 예정된 루틴을 전부 완료하면 +50 XP 보너스가 한 번 지급된다.

AC:
- [ ] 마지막 루틴 체크 완료 시 오늘 예정 루틴 전체 완료 여부를 확인해 +50 XP 지급
- [ ] 같은 날짜에 전체 완료 보너스는 최대 1회만 지급된다 (UserStats.lastBonusDate 비교)
- [ ] 루틴을 하나 해제해서 전체 미완료 상태가 되었다가 다시 완료해도 당일 추가 보너스는 없다
- [ ] 엣지: 오늘 예정 루틴이 0개인 날은 전체 완료 보너스 조건이 성립하지 않는다

### Story 3: 레벨 계산 및 홈 화면 표시
사용자가 홈 화면에 접속하면 현재 레벨과 XP 진행률이 표시된다.

AC:
- [ ] 홈 화면 상단에 `[레벨 이모지] [레벨명] Lv.N` 형식으로 현재 레벨 표시
- [ ] XP 프로그레스 바: 현재 레벨 시작 XP 기준 진행률을 0~100% 로 시각화
- [ ] 프로그레스 바 옆 또는 아래에 `{현재레벨내XP} / {다음레벨까지필요XP} XP` 텍스트 표시
- [ ] 레벨 7(전설)에 도달하면 프로그레스 바 대신 "최고 레벨 달성" 표시
- [ ] 엣지: 신규 사용자(totalXp = 0)는 Lv.1 새싹, 프로그레스 0%로 표시

### Story 4: 레벨업 감지 및 알림
사용자가 루틴을 체크해서 레벨이 올라가면 축하 메시지가 표시된다.

AC:
- [ ] 체크 완료 후 redirect 전에 이전 레벨과 현재 레벨을 비교해 레벨업 여부를 세션 플래시로 전달
- [ ] 홈 화면에서 레벨업 플래시가 있으면 "[이모지] 레벨업! [이전레벨명] → [새레벨명]" 배너 표시
- [ ] 배너는 5초 후 자동으로 사라진다 (또는 사용자가 닫기 버튼으로 제거)
- [ ] 엣지: 레벨 7에서 추가 XP를 획득해도 레벨업 배너는 표시되지 않는다

---

## 도메인 모델

### 신규 엔티티: UserStats

```
TABLE: user_stats
- id              BIGINT PK (항상 1L, 단일 사용자 앱)
- total_xp        INT NOT NULL DEFAULT 0
- last_bonus_date DATE (오늘 전체 완료 보너스 중복 방지용)
- updated_at      TIMESTAMP
```

### RoutineCheck 엔티티 변경

```
TABLE: routine_checks (기존)
+ xp_awarded BOOLEAN NOT NULL DEFAULT FALSE  -- XP 중복 지급 방지 플래그
```

### Level 열거형 (Enum)

```java
enum Level {
    SPROUT(1,   "새싹",   "🌱",  0),
    SAPLING(2,  "묘목",   "🌿",  200),
    TREE(3,     "나무",   "🌳",  600),
    FOREST(4,   "숲",     "🌲",  1500),
    GARDENER(5, "정원사", "👨‍🌾", 3500),
    NATURALIST(6,"자연인","🏕️",  7000),
    LEGEND(7,   "전설",   "⚡",  15000);
    // 각 항목: (레벨번호, 이름, 이모지, 최소누적XP)
}
```

---

## 서비스 계약

### UserStatsService

```
awardXp(int amount)                   → void    // totalXp += amount, updatedAt 갱신
getTotalXp()                          → int
getCurrentLevel()                      → Level
getXpInCurrentLevel()                 → int     // totalXp - currentLevel.minXp
getXpToNextLevel()                    → int     // nextLevel.minXp - totalXp (레벨7: 0)
getXpProgressPercent()                → int     // 0~100
isTodayBonusAwarded()                 → boolean // lastBonusDate == today
markTodayBonusAwarded()               → void    // lastBonusDate = today
```

### GET / 신규 모델 속성

| 키 | 타입 | 설명 |
|----|------|------|
| `userLevel` | `Level` | 현재 레벨 Enum (이름, 번호, 이모지 포함) |
| `currentXp` | `int` | 누적 총 XP |
| `xpInCurrentLevel` | `int` | 현재 레벨 기준 진행 XP |
| `xpToNextLevel` | `int` | 다음 레벨까지 남은 XP |
| `xpProgressPercent` | `int` | 현재 레벨 내 진행률 (0~100) |
| `leveledUp` | `boolean` | 직전 체크로 레벨업 발생 여부 (플래시) |
| `newLevel` | `Level` (nullable) | 레벨업 시 새 레벨 정보 |
| `prevLevel` | `Level` (nullable) | 레벨업 시 이전 레벨 정보 |

---

## 정책 연동

- POLICY.md §3-1 레벨 구간 수치를 그대로 사용 (변경 없음)
- POLICY.md §3-1 XP 중복 적립 방지 정책 신규 추가 → **이번 커밋에 반영 완료**
- POLICY.md §5 기술 정책 "단일 사용자" 전제 → UserStats는 id=1L 싱글턴으로 관리
- POLICY.md §4-1 피드백 원칙 → 레벨업 배너는 즉각적이고 눈에 띄어야 함

---

## 팀 지시사항

### → Designer

기능명: XP & 레벨 시스템
PRD 위치: habit-tracker/docs/planning/prd-xp-level.md

핵심 감정 목표:
- 루틴 체크 시: 뿌듯함 (내가 성장하고 있다는 느낌)
- 레벨업 순간: 설렘 + 성취감 (예상보다 크게 반응해도 괜찮다)

필요한 화면 / 컴포넌트:
1. 홈 화면 상단 XP 바 컴포넌트
   - 레벨 이모지 + 레벨명 + Lv.N 표시
   - 진행률 프로그레스 바 (배경: 연두/자연 계열 그라데이션 권장)
   - "xpInCurrentLevel / xpToNextLevel XP" 수치 텍스트
   - Lv.7 전설 상태: 만렙 특별 표시 (금빛 테두리 등)

2. 레벨업 축하 배너
   - 홈 화면 최상단 or 화면 중앙 오버레이
   - 감정: 갑작스러운 기쁨. 화면을 '가득 채우지는' 않되, 무시할 수 없는 크기
   - 텍스트: "[이전레벨 이모지] → [새레벨 이모지]  [새레벨명]에 도달했어요!"
   - 색상: 따뜻한 골드/오렌지 계열
   - 5초 자동 소멸 + 닫기 버튼

애니메이션 포인트:
- XP 바: 숫자가 오를 때 바가 스르륵 채워지는 transition (0.4s ease-out)
- 레벨업 배너: 위에서 슬라이드 인 → 5초 후 페이드 아웃

게이미피케이션 비주얼:
- 레벨별 이모지를 크게 사용해 레벨 정체성을 시각화
- 레벨 7 전설(⚡)은 다른 레벨과 시각적으로 확연히 다르게 (반짝임, 골드 등)

참고 디자인 시스템: habit-tracker/docs/design/design-system.md


### → Backend

신규 작업 목록:

1. **엔티티 생성: UserStats**
   - 파일: `src/main/java/com/habit/tracker/domain/UserStats.java`
   - 필드: id(Long), totalXp(int default 0), lastBonusDate(LocalDate nullable), updatedAt(LocalDateTime)
   - 단일 사용자이므로 id=1L 고정 싱글턴으로 관리 (없으면 자동 생성)

2. **엔티티 변경: RoutineCheck**
   - 파일: `src/main/java/com/habit/tracker/domain/RoutineCheck.java`
   - 필드 추가: `xpAwarded boolean default false`
   - XP 중복 지급 방지용 플래그

3. **서비스 생성: UserStatsService**
   - 파일: `src/main/java/com/habit/tracker/service/UserStatsService.java`
   - 메서드: awardXp(int), getTotalXp(), getCurrentLevel(), getXpInCurrentLevel(), getXpToNextLevel(), getXpProgressPercent(), isTodayBonusAwarded(), markTodayBonusAwarded()

4. **열거형 생성: Level**
   - 파일: `src/main/java/com/habit/tracker/domain/Level.java`
   - 값: SPROUT(1,"새싹","🌱",0), SAPLING(2,"묘목","🌿",200), TREE(3,"나무","🌳",600), FOREST(4,"숲","🌲",1500), GARDENER(5,"정원사","👨‍🌾",3500), NATURALIST(6,"자연인","🏕️",7000), LEGEND(7,"전설","⚡",15000)
   - 정적 메서드: `fromXp(int totalXp)` → 누적 XP로 현재 레벨 반환

5. **체크 서비스 변경**
   - 루틴 체크 완료(false→true) 처리 시:
     a. RoutineCheck.xpAwarded == false 이면 UserStats에 +10 XP 지급 후 xpAwarded = true
     b. 체크 후 오늘 예정 루틴 전체 완료 여부 확인
     c. 전체 완료 && UserStats.lastBonusDate != today → +50 XP 지급 + lastBonusDate = today
   - 레벨업 감지: XP 지급 전후 Level 비교 → 레벨업 시 RedirectAttributes에 leveledUp=true, newLevel, prevLevel 추가

6. **컨트롤러 변경: HomeController (GET /)**
   - 신규 모델 속성 추가: userLevel, currentXp, xpInCurrentLevel, xpToNextLevel, xpProgressPercent, leveledUp, newLevel, prevLevel
   - RedirectAttributes에서 플래시 속성 읽어 모델에 전달

7. **endpoints.md 업데이트**
   - GET / 모델 속성 표에 신규 속성 7개 추가

8. **정책 변경 공지 (POLICY.md §3-1 변경)**
   - XP 중복 적립 방지: RoutineCheck.xpAwarded 플래그로 구현
   - 전체 완료 보너스 중복 방지: UserStats.lastBonusDate 날짜 비교로 구현


### → Frontend

신규 작업 목록 (Thymeleaf):

1. **홈 화면 (`templates/index.html`) 상단 XP 바 삽입**
   - 홈 화면 루틴 목록 위에 XP 바 컴포넌트 추가
   - 모델 속성: `${userLevel}`, `${xpInCurrentLevel}`, `${xpToNextLevel}`, `${xpProgressPercent}`
   - Lv.7 분기 처리: `xpToNextLevel == 0` 이면 "최고 레벨 달성" 텍스트, 프로그레스 바 숨김

2. **레벨업 배너 (`templates/index.html`) 조건부 렌더링**
   - `${leveledUp}` == true 일 때만 배너 블록 표시
   - 내용: "${prevLevel.emoji} → ${newLevel.emoji} ${newLevel.name}에 도달했어요!"
   - JavaScript: 5초 후 배너 제거 (`setTimeout(() => banner.remove(), 5000)`)
   - 닫기 버튼 클릭 시 즉시 제거

3. **CSS 클래스 (별도 스타일시트 또는 인라인)**
   - `.xp-bar-container`: 레벨 정보 + 프로그레스 바 감싸는 카드
   - `.xp-progress`: 프로그레스 바 track
   - `.xp-progress-fill`: 실제 채워진 부분, width는 `${xpProgressPercent}%`로 설정
   - `.level-up-banner`: 슬라이드 인/페이드 아웃 애니메이션 클래스


### → QA

수용 기준 기반 테스트 케이스:

**TC-XP-01: 기본 XP 적립**
- 루틴 체크 완료 → UserStats.totalXp += 10 확인
- DB에서 RoutineCheck.xpAwarded = true 확인

**TC-XP-02: XP 중복 방지**
- 동일 루틴 체크 → 체크 해제 → 재체크
- totalXp가 처음 +10 이후 추가 증가하지 않음을 확인

**TC-XP-03: 차감 없음**
- 루틴 체크(+10) → 체크 해제 → totalXp 변동 없음 확인

**TC-XP-04: 전체 완료 보너스 지급**
- 오늘 루틴 N개를 모두 체크 완료 → totalXp += 10*N + 50 확인
- UserStats.lastBonusDate = 오늘 확인

**TC-XP-05: 전체 완료 보너스 중복 방지**
- 루틴 하나 해제 후 재체크 → totalXp 추가 +50 없음 확인

**TC-XP-06: 레벨 계산 경계값**
- totalXp = 199 → 레벨 1 (새싹)
- totalXp = 200 → 레벨 2 (묘목)
- totalXp = 14999 → 레벨 6 (자연인)
- totalXp = 15000 → 레벨 7 (전설)

**TC-XP-07: 레벨업 배너 표시**
- totalXp = 195 상태에서 루틴 체크 완료 (XP 5개 남음, 체크 하면 +10 → 205)
- 홈 화면에 레벨업 배너 표시 확인
- 5초 후 자동 제거 확인

**TC-XP-08: 오늘 루틴 0개 예외**
- 오늘 예정 루틴이 없을 때 홈 화면 접속 → XP 바는 정상 표시, 오류 없음 확인

**TC-XP-09: 만렙 상태 UI**
- totalXp >= 15000 → 홈 화면에 프로그레스 바 대신 "최고 레벨 달성" 표시 확인
- 추가 체크 시 XP는 쌓이되 레벨업 배너는 표시되지 않음 확인

---

## 기획 메모

- 스트릭 XP 보너스(3일/7일/30일)는 스트릭 시스템 PRD에서 별도 기획. 이번 PRD에서는 기반 XP 엔진만 구축.
- UserStats 싱글턴 패턴: `findById(1L).orElseGet(() -> save(new UserStats()))` 형태로 서비스 레이어에서 보장.
- 추후 다중 사용자 지원 시 UserStats에 userId 추가만 하면 확장 가능하도록 설계.
